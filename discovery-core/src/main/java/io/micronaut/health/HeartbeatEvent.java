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
package io.micronaut.health;

import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.event.AbstractServiceInstanceEvent;

/**
 * A heartbeat event is an event fired periodically and configured by {@link HeartbeatConfiguration}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class HeartbeatEvent extends AbstractServiceInstanceEvent {

    private final HealthStatus status;

    /**
     * @param source The service instance
     * @param status The status of health indicator
     */
    public HeartbeatEvent(ServiceInstance source, HealthStatus status) {
        super(source);
        this.status = status;
    }

    /**
     * This method will return the {@link HealthStatus} if the server is configured to calculate the status.
     *
     * @return The current health status
     */
    public HealthStatus getStatus() {
        return status;
    }
}
