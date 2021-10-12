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
package io.micronaut.discovery.cloud;

import io.micronaut.context.env.Environment;

import java.util.Optional;

/**
 * Interface for resoling compute instance metadata.
 *
 * @author rvanderwerf
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ComputeInstanceMetadataResolver {

    /**
     * Resolves {@link ComputeInstanceMetadata} for the current environment if possible.
     *
     * @param environment The environment
     * @return The {@link ComputeInstanceMetadata}
     */
    Optional<ComputeInstanceMetadata> resolve(Environment environment);
}
