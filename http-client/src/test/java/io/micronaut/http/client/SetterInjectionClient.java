/*
 * Copyright 2026 original authors
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
package io.micronaut.http.client;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;
import org.reactivestreams.Publisher;

import java.util.Collections;
import java.util.List;

@Client(id = "setter", path = "/", configuration = SetterInjectionClientConfiguration.class)
@Requires(property = "spec.name", value = "ClientIntroductionAdviceSpec")
@Requires(beans = SetterInjectionClientConfiguration.class)
@BootstrapContextCompatible
@TypeHint(SetterInjectionClientOperations.class)
public abstract class SetterInjectionClient implements SetterInjectionClientOperations {

    private final SetterInjectionClientConfiguration defaultConfiguration = new SetterInjectionClientConfiguration();
    private SetterInjectionClientConfiguration configuration = defaultConfiguration;

    @Inject
    public void setConfiguration(SetterInjectionClientConfiguration configuration) {
        this.configuration = configuration;
    }

    public boolean isConfigurationInjected() {
        return configuration != defaultConfiguration;
    }

    @Override
    public String getDescription() {
        return "setter";
    }

    @Override
    public Publisher<List<String>> getServiceIds() {
        return Publishers.just(Collections.emptyList());
    }

    @Override
    public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
        return Publishers.just(Collections.emptyList());
    }

}
