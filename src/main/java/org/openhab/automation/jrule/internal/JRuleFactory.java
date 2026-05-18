/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
 * <p>
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 * <p>
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 * <p>
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.automation.jrule.internal;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.automation.jrule.internal.engine.JRuleEngine;
import org.openhab.automation.jrule.internal.events.JRuleEventSubscriber;
import org.openhab.automation.jrule.internal.handler.*;
import org.openhab.automation.jrule.internal.module.JRuleRuleProvider;
import org.openhab.automation.jrule.items.JRuleItemRegistry;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.items.MetadataRegistry;
import org.openhab.core.persistence.PersistenceServiceRegistry;
import org.openhab.core.scheduler.CronScheduler;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.link.ItemChannelLinkRegistry;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link JRuleFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Joseph (Seaside) Hagberg - Initial contribution
 */
@Component(configurationPid = "automation.jrule")
@NonNullByDefault
public class JRuleFactory {

    private final JRuleHandler jRuleHandler;
    private final JRuleEngine jRuleEngine;

    private static final Logger logger = LoggerFactory.getLogger(JRuleFactory.class);

    private static final String LOG_NAME_FACTORY = "JRuleFactory";

    private final JRuleDelayedDebouncingExecutor delayedInit = new JRuleDelayedDebouncingExecutor(5, TimeUnit.SECONDS);

    @Activate
    public JRuleFactory(Map<String, Object> properties, final @Reference JRuleEventSubscriber eventSubscriber,
            final @Reference ItemRegistry itemRegistry,
            final @Reference ItemChannelLinkRegistry itemChannelLinkRegistry,
            final @Reference ThingRegistry thingRegistry, final @Reference CronScheduler cronScheduler,
            final @Reference MetadataRegistry metadataRegistry, final @Reference JRuleRuleProvider ruleProvider,
            final @Reference PersistenceServiceRegistry persistenceServiceRegistry,
            final @Reference JRuleEventHandler eventHandler, final @Reference JRuleVoiceHandler voiceHandler,
            final @Reference JRuleItemHandler itemHandler, final @Reference JRuleThingHandler thingHandler,
            final @Reference JRuleTransformationHandler transformationHandler) {
        JRuleConfig config = new JRuleConfig(properties);
        config.initConfig();
        jRuleEngine = new JRuleEngine(config, itemRegistry, cronScheduler, ruleProvider, persistenceServiceRegistry);
        jRuleEngine.initialize();
        JRuleItemRegistry.setMetadataRegistry(metadataRegistry);
        jRuleHandler = new JRuleHandler(config, itemRegistry, itemChannelLinkRegistry, thingRegistry, eventSubscriber,
                metadataRegistry);
        delayedInit.call(this::init);
    }

    @Nullable
    private Boolean init() {
        JRuleLog.info(logger, LOG_NAME_FACTORY, "Initializing Java Rules Engine v{}", getBundleVersion());
        jRuleHandler.initialize();
        return Boolean.TRUE;
    }

    private String getBundleVersion() {
        return FrameworkUtil.getBundle(JRuleHandler.class).getVersion().toString();
    }

    @Deactivate
    public synchronized void dispose() {
        delayedInit.cancel();
        jRuleHandler.dispose();
    }
}
