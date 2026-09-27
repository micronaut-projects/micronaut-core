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

import io.micronaut.core.annotation.Experimental;

import java.util.Optional;
import java.util.Set;

/**
 * Retry policies by name, configured under {@code micronaut.retry.policies.<name>}, see
 * {@link NamedRetryPolicyConfiguration}. A method annotated
 * {@code @Retryable(name = "...")} takes its settings from the policy of the name, except those
 * set explicitly on the annotation; other modules, e.g. the gateway, can share the same policies.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface RetryRegistry {

    /**
     * @param name The name
     * @return The policy of the name, if it is configured
     */
    Optional<RetryPolicy> findPolicy(String name);

    /**
     * @param name The name
     * @return The policy of the name
     * @throws IllegalArgumentException if no policy of the name is configured
     */
    default RetryPolicy getPolicy(String name) {
        return findPolicy(name).orElseThrow(() -> new IllegalArgumentException(
            "No retry policy named [" + name + "] is configured, expected one under "
                + NamedRetryPolicyConfiguration.PREFIX + "." + name + ", configured: " + getNames()
        ));
    }

    /**
     * The retry operations of the policy of a name.
     *
     * @param name The name
     * @return The retry operations
     * @throws IllegalArgumentException if no policy of the name is configured
     */
    RetryOperations retry(String name);

    /**
     * @return The names of the configured policies
     */
    Set<String> getNames();
}
