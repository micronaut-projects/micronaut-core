/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.tracing.brave.sender;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.client.LoadBalancerResolver;
import io.micronaut.tracing.brave.BraveTracerConfiguration;
import zipkin2.reporter.Sender;

import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * A Factory for creating a Zipkin {@link Sender} based on {@link io.micronaut.tracing.brave.BraveTracerConfiguration.HttpClientSenderConfiguration}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
@Requires(beans = BraveTracerConfiguration.HttpClientSenderConfiguration.class)
public class HttpClientSenderFactory {
    private final BraveTracerConfiguration.HttpClientSenderConfiguration configuration;

    /**
     * Initialize the factory for creating Zipkin {@link Sender} with configurations.
     *
     * @param configuration The HTTP client sender configurations
     */
    protected HttpClientSenderFactory(BraveTracerConfiguration.HttpClientSenderConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * @param loadBalancerResolver A resolver capable of resolving references to services into a concrete loadbalance
     * @return The {@link Sender}
     */
    @Singleton
    @Requires(missingBeans = Sender.class)
    Sender zipkinSender(Provider<LoadBalancerResolver> loadBalancerResolver) {
        return configuration.getBuilder().build(loadBalancerResolver);
    }
}
