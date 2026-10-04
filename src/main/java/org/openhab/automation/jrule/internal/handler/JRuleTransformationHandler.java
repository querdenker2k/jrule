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

import org.openhab.automation.jrule.exception.JRuleRuntimeException;
import org.openhab.core.transform.TransformationException;
import org.openhab.core.transform.TransformationHelper;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link JRuleTransformationHandler} is responsible for handling transformation requests
 *
 * @author Arne Seime - Initial contribution
 */
public class JRuleTransformationHandler {

    private static BundleContext bundleContext;

    private static final Logger logger = LoggerFactory.getLogger(JRuleTransformationHandler.class);

    private JRuleTransformationHandler() {
    }

    public static void setBundleContext(BundleContext bundleContext) {
        JRuleTransformationHandler.bundleContext = bundleContext;
    }

    public static BundleContext getBundleContext() {
        return bundleContext;
    }

    /**
     * Transforms the given state with the transformation pattern.
     *
     * @param stateDescPattern The transformation pattern
     * @param state State which should be converted
     * @return The transformation result
     * @throws JRuleRuntimeException In case a transformation exception occur
     */
    public static String transform(String stateDescPattern, String state) throws JRuleRuntimeException {
        try {
            return TransformationHelper.transform(bundleContext, stateDescPattern, state);
        } catch (TransformationException e) {
            throw new JRuleRuntimeException(
                    String.format("Transformation of %s using %s failed: %s", state, stateDescPattern, e));
        }
    }
}
