package io.micronaut.configuration.jmx;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Configuration properties for JMX.
 *
 * The agent id will be used to find an MBean server. If no servers
 * are found, an exception will be thrown unless {@link #ignoreAgentNotFound}
 * is true.
 *
 * Next the default platform MBean server will be retrieved. If an error
 * is thrown, a new MBean server will be created using the {@link #domain}.
 * The newly created server will be registered with the MBeanFactory if
 * {@link #addToFactory} is true.
 *
 * @author James Kleeh
 * @since 1.0
 */
@ConfigurationProperties(JmxConfiguration.PREFIX)
public class JmxConfiguration {

    public static final String PREFIX = "jmx";

    protected String agentId = null;
    protected String domain = null;
    protected boolean addToFactory = true;
    protected boolean ignoreAgentNotFound = false;

    /**
     * If specified, it is expected the {@link javax.management.MBeanServerFactory#findMBeanServer}
     * will return a server. An error will be thrown otherwise.
     *
     * @return The agent id to find an existing MBean server
     */
    public String getAgentId() {
        return agentId;
    }

    /**
     * Used if {@link #getAgentId()} returns null and
     * {@link java.lang.management.ManagementFactory#getPlatformMBeanServer()} throws
     * an exception.
     *
     * @return The domain to create a new MBean server with
     */
    public String getDomain() {
        return domain;
    }

    /**
     * Only used if {@link #getAgentId()} returns null and
     * {@link java.lang.management.ManagementFactory#getPlatformMBeanServer()} throws
     * an exception.
     *
     * @return Whether a newly created MBean server will be
     * kept in the {@link javax.management.MBeanServerFactory}
     */
    public boolean isAddToFactory() {
        return addToFactory;
    }

    /**
     * If a server could not be found with the {@link #agentId},
     * an exception will be thrown unless this method returns
     * true.
     *
     * @return True, if the exception should not be thrown
     */
    public boolean isIgnoreAgentNotFound() {
        return ignoreAgentNotFound;
    }
}
