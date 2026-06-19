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
package org.openhab.automation.jrule.items;

import java.util.Set;
import java.util.stream.Collectors;

import org.openhab.automation.jrule.internal.handler.JRuleEventHandler;
import org.openhab.automation.jrule.rules.value.JRuleRefreshValue;
import org.openhab.automation.jrule.rules.value.JRuleValue;

/**
 * The {@link JRuleGroupItem} JRule Item
 *
 * @author Robert Delbrück - Initial contribution
 */
public interface JRuleGroupItem<I extends JRuleItem> extends JRuleItem {
    @Deprecated
    default Set<String> members() {
        return JRuleEventHandler.getGroupMemberNames(getName(), false);
    }

    default Set<? extends JRuleItem> memberItems() {
        return memberItems(false);
    }

    default Set<? extends JRuleItem> memberItems(boolean recursive) {
        return JRuleEventHandler.getGroupMemberItems(getName(), recursive).stream().collect(Collectors.toSet());
    }

    default void sendUncheckedCommand(JRuleValue command) {
        JRuleEventHandler.sendCommand(getName(), command);
        JRuleEventHandler.getGroupMemberItems(getName(), false).forEach(i -> i.sendUncheckedCommand(command));
    }

    default void postUncheckedUpdate(JRuleValue state) {
        JRuleEventHandler.postUpdate(getName(), state);
        JRuleEventHandler.getGroupMemberItems(getName(), false).forEach(i -> i.postUncheckedUpdate(state));
    }

    default void postUpdate(JRuleRefreshValue state) {
        JRuleEventHandler.postUpdate(getName(), state);
        JRuleEventHandler.getGroupMemberItems(getName(), false).forEach(i -> i.postUncheckedUpdate(state));
    }

    default void postNullUpdate() {
        JRuleEventHandler.postUpdate(getName(), null);
        JRuleEventHandler.getGroupMemberItems(getName(), false).forEach(JRuleItem::postNullUpdate);
    }

    @Override
    default boolean isGroup() {
        return true;
    }
}
