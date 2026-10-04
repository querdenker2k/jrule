/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.automation.jrule.internal;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link JRuleDelayedDebouncingExecutor}.
 *
 * @author Robert Delbrück - Initial contribution
 */
class JRuleDelayedDebouncingExecutorTest {

    private static final long AWAIT_SECONDS = 10;

    /**
     * cancel() drops the pending invocation but keeps the executor usable, so a dispose path that only
     * cancels leaves the executor thread behind. This is what JRuleFactory.dispose() used to do.
     */
    @Test
    void cancelKeepsTheExecutorThreadAliveButShutdownEndsIt() throws Exception {
        JRuleDelayedDebouncingExecutor executor = new JRuleDelayedDebouncingExecutor(0, TimeUnit.MILLISECONDS);
        Thread worker = runAndCaptureWorkerThread(executor);

        executor.cancel();
        assertTrue(worker.isAlive(), "cancel() must leave the executor thread running, it only drops the future");

        executor.shutdown();
        await().atMost(AWAIT_SECONDS, TimeUnit.SECONDS).until(() -> !worker.isAlive());
    }

    /**
     * shutdown() has to discard work that is still waiting for its delay to pass, otherwise a rule
     * reload could run against a bundle that is already going away.
     */
    @Test
    void shutdownDiscardsAPendingInvocation() throws Exception {
        JRuleDelayedDebouncingExecutor executor = new JRuleDelayedDebouncingExecutor(5, TimeUnit.SECONDS);
        AtomicBoolean invoked = new AtomicBoolean();
        executor.call(() -> {
            invoked.set(true);
            return true;
        });

        executor.shutdown();

        Thread.sleep(500);
        assertFalse(invoked.get(), "a pending invocation must not run after shutdown");
    }

    /**
     * Both JRuleFactory.dispose() and JRuleHandler.dispose() may run for the same component, and OSGi
     * may deactivate twice, so shutting down more than once must not throw.
     */
    @Test
    void shutdownIsIdempotent() throws Exception {
        JRuleDelayedDebouncingExecutor executor = new JRuleDelayedDebouncingExecutor(0, TimeUnit.MILLISECONDS);
        Thread worker = runAndCaptureWorkerThread(executor);

        executor.shutdown();
        executor.shutdown();

        await().atMost(AWAIT_SECONDS, TimeUnit.SECONDS).until(() -> !worker.isAlive());
    }

    /**
     * Runs one invocation and returns the thread it ran on, which is the executor's single core thread.
     */
    private Thread runAndCaptureWorkerThread(JRuleDelayedDebouncingExecutor executor) throws InterruptedException {
        AtomicReference<Thread> worker = new AtomicReference<>();
        CountDownLatch invoked = new CountDownLatch(1);
        executor.call(() -> {
            worker.set(Thread.currentThread());
            invoked.countDown();
            return true;
        });
        assertTrue(invoked.await(AWAIT_SECONDS, TimeUnit.SECONDS), "scheduled invocation did not run");
        return worker.get();
    }
}
