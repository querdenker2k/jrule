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
package org.openhab.automation.jrule.internal.handler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.automation.jrule.internal.engine.excutioncontext.JRuleExecutionContext;

/**
 * Tests for {@link JRuleTimerHandler}.
 *
 * @author Robert Delbrück - Initial contribution
 */
class JRuleTimerHandlerTest {

    private static final Duration DELAY = Duration.ofSeconds(1);
    private static final long AWAIT_MILLIS = 4000;

    private JRuleTimerHandler handler;
    private JRuleExecutionContext context;

    @BeforeEach
    void setUp() {
        handler = JRuleTimerHandler.get();
        handler.cancelAll();
        context = mock(JRuleExecutionContext.class);
        when(context.getLogName()).thenReturn("JRuleTimerHandlerTest");
        when(context.getLoggingTags()).thenReturn(new String[0]);
    }

    @AfterEach
    void tearDown() {
        handler.cancelAll();
    }

    /**
     * Control case for the two cancelAll() tests below: without cancelling, the timer does fire within
     * the window they wait for. Without this, those tests would also pass if the timer never fired.
     */
    @Test
    void anUncancelledTimerFires() throws Exception {
        CountDownLatch fired = new CountDownLatch(1);

        handler.createTimer("fires", DELAY, timer -> fired.countDown(), context);

        assertTrue(fired.await(AWAIT_MILLIS, TimeUnit.MILLISECONDS), "timer should have fired");
    }

    /**
     * JRuleHandler.dispose() calls cancelAll(), so a timer scheduled before deactivation must not run
     * into a rule engine that is already gone.
     */
    @Test
    void cancelAllPreventsAPendingTimerFromFiring() throws Exception {
        CountDownLatch fired = new CountDownLatch(1);
        JRuleTimerHandler.JRuleTimer timer = handler.createTimer("pending", DELAY, t -> fired.countDown(), context);

        handler.cancelAll();

        assertFalse(timer.isRunning(), "cancelAll() must cancel the timer's future");
        assertFalse(fired.await(AWAIT_MILLIS, TimeUnit.MILLISECONDS), "a cancelled timer must not fire");
    }

    /**
     * A repeating timer holds one future per repetition, so cancelAll() has to cancel all of them.
     */
    @Test
    void cancelAllPreventsARepeatingTimerFromFiring() throws Exception {
        CountDownLatch fired = new CountDownLatch(1);
        JRuleTimerHandler.JRuleTimer timer = handler.createRepeatingTimer("repeating", DELAY, 3, t -> fired.countDown(),
                context);

        handler.cancelAll();

        assertFalse(timer.isRunning(), "cancelAll() must cancel every repetition");
        assertFalse(fired.await(AWAIT_MILLIS, TimeUnit.MILLISECONDS), "a cancelled repeating timer must not fire");
    }

    /**
     * Deactivation may happen more than once, and cancelAll() drains in passes, so calling it on an
     * already empty handler has to be a no-op rather than looping or throwing.
     */
    @Test
    void cancelAllIsSafeToCallRepeatedly() {
        handler.createTimer("twice", DELAY, timer -> {
        }, context);

        handler.cancelAll();
        handler.cancelAll();
    }

    /**
     * Timer bodies run on the handler's own pool, and those threads must not keep the JVM alive after
     * openHAB shuts down. Note that this property currently also holds without the explicit
     * setDaemon(true) in the thread factory: the pool threads are created by CompletableFuture's
     * internal delay scheduler, which is a daemon, and a new thread inherits the flag from its
     * creator. This test pins the property, not that one particular mechanism provides it.
     */
    @Test
    void timerBodiesRunOnDaemonThreads() throws Exception {
        AtomicReference<Thread> worker = new AtomicReference<>();
        CountDownLatch fired = new CountDownLatch(1);

        handler.createTimer("daemon", Duration.ofMillis(1), timer -> {
            worker.set(Thread.currentThread());
            fired.countDown();
        }, context);

        assertTrue(fired.await(AWAIT_MILLIS, TimeUnit.MILLISECONDS), "timer should have fired");
        assertTrue(worker.get().isDaemon(), "timer threads must be daemon threads");
    }
}
