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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.automation.jrule.internal.JRuleConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link JRuleClassLoader} loads user rule classes and generated classes from the JRule working
 * directory, which are plain class files on disk rather than part of any bundle.
 *
 * @author Joseph (Seaside) Hagberg - Initial contribution
 */
@NonNullByDefault
class JRuleClassLoader extends URLClassLoader {

    private final Logger logger = LoggerFactory.getLogger(JRuleClassLoader.class);

    JRuleClassLoader(URL[] urls, @Nullable ClassLoader parent) {
        super(urls, parent);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        // defineClass may only be called once per name and loader instance, so a class this loader has
        // already defined has to be returned from the cache instead of being defined a second time.
        final Class<?> loadedClass = findLoadedClass(name);
        if (loadedClass != null) {
            return loadedClass;
        }
        try {
            for (URL url : getURLs()) {
                if (url.getProtocol().equals("file")) {
                    File classFile = new File(url.getFile(),
                            name.replaceAll("\\.", "/") + JRuleConstants.CLASS_FILE_TYPE);
                    try (InputStream is = new FileInputStream(classFile)) {
                        byte[] buf = is.readAllBytes();
                        return defineClass(name, buf, 0, buf.length);
                    }
                }
            }
            return super.loadClass(name);
        } catch (FileNotFoundException e) {
            return super.loadClass(name);
        } catch (IOException e) {
            logger.warn("Trouble loading class {} from file system, deferring to parent clasloader: {}", name,
                    e.toString());
            return super.loadClass(name);
        }
    }
}
