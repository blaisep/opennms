/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2006-2014 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2014 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.poller.remote.support;

import static org.opennms.netmgt.poller.remote.PollerBackEnd.HOST_ADDRESS_KEY;
import static org.opennms.netmgt.poller.remote.PollerBackEnd.HOST_NAME_KEY;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.opennms.core.utils.InetAddressUtils;
import org.opennms.core.xml.JaxbUtils;
import org.opennms.netmgt.poller.DistributionContext;
import org.opennms.netmgt.poller.PollStatus;
import org.opennms.netmgt.poller.remote.ConfigurationChangedListener;
import org.opennms.netmgt.poller.remote.PollService;
import org.opennms.netmgt.poller.remote.PolledService;
import org.opennms.netmgt.poller.remote.Poller;
import org.opennms.netmgt.poller.remote.PollerBackEnd;
import org.opennms.netmgt.poller.remote.PollerConfiguration;
import org.opennms.netmgt.poller.remote.PollerFrontEnd;
import org.opennms.netmgt.poller.remote.PollerSettings;
import org.opennms.netmgt.poller.remote.ServicePollState;
import org.opennms.netmgt.poller.remote.ServicePollStateChangedListener;
import org.opennms.netmgt.poller.remote.TimeAdjustment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * <p>ScanReportPollerFrontEnd class.</p>
 *
 * @author Seth
 * @author <a href="mailto:brozow@opennms.org">Mathew Brozowski</a>
 */
public class ScanReportPollerFrontEnd implements PollerFrontEnd, InitializingBean, DisposableBean {

    public static enum ScanReportProperties {
        percentageComplete
    }

    private static final Logger LOG = LoggerFactory.getLogger(ScanReportPollerFrontEnd.class);

    private class Initial extends PollerFrontEndState {
        @Override
        public void initialize() {
            try {
                assertNotNull(m_backEnd, "pollerBackEnd");
                assertNotNull(m_pollService, "pollService");
                assertNotNull(m_pollerSettings, "pollerSettings");

                final String monitorId = m_pollerSettings.getMonitoringSystemId();

                // If the monitor isn't registered yet...
                if (monitorId == null) {
                    // Change to the 'registering' state
                    setState(new Registering());
                } else { 
                    // TODO: Check return value?
                    // TODO: Add metadata values to the details
                    m_backEnd.pollerStarting(getMonitoringSystemId(), getDetails());
                    // Change the state to running so we're ready to execute polls
                    setState(new Running());
                    // TODO: Execute the scan
                    performServiceScans();
                }
            } catch (final Throwable e) {
                setState(new FatalExceptionOccurred(e));

                // rethrow the exception on initialize so we exit if we fail to initialize
                throw e;
            }
        }

        @Override
        public boolean isRegistered() { return false; }

        @Override
        public boolean isStarted() { return false; }
    }

    private class Registering extends PollerFrontEndState {
        @Override
        public boolean isRegistered() { return false; }

        @Override
        public boolean isStarted() { return false; }

        @Override
        public void register(final String location) {
            try {
                // Create the location entry
                doRegister(location);
                // TODO: Check return value?
                // TODO: Add metadata values to the details
                m_backEnd.pollerStarting(getMonitoringSystemId(), getDetails());
                // Change the state to running so we're ready to execute polls
                setState(new Running());
                // TODO: Execute the scan
                performServiceScans();
            } catch (final Throwable e) {
                LOG.warn("Unable to register.", e);
                setState(new FatalExceptionOccurred(e));
            }
        }
    }

    private class Running extends PollerFrontEndState {
        @Override
        public boolean isRegistered() { return true; }

        @Override
        public boolean isStarted() { return true; }

        @Override
        public void pollService(final Integer polledServiceId) {
            // Don't do scheduled polls
        }
    }

    private PollerFrontEndState m_state = new Initial();

    // injected dependencies
    private PollerBackEnd m_backEnd;

    private PollerSettings m_pollerSettings;

    private PollService m_pollService;

    private TimeAdjustment m_timeAdjustment;

    // listeners
    private List<PropertyChangeListener> m_propertyChangeListeners = new LinkedList<PropertyChangeListener>();

    // current configuration
    private PollerConfiguration m_pollerConfiguration;

    private Map<String, String> m_metadata = Collections.emptyMap();

    /** {@inheritDoc} */
    @Override
    public void addConfigurationChangedListener(ConfigurationChangedListener l) {
    }

    /** {@inheritDoc} */
    @Override
    public void addPropertyChangeListener(final PropertyChangeListener listener) {
        m_propertyChangeListeners.add(0, listener);
    }

    /** {@inheritDoc} */
    @Override
    public void addServicePollStateChangedListener(final ServicePollStateChangedListener listener) {
    }

