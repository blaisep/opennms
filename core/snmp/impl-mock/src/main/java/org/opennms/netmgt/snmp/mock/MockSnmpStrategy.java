/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2011-2012 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2012 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.snmp.mock;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.ArrayUtils;
import org.opennms.core.utils.InetAddressUtils;
import org.opennms.core.utils.LogUtils;
import org.opennms.netmgt.snmp.CollectionTracker;
import org.opennms.netmgt.snmp.SnmpAgentAddress;
import org.opennms.netmgt.snmp.SnmpAgentConfig;
import org.opennms.netmgt.snmp.SnmpObjId;
import org.opennms.netmgt.snmp.SnmpStrategy;
import org.opennms.netmgt.snmp.SnmpTrapBuilder;
import org.opennms.netmgt.snmp.SnmpV1TrapBuilder;
import org.opennms.netmgt.snmp.SnmpV2TrapBuilder;
import org.opennms.netmgt.snmp.SnmpV3TrapBuilder;
import org.opennms.netmgt.snmp.SnmpV3User;
import org.opennms.netmgt.snmp.SnmpValue;
import org.opennms.netmgt.snmp.SnmpValueFactory;
import org.opennms.netmgt.snmp.SnmpWalker;
import org.opennms.netmgt.snmp.TrapNotificationListener;
import org.opennms.netmgt.snmp.TrapProcessorFactory;
import org.springframework.core.io.Resource;

public class MockSnmpStrategy implements SnmpStrategy {
    private static final SnmpValue[] EMPTY_SNMP_VALUE_ARRAY = new SnmpValue[0];

    // TOG's enterprise ID
    private static int s_enterpriseId = 5813;
    private static Map<SnmpAgentAddress,PropertyOidContainer> m_loaders = new HashMap<SnmpAgentAddress,PropertyOidContainer>();

    public MockSnmpStrategy() {
    }
    
    @Override
    public SnmpWalker createWalker(final SnmpAgentConfig agentConfig, final String name, final CollectionTracker tracker) {
        LogUtils.debugf(this, "createWalker(%s/%d, %s, %s)", InetAddressUtils.str(agentConfig.getAddress()), agentConfig.getPort(), name, tracker.getClass().getName());
        final SnmpAgentAddress aa = new SnmpAgentAddress(agentConfig.getAddress(), agentConfig.getPort());
        return new MockSnmpWalker(aa, m_loaders.get(aa), name, tracker, agentConfig.getMaxVarsPerPdu());
    }

    @Override
    public SnmpValue set(final SnmpAgentConfig agentConfig, final SnmpObjId oid, final SnmpValue value) {
        final SnmpAgentAddress aa = new SnmpAgentAddress(agentConfig.getAddress(), agentConfig.getPort());
        if (!m_loaders.containsKey(aa)) {
            return null;
        }
        return m_loaders.get(aa).set(oid, value);
    }

    @Override
    public SnmpValue[] set(final SnmpAgentConfig agentConfig, final SnmpObjId[] oids, final SnmpValue[] values) {
        final SnmpAgentAddress aa = new SnmpAgentAddress(agentConfig.getAddress(), agentConfig.getPort());
        if (!m_loaders.containsKey(aa)) {
            return new SnmpValue[values.length];
        }
        return m_loaders.get(aa).set(oids, values);
    }

    @Override
    public SnmpValue get(final SnmpAgentConfig agentConfig, final SnmpObjId oid) {
        final SnmpAgentAddress aa = new SnmpAgentAddress(agentConfig.getAddress(), agentConfig.getPort());
        if (!m_loaders.containsKey(aa)) {
            return null;
        }

        return m_loaders.get(aa).findValueForOid(oid);
    }

    @Override
    public SnmpValue[] get(final SnmpAgentConfig agentConfig, final SnmpObjId[] oids) {
        final SnmpAgentAddress aa = new SnmpAgentAddress(agentConfig.getAddress(), agentConfig.getPort());

        final PropertyOidContainer container = m_loaders.get(aa);
        if (container == null) return new SnmpValue[oids.length];
        final List<SnmpValue> values = new ArrayList<SnmpValue>();

        for (final SnmpObjId oid : oids) {
    		values.add(container.findValueForOid(oid));
        }
        return values.toArray(EMPTY_SNMP_VALUE_ARRAY);
    }

    @Override
    public SnmpValue getNext(final SnmpAgentConfig agentConfig, final SnmpObjId oid) {
        final SnmpAgentAddress aa = new SnmpAgentAddress(agentConfig.getAddress(), agentConfig.getPort());
        if (!m_loaders.containsKey(aa)) {
            return null;
        }

        final PropertyOidContainer container = m_loaders.get(aa);
        return container.findNextValueForOid(oid);
    }

