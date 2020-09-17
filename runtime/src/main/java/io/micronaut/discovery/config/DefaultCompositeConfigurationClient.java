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
package io.micronaut.discovery.config;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.util.ArrayUtils;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The default {@link ConfigurationClient} implementation.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Primary
@BootstrapContextCompatible
public class DefaultCompositeConfigurationClient implements ConfigurationClient {

    private final ConfigurationClient[] configurationClients;

    /**
     * Create a default composite configuration client from given configuration clients.
     *
     * @param configurationClients The configuration clients
     */
    public DefaultCompositeConfigurationClient(ConfigurationClient[] configurationClients) {
        this.configurationClients = configurationClients;
    }

    @Override
    public String getDescription() {
        return toString();
    }

    @Override
    public Publisher<PropertySource> getPropertySources(Environment environment) {
        if (ArrayUtils.isEmpty(configurationClients)) {
            return Flowable.empty();
        }
        List<Publisher<PropertySource>> publishers = Arrays.stream(configurationClients)
            .map(configurationClient -> configurationClient.getPropertySources(environment))
            .collect(Collectors.toList());

        return Flowable.merge(publishers);
    }

    @Override
    public String toString() {
        return "compositeConfigurationClient(" + Arrays.stream(configurationClients).map(ConfigurationClient::getDescription).collect(Collectors.joining(",")) + ")";
    }
}
