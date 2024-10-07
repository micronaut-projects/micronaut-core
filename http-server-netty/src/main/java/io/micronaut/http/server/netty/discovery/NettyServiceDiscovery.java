/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.server.netty.discovery;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.event.ServiceReadyEvent;
import io.micronaut.discovery.event.ServiceStoppedEvent;
import io.micronaut.http.server.netty.NettyEmbeddedServer;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.runtime.server.event.ServerShutdownEvent;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import jakarta.inject.Singleton;

@Singleton
@Internal
@Requires(classes = ServiceInstance.class)
@Order(Ordered.LOWEST_PRECEDENCE)
final class NettyServiceDiscovery {
    private final ApplicationEventPublisher<ServiceReadyEvent> serviceReadyEventApplicationEventPublisher;
    private final ApplicationEventPublisher<ServiceStoppedEvent> serviceStoppedEventApplicationEventPublisher;
    private NettyEmbeddedServerInstance createdInstance;

    NettyServiceDiscovery(ApplicationEventPublisher<ServiceReadyEvent> serviceReadyEventApplicationEventPublisher,
                          ApplicationEventPublisher<ServiceStoppedEvent> serviceStoppedEventApplicationEventPublisher) {
        this.serviceReadyEventApplicationEventPublisher = serviceReadyEventApplicationEventPublisher;
        this.serviceStoppedEventApplicationEventPublisher = serviceStoppedEventApplicationEventPublisher;
    }

    @EventListener
    void onStart(ServerStartupEvent event) {
        if (event.getSource() instanceof NettyEmbeddedServer nettyEmbeddedServer) {
            NettyEmbeddedServerInstance instance = getInstance(nettyEmbeddedServer);
            if (instance != null) {
                serviceReadyEventApplicationEventPublisher.publishEvent(new ServiceReadyEvent(instance));
            }
        }
    }

    @EventListener
    void onStop(ServerShutdownEvent event) {
        if (event.getSource() instanceof NettyEmbeddedServer nettyEmbeddedServer) {
            NettyEmbeddedServerInstance instance = getInstance(nettyEmbeddedServer);
            if (instance != null) {
                serviceStoppedEventApplicationEventPublisher.publishEvent(new ServiceStoppedEvent(instance));
            }
        }
    }

    @Nullable
    private NettyEmbeddedServerInstance getInstance(NettyEmbeddedServer nettyEmbeddedServer) {
        if (createdInstance == null) {
            ApplicationContext applicationContext = nettyEmbeddedServer.getApplicationContext();
            nettyEmbeddedServer.getApplicationConfiguration().getName()
                    .ifPresent(id -> this.createdInstance = applicationContext.createBean(NettyEmbeddedServerInstance.class, id, nettyEmbeddedServer));
        }
        return createdInstance;
    }
}
