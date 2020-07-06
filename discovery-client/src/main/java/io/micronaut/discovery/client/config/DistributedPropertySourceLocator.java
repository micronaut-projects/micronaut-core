/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.discovery.client.config;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.BootstrapPropertySourceLocator;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Blocking;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.config.ConfigurationClient;
import io.reactivex.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * <p>A {@link BootstrapPropertySourceLocator} implementation that uses the {@link ConfigurationClient} to find
 * available {@link PropertySource} instances from distributed configuration sources.</p>
 * <p>
 * <p>This implementation using a Blocking operation which is required during bootstrap which is configured to Timeout after
 * 10 seconds. The timeout can be configured with {@code micronaut.config.readTimeout} in configuration</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Requires(property = ConfigurationClient.ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
@BootstrapContextCompatible
public class DistributedPropertySourceLocator implements BootstrapPropertySourceLocator {
    private static final Logger LOG = LoggerFactory.getLogger(DistributedPropertySourceLocator.class);
    private final ConfigurationClient configurationClient;
    private final Duration readTimeout;

    /**
     * @param configurationClient The configuration client
     * @param readTimeout         The read timeout
     */
    public DistributedPropertySourceLocator(
        ConfigurationClient configurationClient,
        @Value("${" + ConfigurationClient.READ_TIMEOUT + ":10s}")
            Duration readTimeout) {

        this.configurationClient = configurationClient;
        this.readTimeout = readTimeout;
    }

    @Override
    @Blocking
    public Iterable<PropertySource> findPropertySources(Environment environment) throws ConfigurationException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Resolving configuration sources from client: {}", configurationClient);
        }
        try {
            Flowable<PropertySource> propertySourceFlowable = Flowable.fromPublisher(configurationClient.getPropertySources(environment));
            List<PropertySource> propertySources = propertySourceFlowable.timeout(
                readTimeout.toMillis(),
                TimeUnit.MILLISECONDS
            ).toList().blockingGet();
            if (LOG.isInfoEnabled()) {
                LOG.info("Resolved {} configuration sources from client: {}", propertySources.size(), configurationClient);
            }
            return propertySources;
        } catch (RuntimeException e) {
            if (e.getCause() instanceof TimeoutException) {
                throw new ConfigurationException("Read timeout occurred reading distributed configuration from client: " + configurationClient.getDescription(), e);
            } else {
                throw e;
            }
        }
    }
}
