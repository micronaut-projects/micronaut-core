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
package io.micronaut.retry;

import io.micronaut.core.annotation.Internal;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The default {@link RetryRegistry}. A policy is built from its configuration on first use, so an
 * invalid policy fails its own users only; {@link NamedRetryPolicyValidator} reports it at startup.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Singleton
final class DefaultRetryRegistry implements RetryRegistry {

    private final Map<String, NamedRetryPolicyConfiguration> configurations = new TreeMap<>();
    private final Map<String, RetryPolicy> policies = new ConcurrentHashMap<>();
    private final Map<String, RetryOperations> operations = new ConcurrentHashMap<>();
    private final RetryOperationsFactory retryOperationsFactory;

    DefaultRetryRegistry(List<NamedRetryPolicyConfiguration> configurations,
                         RetryOperationsFactory retryOperationsFactory) {
        for (NamedRetryPolicyConfiguration configuration : configurations) {
            this.configurations.put(configuration.getName(), configuration);
        }
        this.retryOperationsFactory = retryOperationsFactory;
    }

    @Override
    public Optional<RetryPolicy> findPolicy(String name) {
        NamedRetryPolicyConfiguration configuration = configurations.get(Objects.requireNonNull(name, "name"));
        if (configuration == null) {
            return Optional.empty();
        }
        return Optional.of(policies.computeIfAbsent(name, n -> configuration.toPolicy()));
    }

    @Override
    public RetryOperations retry(String name) {
        RetryPolicy policy = getPolicy(name);
        return operations.computeIfAbsent(name, n -> retryOperationsFactory.createRetryOperations(policy));
    }

    @Override
    public Set<String> getNames() {
        return Collections.unmodifiableSet(configurations.keySet());
    }
}
