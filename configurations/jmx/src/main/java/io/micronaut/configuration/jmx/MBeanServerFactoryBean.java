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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.core.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * A factory to create a management bean server bean.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Factory
public class MBeanServerFactoryBean {

    private static final Logger LOG = LoggerFactory.getLogger(MBeanServerFactoryBean.class);

    private final JmxConfiguration configuration;
    private final ApplicationContext applicationContext;

    /**
     * @param configuration The JMX configuration
     * @param applicationContext The application context
     */
    public MBeanServerFactoryBean(JmxConfiguration configuration, ApplicationContext applicationContext) {
        this.configuration = configuration;
        this.applicationContext = applicationContext;
    }

    /**
     * @return The management bean server
     */
    @Requires(missingBeans = MBeanServer.class)
    @Singleton
    public MBeanServer getMBeanServer() {
        String agentId = configuration.getAgentId();
        if (StringUtils.isNotEmpty(agentId)) {
            List<MBeanServer> servers = MBeanServerFactory.findMBeanServer(agentId);
            if (!servers.isEmpty()) {
                if (servers.size() > 1 && LOG.isWarnEnabled()) {
                    LOG.warn("More than one MBeanServer exists for agentId [{}]. Using the first one found.", agentId);
                }

                return servers.get(0);
            } else if (!configuration.isIgnoreAgentNotFound()) {
                throw new BeanInstantiationException("Could not find MBeanServer with agentId [" + agentId + "].");
            }
        }

        try {
            return ManagementFactory.getPlatformMBeanServer();
        } catch (SecurityException e) {
            if (LOG.isInfoEnabled()) {
                LOG.info("Could not retrieve the platform MBean server. Creating a new one...");
            }
        }

        String domain = configuration.getDomain();

        MBeanServer server;

        if (configuration.isAddToFactory()) {
            server = MBeanServerFactory.createMBeanServer(domain);
            applicationContext.registerSingleton((ApplicationEventListener<ShutdownEvent>) event -> {
                MBeanServerFactory.releaseMBeanServer(server);
            }, false);
        } else {
            server = MBeanServerFactory.newMBeanServer(domain);
        }

        return server;
    }
}
