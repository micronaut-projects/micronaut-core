/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    private static final Boolean DEFAULT_REG_ENDPOINTS = true;
    private static final Boolean DEFAULT_IGNORE_AGENT = false;
    private static final Boolean DEFAULT_ADD_FACTORY = true;

    private String agentId = null;
    private String domain = null;
    private boolean addToFactory = DEFAULT_ADD_FACTORY;
    private boolean ignoreAgentNotFound = DEFAULT_IGNORE_AGENT;
    private boolean registerEndpoints = DEFAULT_REG_ENDPOINTS;

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
     * Sets the agent id.
     *
     * @param agentId The agent id
     */
    public void setAgentId(String agentId) {
        this.agentId = agentId;
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
     * Sets the domain to create a new server with.
     *
     * @param domain The domain
     */
    public void setDomain(String domain) {
        this.domain = domain;
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
     * Sets if the server should be kept in the factory. Default {@value #DEFAULT_ADD_FACTORY}.
     *
     * @param addToFactory The add to factory flag
     */
    public void setAddToFactory(boolean addToFactory) {
        this.addToFactory = addToFactory;
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

    /**
     * Sets to ignore the exception if the agent is not found. Default {@value #DEFAULT_IGNORE_AGENT}.
     *
     * @param ignoreAgentNotFound The ignoreAgentNotFound
     */
    public void setIgnoreAgentNotFound(boolean ignoreAgentNotFound) {
        this.ignoreAgentNotFound = ignoreAgentNotFound;
    }

    /**
     * If management beans should be registered for endpoints.
     *
     * @return True if endpoints should be registered
     */
    public boolean isRegisterEndpoints() {
        return registerEndpoints;
    }

    /**
     * Sets if endpoints should be registered. Default {@value #DEFAULT_REG_ENDPOINTS}.
     *
     * @param registerEndpoints The flag
     */
    public void setRegisterEndpoints(boolean registerEndpoints) {
        this.registerEndpoints = registerEndpoints;
    }
}