    /**
     * <p>afterPropertiesSet</p>
     *
     * @throws java.lang.Exception if any.
     */
    @Override
    public void afterPropertiesSet() {
        assertNotNull(m_timeAdjustment, "timeAdjustment");
        assertNotNull(m_backEnd, "pollerBackEnd");
        assertNotNull(m_pollService, "pollService");
        assertNotNull(m_pollerSettings, "pollerSettings");
    }

    /**
     * <p>destroy</p>
     *
     * @throws java.lang.Exception if any.
     */
    @Override
    public void destroy() {
        // Do nothing
    }

    /**
     * <p>doRegister</p>
     *
     * @param location a {@link java.lang.String} object.
     */
    private void doRegister(final String location) {

        String monitoringSystemId = m_backEnd.registerLocationMonitor(location);

        try {
            m_pollerSettings.setMonitoringSystemId(monitoringSystemId);
        } catch (Throwable e) {
            // TODO: Should we start anyway? I guess so.
            LOG.warn("Unable to set monitoring system ID: " + e.getMessage(), e);
        }
    }

    /**
     * Construct a list of certain system properties and metadata about this
     * monitoring system that will be relayed back to the {@link PollerBackEnd}.
     *
     * @return a {@link java.util.Map} object.
     */
    public static Map<String, String> getDetails() {
        final HashMap<String, String> details = new HashMap<String, String>();
        final Properties p = System.getProperties();

        for (final Map.Entry<Object, Object> e : p.entrySet()) {
            if (e.getKey().toString().startsWith("os.") && e.getValue() != null) {
                details.put(e.getKey().toString(), e.getValue().toString());
            }
        }

        final InetAddress us = InetAddressUtils.getLocalHostAddress();
        details.put(HOST_ADDRESS_KEY, InetAddressUtils.str(us));
        details.put(HOST_NAME_KEY, us.getHostName());

        return Collections.unmodifiableMap(details);
    }

    /**
     * <p>getMonitoringSystemId</p>
     *
     * @return a {@link java.lang.String} object.
     */
    @Override
    public String getMonitoringSystemId() {
        return m_pollerSettings.getMonitoringSystemId();
    }

    /**
     * <p>getMonitorName</p>
     *
     * @return a {@link java.lang.String} object.
     */
    @Override
    public String getMonitorName() {
        return (isRegistered() ? m_backEnd.getMonitorName(getMonitoringSystemId()) : "");
    }

    /**
     * <p>getPolledServices</p>
     *
     * @return a {@link java.util.Collection} object.
     */
    @Override
    public Collection<PolledService> getPolledServices() {
        return Arrays.asList(m_pollerConfiguration.getPolledServices());
    }

    /**
     * <p>getPollerPollState</p>
     *
     * @return a {@link java.util.List} object.
     */
    @Override
    public List<ServicePollState> getPollerPollState() {
        return Collections.emptyList();
    }

    /** {@inheritDoc} */
    @Override
    public ServicePollState getServicePollState(int polledServiceId) {
        return null;
    }

    /**
     * <p>isRegistered</p>
     *
     * @return a boolean.
     */
    @Override
    public boolean isRegistered() {
        return m_state.isRegistered();
    }

    /**
     * <p>isStarted</p>
     *
     * @return a boolean.
     */
    @Override
    public boolean isStarted() {
        return m_state.isStarted();
    }

    /** {@inheritDoc} */
    @Override
    public void pollService(final Integer polledServiceId) {
        m_state.pollService(polledServiceId);
    }

    /** {@inheritDoc} */
    @Override
    public void register(final String monitoringLocation) {
        m_state.register(monitoringLocation);
    }

    /** {@inheritDoc} */
    @Override
    public void removeConfigurationChangedListener(final ConfigurationChangedListener listener) {
    }

    /** {@inheritDoc} */
    @Override
    public void removePropertyChangeListener(final PropertyChangeListener listener) {
        m_propertyChangeListeners.remove(listener);
    }

    /** {@inheritDoc} */
    @Override
    public void removeServicePollStateChangedListener(final ServicePollStateChangedListener listener) {
    }

    /** {@inheritDoc} */
    @Override
    public void setInitialPollTime(final Integer polledServiceId, final Date initialPollTime) {
    }

    /**
     * <p>setPollerBackEnd</p>
     *
     * @param backEnd a {@link org.opennms.netmgt.poller.remote.PollerBackEnd} object.
     */
    public void setPollerBackEnd(final PollerBackEnd backEnd) {
        m_backEnd = backEnd;
    }