    @Override
    public SnmpValue[] getNext(final SnmpAgentConfig agentConfig, final SnmpObjId[] oids) {
        final SnmpAgentAddress aa = new SnmpAgentAddress(agentConfig.getAddress(), agentConfig.getPort());
        if (!m_loaders.containsKey(aa)) {
            return null;
        }

        final PropertyOidContainer container = m_loaders.get(aa);
        final List<SnmpValue> values = new ArrayList<SnmpValue>();

        for (final SnmpObjId oid : oids) {
            values.add(container.findNextValueForOid(oid));
        }
        return values.toArray(EMPTY_SNMP_VALUE_ARRAY);
    }

    @Override
    public SnmpValue[] getBulk(final SnmpAgentConfig agentConfig, final SnmpObjId[] oids) {
        return getNext(agentConfig, oids);
    }

    @Override
    public void registerForTraps(final TrapNotificationListener listener, final TrapProcessorFactory processorFactory, final InetAddress address, final int snmpTrapPort) throws IOException {
        LogUtils.warnf(this, "Can't register for traps.  No network in the MockSnmpStrategy!");
    }

    @Override
    public void registerForTraps(final TrapNotificationListener listener, final TrapProcessorFactory processorFactory, final int snmpTrapPort) throws IOException {
        LogUtils.warnf(this, "Can't register for traps.  No network in the MockSnmpStrategy!");
    }

    @Override
    public void registerForTraps(TrapNotificationListener listener, TrapProcessorFactory processorFactory, InetAddress address, int snmpTrapPort, List<SnmpV3User> snmpv3Users) throws IOException {
        LogUtils.warnf(this, "Can't register for traps.  No network in the MockSnmpStrategy!");
    }

    @Override
    public void unregisterForTraps(final TrapNotificationListener listener, final InetAddress address, final int snmpTrapPort) throws IOException {
    }

    @Override
    public void unregisterForTraps(final TrapNotificationListener listener, final int snmpTrapPort) throws IOException {
    }

    @Override
    public SnmpValueFactory getValueFactory() {
    	return new MockSnmpValueFactory();
    }

    @Override
    public SnmpV1TrapBuilder getV1TrapBuilder() {
    	throw new UnsupportedOperationException("Not yet implemented!");
//        return new NullSnmpV1TrapBuilder();
    }

    @Override
    public SnmpTrapBuilder getV2TrapBuilder() {
    	throw new UnsupportedOperationException("Not yet implemented!");
//        return new NullSnmpTrapBuilder();
    }

    @Override
    public SnmpV3TrapBuilder getV3TrapBuilder() {
    	throw new UnsupportedOperationException("Not yet implemented!");
//        return new NullSnmpV3TrapBuilder();
    }

    @Override
    public SnmpV2TrapBuilder getV2InformBuilder() {
    	throw new UnsupportedOperationException("Not yet implemented!");
//        return new NullSnmpV2TrapBuilder();
    }

    @Override
    public SnmpV3TrapBuilder getV3InformBuilder() {
    	throw new UnsupportedOperationException("Not yet implemented!");
//        return new NullSnmpV3TrapBuilder();
    }

    @Override
    public byte[] getLocalEngineID() {
        // lovingly stolen from SNMP4J
        final byte[] engineID = new byte[5];
        engineID[0] = (byte) (0x80 | ((s_enterpriseId >> 24) & 0xFF));
        engineID[1] = (byte) ((s_enterpriseId >> 16) & 0xFF);
        engineID[2] = (byte) ((s_enterpriseId >> 8) & 0xFF);
        engineID[3] = (byte) (s_enterpriseId & 0xFF);
        byte[] ip = new byte[0];

        ip = InetAddressUtils.getLocalHostAddress().getAddress();

        if (ip.length == 4) {
            // IPv4
            engineID[4] = 1;
        } else if (ip.length == 16) {
            // IPv6
            engineID[4] = 2;
        } else {
            // Text
            engineID[4] = 4;
        }
        return ArrayUtils.addAll(engineID, ip);
    }

	public static void setDataForAddress(final SnmpAgentAddress agentAddress, final Resource resource) throws IOException {
        m_loaders.put(agentAddress, new PropertyOidContainer(resource));
    }
    
    public static void removeHost(final SnmpAgentAddress agentAddr) {
        m_loaders.remove(agentAddr);
    }

	public static void resetData() {
		m_loaders.clear();
	}

}
