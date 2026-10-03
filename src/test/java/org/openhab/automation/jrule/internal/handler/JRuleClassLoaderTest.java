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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link JRuleClassLoader}.
 *
 * @author Robert Delbrück - Initial contribution
 */
class JRuleClassLoaderTest {

    private static final String PROBE_CLASS_NAME = JRuleClassLoaderProbe.class.getName();
    private static final String PROBE_CLASS_FILE = "JRuleClassLoaderProbe.class";

    /**
     * Loading the same name twice from one loader instance must return the cached class. Without the
     * findLoadedClass() guard the loader calls defineClass() again and the JVM answers with a
     * LinkageError for the duplicate definition.
     */
    @Test
    void loadingTheSameClassTwiceReturnsTheSameClass() throws Exception {
        try (JRuleClassLoader loader = loaderFor(classesDir)) {
            Class<?> first = loader.loadClass(PROBE_CLASS_NAME);
            Class<?> second = loader.loadClass(PROBE_CLASS_NAME);

            assertSame(first, second, "loadClass must return the cached class on the second call");
        }
    }

    /**
     * The class has to come from the directory on disk, not from the parent, so that a recompiled rule
     * replaces the previous version instead of being shadowed by it.
     */
    @Test
    void classIsDefinedByTheLoaderItselfAndIsUsable() throws Exception {
        try (JRuleClassLoader loader = loaderFor(classesDir)) {
            Class<?> loaded = loader.loadClass(PROBE_CLASS_NAME);

            assertSame(loader, loaded.getClassLoader(), "class must be defined by the JRule loader");
            assertNotSame(JRuleClassLoaderProbe.class, loaded, "class must not come from the test classpath");
            assertEquals("probe",
                    loaded.getDeclaredMethod("greet").invoke(loaded.getDeclaredConstructor().newInstance()));
        }
    }

    /**
     * A fresh loader per reload is what makes hot reloading work, so two instances must hand out
     * distinct classes even for the same class file.
     */
    @Test
    void separateLoaderInstancesDefineDistinctClasses() throws Exception {
        try (JRuleClassLoader first = loaderFor(classesDir); JRuleClassLoader second = loaderFor(classesDir)) {
            assertNotSame(first.loadClass(PROBE_CLASS_NAME), second.loadClass(PROBE_CLASS_NAME));
        }
    }

    @TempDir
    Path classesDir;

    /**
     * Copies the probe class file into the temporary directory, mirroring the package layout JRule
     * produces in its gen and rules directories.
     */
    private JRuleClassLoader loaderFor(Path root) throws IOException {
        Path packageDir = root
                .resolve(PROBE_CLASS_NAME.substring(0, PROBE_CLASS_NAME.lastIndexOf('.')).replace('.', '/'));
        Files.createDirectories(packageDir);
        try (InputStream is = JRuleClassLoaderProbe.class.getResourceAsStream(PROBE_CLASS_FILE)) {
            if (is == null) {
                throw new IOException("probe class file not found on the test classpath");
            }
            Files.write(packageDir.resolve(PROBE_CLASS_FILE), is.readAllBytes());
        }
        return new JRuleClassLoader(new URL[] { root.toUri().toURL() }, null);
    }
}
