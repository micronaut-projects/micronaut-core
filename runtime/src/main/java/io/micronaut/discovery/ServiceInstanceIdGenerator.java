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
package io.micronaut.discovery;

import io.micronaut.context.env.Environment;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Interface for generating IDs for {@link ServiceInstance}.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface ServiceInstanceIdGenerator {

    /**
     * Generates a service ID.
     *
     * @param environment     The environment
     * @param serviceInstance The service instance
     * @return The generated ID. Never null
     */
    @NonNull
    String generateId(Environment environment, ServiceInstance serviceInstance);
}
