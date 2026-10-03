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

/**
 * Stand-in for a compiled user rule class. {@link JRuleClassLoaderTest} copies this class file into a
 * temporary directory and loads it from there, the way JRule loads classes from its working directory.
 *
 * @author Robert Delbrück - Initial contribution
 */
public class JRuleClassLoaderProbe {

    public String greet() {
        return "probe";
    }
}
