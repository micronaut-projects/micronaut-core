/*
 * Copyright 2018 original authors
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
package org.particleframework.discovery.eureka.registration;

import org.particleframework.discovery.ServiceInstance;
import org.particleframework.discovery.registration.AutoRegistration;
import org.particleframework.health.HealthStatus;

/**
 * @author graemerocher
 * @since 1.0
 */
public class EurekaAutoRegistration extends AutoRegistration {
    @Override
    protected void pulsate(ServiceInstance instance, HealthStatus status) {

    }

    @Override
    protected void deregister(ServiceInstance instance) {

    }

    @Override
    protected void register(ServiceInstance instance) {

    }
}
