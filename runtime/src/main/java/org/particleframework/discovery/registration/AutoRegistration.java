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
package org.particleframework.discovery.registration;

import org.particleframework.context.event.ApplicationEventListener;
import org.particleframework.discovery.ServiceInstance;
import org.particleframework.discovery.event.AbstractServiceInstanceEvent;
import org.particleframework.discovery.event.ServiceDegistrationEvent;
import org.particleframework.discovery.event.ServiceRegistrationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A base class for classes that automatically register the server with discovery services
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class AutoRegistration implements ApplicationEventListener<AbstractServiceInstanceEvent> {

    protected static final Logger LOG = LoggerFactory.getLogger(AutoRegistration.class);

    @Override
    public void onApplicationEvent(AbstractServiceInstanceEvent event) {
        if(event instanceof ServiceRegistrationEvent) {
            register(event.getSource());
        }
        else if(event instanceof ServiceDegistrationEvent) {
            deregister(event.getSource());
        }
    }

    /**
     * Deregister the server from service discovery services
     * @param instance The embedded server
     */
    protected abstract void deregister(ServiceInstance instance);

    /**
     * Register the server with discovery services
     *
     * @param instance The server to register
     */
    protected abstract void register(ServiceInstance instance);
}
