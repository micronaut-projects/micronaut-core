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
import io.micronaut.core.async.publisher.CompletionStagePublishers;
import io.micronaut.core.util.ArrayUtils;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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
            return Flux.empty();
        }
        List<Publisher<PropertySource>> publishers = Arrays.stream(configurationClients)
            .map(configurationClient -> configurationClient.getPropertySources(environment))
            .collect(Collectors.toList());

        return Flux.merge(publishers);
    }

    /**
     * The property sources of all the configuration clients, in the order of the clients. The
     * first client that fails fails the result, like the publisher of
     * {@link #getPropertySources(Environment)}.
     *
     * @param environment The environment
     * @return A {@link CompletionStage} completed with the {@link PropertySource} instances of all the clients
     */
    @Override
    public CompletionStage<List<PropertySource>> getPropertySourcesAsync(Environment environment) {
        if (ArrayUtils.isEmpty(configurationClients)) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        List<CompletionStage<List<PropertySource>>> stages = new ArrayList<>(configurationClients.length);
        for (ConfigurationClient configurationClient : configurationClients) {
            stages.add(configurationClient.getPropertySourcesAsync(environment));
        }
        return CompletionStagePublishers.concat(stages);
    }

    @Override
    public String toString() {
        return "compositeConfigurationClient(" + Arrays.stream(configurationClients).map(ConfigurationClient::getDescription).collect(Collectors.joining(",")) + ")";
    }
}
