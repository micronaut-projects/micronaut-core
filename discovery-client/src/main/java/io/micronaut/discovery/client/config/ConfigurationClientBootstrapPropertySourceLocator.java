/*
 * Copyright 2018 original authors
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
package io.micronaut.discovery.client.config;

import io.micronaut.context.env.BootstrapPropertySourceLocator;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Blocking;
import io.micronaut.discovery.config.ConfigurationClient;
import io.reactivex.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A {@link BootstrapPropertySourceLocator} implementation that blocks using RxJava when reading the configuration necessary to bootstrap a server
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class ConfigurationClientBootstrapPropertySourceLocator implements BootstrapPropertySourceLocator {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationClientBootstrapPropertySourceLocator.class);
    private final ConfigurationClient configurationClient;

    public ConfigurationClientBootstrapPropertySourceLocator(ConfigurationClient configurationClient) {
        this.configurationClient = configurationClient;
    }

    @Override
    @Blocking
    public Iterable<PropertySource> findPropertySources(Environment environment) throws ConfigurationException {
        if(LOG.isDebugEnabled()) {
            LOG.debug("Resolving configuration sources from client: {}", configurationClient);
        }
        Flowable<PropertySource> propertySourceFlowable = Flowable.fromPublisher(configurationClient.getPropertySources(environment));
        List<PropertySource> propertySources = propertySourceFlowable.timeout(
                5,
                TimeUnit.SECONDS
        ).toList().blockingGet();
        if(LOG.isDebugEnabled()) {
            LOG.debug("Resolved {} configuration sources from client: {}", propertySources.size(), configurationClient);
        }
        return propertySources;
    }
}