    /**
     * <p>setPollerSettings</p>
     *
     * @param settings a {@link org.opennms.netmgt.poller.remote.PollerSettings} object.
     */
    public void setPollerSettings(final PollerSettings settings) {
        m_pollerSettings = settings;
    }

    /**
     * @param timeAdjustment the timeAdjustment to set
     */
    public void setTimeAdjustment(TimeAdjustment timeAdjustment) {
        m_timeAdjustment = timeAdjustment;
    }

    /**
     * <p>setPollService</p>
     *
     * @param pollService a {@link org.opennms.netmgt.poller.remote.PollService} object.
     */
    public void setPollService(final PollService pollService) {
        m_pollService = pollService;
    }

    private static void assertNotNull(final Object propertyValue, final String propertyName) {
        Assert.state(propertyValue != null, propertyName + " must be set for instances of " + ScanReportPollerFrontEnd.class.getName());
    }

    /**
     * TODO: Change this method so that instead of loading the config and firing configuration
     * changes to a {@link Poller}, we actually initiate the single scan.
     */
    private void performServiceScans() {

        firePropertyChange(ScanReportProperties.percentageComplete.toString(), null, 0.0);

        ScanReport scanReport = new ScanReport();
        System.err.println("metadata: " + m_metadata);
        scanReport.setCustomerAccountNumber(m_metadata.get("customer-account-number"));
        scanReport.setCustomerName(m_metadata.get("customer-name"));
        scanReport.setReferenceId(m_metadata.get("reference-id"));

        try {
            m_pollService.setServiceMonitorLocators(m_backEnd.getServiceMonitorLocators(DistributionContext.REMOTE_MONITOR));
            m_pollerConfiguration = retrieveLatestConfiguration();

            PolledService[] services = getPolledServices().toArray(new PolledService[0]);
            for (int i = 0; i < services.length; i++) {
                PolledService service = services[i];

                // Initialize the monitor for the service
                m_pollService.initialize(service);

                try {
                    final PollStatus result = doPoll(service);
                    if (result == null) {
                        LOG.warn("Null poll result for service {}", service.getServiceId());
                    } else {
                        LOG.info(
                                 new ToStringBuilder(this)
                                 .append("statusName", result.getStatusName())
                                 .append("reason", result.getReason())
                                 .toString()
                                );
                        scanReport.addPollStatus(result);
                    }
                } catch (Throwable e) {
                    LOG.error("Unexpected exception occurred while polling service ID {}", service.getServiceId(), e);
                    setState(new FatalExceptionOccurred(e));
                }

                firePropertyChange(ScanReportProperties.percentageComplete.toString(), null, ((double)i / (double)services.length));
            }
        } catch (final Throwable e) {
            LOG.error("Error while performing scan", e);
        }

        // Set the percentage complete to 100%
        firePropertyChange(ScanReportProperties.percentageComplete.toString(), null, 1.0);

        LOG.debug("Returning scan report: " + JaxbUtils.marshal(scanReport));

        // Fire an exitNecessary event with the scanReport as the parameter
        firePropertyChange(PollerFrontEndStates.exitNecessary.toString(), null, scanReport);
    }

    private PollerConfiguration retrieveLatestConfiguration() {
        PollerConfiguration config = m_backEnd.getPollerConfiguration(getMonitoringSystemId());
        m_timeAdjustment.setMasterTime(config.getServerTime());
        return config;
    }

    private PollStatus doPoll(final PolledService polledService) {
        return m_pollService.poll(polledService);
    }

    private void firePropertyChange(final String propertyName, final Object oldValue, final Object newValue) {
        if (nullSafeEquals(oldValue, newValue)) {
            // no change no event
            return;

        }
        final PropertyChangeEvent event = new PropertyChangeEvent(this, propertyName, oldValue, newValue);

        for (final PropertyChangeListener listener : m_propertyChangeListeners) {
            listener.propertyChange(event);
        }
    }

    private static boolean nullSafeEquals(final Object oldValue, final Object newValue) {
        return (oldValue == newValue ? true : ObjectUtils.nullSafeEquals(oldValue, newValue));
    }

    private void setState(final PollerFrontEndState newState) {
        final boolean started = isStarted();
        final boolean registered = isRegistered();
        m_state = newState;
        firePropertyChange(PollerFrontEndStates.started.toString(), started, isStarted());
        firePropertyChange(PollerFrontEndStates.registered.toString(), registered, isRegistered());

    }

    public void setMetadata(final Map<String,String> metadata) {
        m_metadata = metadata;
    }

    @Override
    public void checkConfig() {
    }

    @Override
    public void initialize() {
        m_state.initialize();
    }

    @Override
    public boolean isExitNecessary() {
        return false;
    }

    @Override
    public void stop() {
        // Do nothing
    }
}
