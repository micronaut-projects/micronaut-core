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

import java.util.List;
import java.util.Optional;

/**
 * Interface for types that expose a list of {@link ServiceInstance}.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface ServiceInstanceList {

    /**
     * @return The service ID
     */
    String getID();

    /**
     * Returns the current list of services. Note: This method should NEVER block.
     *
     * @return The instances
     */
    List<ServiceInstance> getInstances();

    /**
     * @return The context path to use for requests to the service.
     */
    default Optional<String> getContextPath() {
        return Optional.empty();
    }
}
