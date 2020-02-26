/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.discovery.registration;

import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.event.AbstractServiceInstanceEvent;
import io.micronaut.discovery.event.ServiceStoppedEvent;
import io.micronaut.discovery.event.ServiceReadyEvent;
import io.micronaut.discovery.exceptions.DiscoveryException;
import io.micronaut.health.HealthStatus;
import io.micronaut.health.HeartbeatEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * A base class for classes that automatically register the server with discovery services.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class AutoRegistration implements ApplicationEventListener<AbstractServiceInstanceEvent> {

    protected static final Logger LOG = LoggerFactory.getLogger(AutoRegistration.class);
    private static final Pattern APPLICATION_NAME_PATTERN = Pattern.compile("^[a-zA-Z][\\w\\d-]*[a-zA-Z\\d]$");
    protected final AtomicBoolean registered = new AtomicBoolean(false);
    private final RegistrationConfiguration registrationConfiguration;

    /**
     * Initialize given configurations.
     *
     * @param registrationConfiguration Common configurations for registration
     */
    protected AutoRegistration(RegistrationConfiguration registrationConfiguration) {
        this.registrationConfiguration = registrationConfiguration;
    }

    @Override
    public void onApplicationEvent(AbstractServiceInstanceEvent event) {
        if (registrationConfiguration.isEnabled()) {
            if (event instanceof ServiceReadyEvent) {
                register(event.getSource());
            } else if (event instanceof ServiceStoppedEvent) {
                if (registrationConfiguration.isDeregister()) {
                    deregister(event.getSource());
                }
            } else if (event instanceof HeartbeatEvent) {
                HeartbeatEvent heartbeatEvent = (HeartbeatEvent) event;
                pulsate(event.getSource(), heartbeatEvent.getStatus());
            }
        }
    }

    /**
     * This method will be invoked each time a {@link HeartbeatEvent} occurs allowing the implementation to perform any necessary callbacks to the service discovery server.
     *
     * @param instance The instance
     * @param status   The {@link HealthStatus}
     */
    protected abstract void pulsate(ServiceInstance instance, HealthStatus status);

    /**
     * Deregister the {@link ServiceInstance} from service discovery services.
     *
     * @param instance The {@link ServiceInstance}
     */
    protected abstract void deregister(ServiceInstance instance);

    /**
     * Register the {@link ServiceInstance} with discovery services.
     *
     * @param instance The {@link ServiceInstance}
     */
    protected abstract void register(ServiceInstance instance);

    /**
     * Validate the given application name.
     *
     * @param name The application name
     */
    protected void validateApplicationName(String name) {
        String typeDescription = "Application name";
        validateName(name, typeDescription);
    }

    /**
     * Validate the given application name.
     *
     * @param name The application name
     * @param typeDescription The detailed information about name
     */
    protected void validateName(String name, String typeDescription) {
        if (!APPLICATION_NAME_PATTERN.matcher(name).matches()) {
            throw new DiscoveryException(typeDescription + " [" + name + "] must start with a letter, end with a letter or digit and contain only letters, digits or hyphens. Example: foo-bar");
        }
    }
}
