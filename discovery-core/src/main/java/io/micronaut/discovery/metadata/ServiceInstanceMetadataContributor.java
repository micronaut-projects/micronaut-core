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
package io.micronaut.discovery.metadata;

import io.micronaut.core.annotation.Indexed;
import io.micronaut.discovery.ServiceInstance;

import java.util.Map;

/**
 * Strategy interface for classes to contribute to {@link io.micronaut.discovery.ServiceInstance} metadata
 * when registering an instance with a discovery service.
 *
 * @author graemerocher
 * @since 1.0
 */
@Indexed(ServiceInstanceMetadataContributor.class)
public interface ServiceInstanceMetadataContributor {

    /**
     * Contribute metadata to the given {@link ServiceInstance} prior to registration.
     *
     * @param instance The instance
     * @param metadata The metadata
     */
    void contribute(ServiceInstance instance, Map<String, String> metadata);
}
