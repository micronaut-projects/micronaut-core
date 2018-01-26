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
import org.particleframework.runtime.server.EmbeddedServer;
import org.particleframework.runtime.server.event.AbstractServerApplicationEvent;
import org.particleframework.runtime.server.event.ServerShutdownEvent;
import org.particleframework.runtime.server.event.ServerStartupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A base class for classes that automatically register the server with discovery services
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class AutoRegistration implements ApplicationEventListener<AbstractServerApplicationEvent> {

    protected static final Logger LOG = LoggerFactory.getLogger(AutoRegistration.class);

    @Override
    public void onApplicationEvent(AbstractServerApplicationEvent event) {
        if(event instanceof ServerStartupEvent) {
            register(event.getSource());
        }
        else if(event instanceof ServerShutdownEvent) {
            deregister(event.getSource());
        }
    }

    /**
     * Deregister the server from service discovery services
     * @param server The embedded server
     */
    protected abstract void deregister(EmbeddedServer server);

    /**
     * Register the server with discovery services
     *
     * @param server The server to register
     */
    protected abstract void register(EmbeddedServer server);
}
