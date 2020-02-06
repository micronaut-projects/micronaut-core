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
package io.micronaut.discovery.event;

import io.micronaut.discovery.ServiceInstance;

/**
 * An event triggered when a {@link ServiceInstance} has shutdown and will no longer service requests.
 *
 * @author graemerocher
 * @since 1.0
 */
public class ServiceStoppedEvent extends AbstractServiceInstanceEvent {

    /**
     * Constructs a prototypical Event.
     *
     * @param source The object on which the Event initially occurred.
     * @throws IllegalArgumentException if source is null.
     */
    public ServiceStoppedEvent(ServiceInstance source) {
        super(source);
    }
}
