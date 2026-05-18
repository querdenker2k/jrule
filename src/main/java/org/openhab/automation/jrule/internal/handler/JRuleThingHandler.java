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

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.openhab.automation.jrule.items.JRuleItem;
import org.openhab.automation.jrule.items.JRuleItemRegistry;
import org.openhab.automation.jrule.things.JRuleChannel;
import org.openhab.automation.jrule.things.JRuleThingStatus;
import org.openhab.core.thing.*;
import org.openhab.core.thing.link.ItemChannelLinkRegistry;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * The {@link JRuleThingHandler} provides access to thing actions
 *
 * @author Arne Seime - Initial contribution
 */
@Component
public class JRuleThingHandler {

    private static volatile JRuleThingHandler instance;

    private final ThingRegistry thingRegistry;
    private final ThingManager thingManager;
    private final ItemChannelLinkRegistry itemChannelLinkRegistry;

    @Activate
    public JRuleThingHandler(@Reference ThingRegistry thingRegistry, @Reference ThingManager thingManager,
            @Reference ItemChannelLinkRegistry itemChannelLinkRegistry) {
        this.thingRegistry = thingRegistry;
        this.thingManager = thingManager;
        this.itemChannelLinkRegistry = itemChannelLinkRegistry;
        instance = this;
    }

    @Deactivate
    void deactivate() {
        instance = null;
    }

    public static JRuleThingHandler get() {
        return instance;
    }

    public void disable(String thingUID) {
        setEnabled(thingUID, false);
    }

    public void enable(String thingUID) {
        setEnabled(thingUID, true);
    }

    public Collection<Thing> getThings() {
        return thingRegistry.getAll();
    }

    private void setEnabled(String thingUID, boolean enabled) {
        thingRegistry.getAll().forEach(t -> t.getBridgeUID());
        Thing thing = thingRegistry.get(new ThingUID(thingUID));
        if (thing != null) {
            thingManager.setEnabled(new ThingUID(thingUID), enabled);
        }
    }

    public JRuleThingStatus getStatus(String thingUID) {
        Thing thing = thingRegistry.get(new ThingUID(thingUID));
        if (thing != null) {
            return JRuleThingStatus.valueOf(thing.getStatus().name());
        } else {
            return JRuleThingStatus.THING_UNKNOWN;
        }
    }

    /**
     * Get all channels of a thing
     * 
     * @param thingUID the thing UID
     * @return list of all channels
     */
    public List<JRuleChannel> getChannels(String thingUID) {
        Thing thing = thingRegistry.get(new ThingUID(thingUID));
        if (thing != null) {
            return thing.getChannels().stream().map(channel -> new JRuleChannel(channel.getUID().toString())).toList();
        } else {
            return List.of();
        }
    }

    public Set<JRuleItem> getLinkedItems(JRuleChannel channel) {
        return itemChannelLinkRegistry.getLinkedItemNames(new ChannelUID(channel.getChannelUID())).stream()
                .map(itemName -> JRuleItemRegistry.get(itemName)).collect(Collectors.toSet());
    }
}
