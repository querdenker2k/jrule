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
package org.openhab.automation.jrule.rules.integration_test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.MountableFile;

/**
 * Verifies that the add-on survives repeated deactivation and reactivation without accumulating
 * threads, and that it comes back working afterwards.
 *
 * This test deliberately does not extend {@link JRuleITBase}: the containers there are static and
 * reused across all integration test classes, and removing the add-on jar from a shared container
 * would affect whichever test class happens to run next. It therefore brings its own container.
 *
 * Deactivation is triggered by removing the add-on jar from /openhab/addons, which openHAB's file
 * installer picks up. The Karaf console is not usable for this - in this image every command
 * answers with "Closed" and empty output, so bundle:stop and threads --list are not available.
 * Threads are read from /proc instead, where the JVM thread names show up truncated to 15
 * characters.
 *
 * @author Robert Delbrück - Initial contribution
 */
public class ITJRuleLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ITJRuleLifecycle.class);

    private static final String INITIALIZED = "Initializing Java Rules Engine";
    private static final String DISPOSED = "Dispose complete";
    private static final String ADDON_PATH = "/openhab/addons/jrule-engine.jar";
    private static final int CYCLES = 3;
    private static final int TIMEOUT_SECONDS = 300;

    /**
     * Thread name prefixes that a deactivation must not leave behind. The names are what the JVM
     * reports to the OS, so they are cut off at 15 characters.
     */
    private static final List<String> POOL_PREFIXES = List.of("pool-", "jrule-timer", "JRule-Executor");

    private static final List<String> logLines = new CopyOnWriteArrayList<>();

    private static final String jarPath;

    static {
        try {
            String version = IOUtils.resourceToString("/version", StandardCharsets.UTF_8);
            jarPath = String.format("target/org.openhab.automation.jrule-%s.jar", version);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("resource")
    private static final GenericContainer<?> openhab = new GenericContainer<>("openhab/openhab:5.1.0-debian")
            .withCopyToContainer(MountableFile.forClasspathResource("docker/conf", 0777), "/openhab/conf")
            .withCopyFileToContainer(MountableFile.forClasspathResource("docker/log4j2.xml", 0777),
                    "/openhab/userdata/etc/log4j2.xml")
            .withCopyFileToContainer(MountableFile.forClasspathResource("docker/users.json", 0777),
                    "/openhab/userdata/jsondb/users.json")
            .withCopyFileToContainer(MountableFile.forHostPath(jarPath, 0777), ADDON_PATH)
            .withCopyToContainer(
                    MountableFile.forHostPath("src/test/java/org/openhab/automation/jrule/rules/user", 0777),
                    "/openhab/conf/automation/jrule/rules/org/openhab/automation/jrule/rules/user")
            .withExposedPorts(8080).withLogConsumer(outputFrame -> {
                logLines.add(outputFrame.getUtf8String().strip());
                new Slf4jLogConsumer(LoggerFactory.getLogger("docker.openhab.lifecycle")).accept(outputFrame);
            }).waitingFor(new LogMessageWaitStrategy().withRegEx(".*" + INITIALIZED + ".*")
                    .withStartupTimeout(Duration.ofSeconds(TIMEOUT_SECONDS)));

    @BeforeAll
    static void startContainer() {
        openhab.start();
    }

    @AfterAll
    static void stopContainer() {
        openhab.stop();
    }

    /**
     * Each cycle asserts on its own that the add-on disposed and came back up - an engine that stayed
     * dead after reactivation would fail in {@link #activate()} rather than here. The thread counts
     * then show whether a deactivation left an executor behind.
     *
     * The reference point is taken after the first cycle, not at startup: openHAB brings up pools of
     * its own for a while after the add-on first reports readiness, so a count taken at startup is
     * not yet a steady state and would make this test fail on that noise. What a leak looks like is
     * growth from one post-activation state to the next - measured here, an add-on that does not shut
     * its executors down went 5, 6, 7 over three cycles, while a fixed one stays flat.
     */
    @Test
    void repeatedDeactivationDoesNotAccumulateThreads() throws Exception {
        log.info("thread counts at startup: {}", poolThreadCounts());

        deactivate();
        activate();
        Map<String, Long> reference = poolThreadCounts();
        log.info("thread reference after first cycle: {}", reference);

        for (int cycle = 2; cycle <= CYCLES; cycle++) {
            deactivate();
            activate();
            log.info("thread counts after cycle {}: {}", cycle, poolThreadCounts());
        }

        Map<String, Long> afterCycles = poolThreadCounts();
        for (String prefix : POOL_PREFIXES) {
            long before = reference.getOrDefault(prefix, 0L);
            long after = afterCycles.getOrDefault(prefix, 0L);
            assertTrue(after <= before, String.format(
                    "'%s' threads grew from %d to %d between cycle 1 and cycle %d, so a deactivation leaks an executor (reference %s, after %s)",
                    prefix, before, after, CYCLES, reference, afterCycles));
        }
    }

    private void deactivate() throws Exception {
        int disposedBefore = count(DISPOSED);
        Container.ExecResult result = openhab.execInContainer("rm", "-f", ADDON_PATH);
        assertTrue(result.getExitCode() == 0, "removing the add-on jar failed: " + result.getStderr());
        awaitLogCount(DISPOSED, disposedBefore + 1);
    }

    private void activate() {
        int initializedBefore = count(INITIALIZED);
        openhab.copyFileToContainer(MountableFile.forHostPath(jarPath, 0777), ADDON_PATH);
        awaitLogCount(INITIALIZED, initializedBefore + 1);
    }

    private void awaitLogCount(String needle, int expected) {
        Awaitility.await("log line '" + needle + "' seen " + expected + " times")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS)
                .until(() -> count(needle) >= expected);
    }

    private int count(String needle) {
        return (int) logLines.stream().filter(line -> line.contains(needle)).count();
    }

    /**
     * Reads the JVM's thread names from /proc and counts them per prefix of interest.
     */
    private Map<String, Long> poolThreadCounts() throws Exception {
        Container.ExecResult result = openhab.execInContainer("sh", "-c",
                "PID=$(pgrep -f karaf.main.Main | head -1); cat /proc/$PID/task/*/comm");
        assertTrue(result.getExitCode() == 0, "reading thread names failed: " + result.getStderr());
        List<String> names = Arrays.stream(result.getStdout().split("\n")).map(String::strip)
                .filter(name -> !name.isEmpty()).collect(Collectors.toList());
        return POOL_PREFIXES.stream().collect(Collectors.toMap(Function.identity(),
                prefix -> names.stream().filter(name -> name.startsWith(prefix)).count()));
    }
}
