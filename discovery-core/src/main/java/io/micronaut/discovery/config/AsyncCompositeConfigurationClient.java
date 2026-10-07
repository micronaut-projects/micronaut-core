/*
 * Copyright 2017-2026 original authors
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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.CompletionStagePublishers;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The {@link DefaultCompositeConfigurationClient} bean. It combines the
 * {@link ConfigurationClient#getPropertySourcesAsync(Environment)} stages of the configuration
 * clients without a publisher: the property sources are concatenated in the order of the clients,
 * and the first client that fails fails the result, like the publisher of
 * {@link #getPropertySources(Environment)}. It is final, so that a subclass of
 * {@link DefaultCompositeConfigurationClient} that overrides the publisher method is called
 * through it.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Singleton
@Primary
@BootstrapContextCompatible
final class AsyncCompositeConfigurationClient extends DefaultCompositeConfigurationClient {

    private final ConfigurationClient[] configurationClients;

    /**
     * @param configurationClients The configuration clients
     */
    AsyncCompositeConfigurationClient(ConfigurationClient[] configurationClients) {
        super(configurationClients);
        this.configurationClients = configurationClients;
    }

    @Override
    public CompletionStage<List<PropertySource>> getPropertySourcesAsync(Environment environment) {
        if (configurationClients.length == 0) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        List<CompletionStage<List<PropertySource>>> stages = new ArrayList<>(configurationClients.length);
        for (ConfigurationClient configurationClient : configurationClients) {
            stages.add(configurationClient.getPropertySourcesAsync(environment));
        }
        return CompletionStagePublishers.concat(stages);
    }
}
