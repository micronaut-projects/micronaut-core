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
package org.particleframework.health;

import org.particleframework.context.annotation.Requires;
import org.particleframework.context.event.ApplicationEventListener;
import org.particleframework.context.event.ApplicationEventPublisher;
import org.particleframework.context.exceptions.ConfigurationException;
import org.particleframework.discovery.ServiceInstance;
import org.particleframework.discovery.event.AbstractServiceInstanceEvent;
import org.particleframework.discovery.event.ServiceDegistrationEvent;
import org.particleframework.discovery.event.ServiceRegistrationEvent;
import org.particleframework.runtime.executor.ScheduledExecutorServiceConfig;
import org.particleframework.runtime.server.EmbeddedServer;
import org.particleframework.runtime.server.EmbeddedServerInstance;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * This class schedules the application {@link HeartbeatEvent} to be triggered at the interval specified by {@link HeartbeatConfiguration#getInterval()}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Requires(beans = EmbeddedServer.class)
public class HeartbeatScheduler implements ApplicationEventListener<AbstractServiceInstanceEvent> {


    private final ScheduledExecutorService executorService;
    private final HeartbeatConfiguration heartbeatConfiguration;
    private final ApplicationEventPublisher eventPublisher;
    private ScheduledFuture<?> scheduledFixture;

    public HeartbeatScheduler(
            HeartbeatConfiguration heartbeatConfiguration,
            ApplicationEventPublisher publisher,
            @Named(ScheduledExecutorServiceConfig.NAME) ExecutorService executorService) {
        if(!(executorService instanceof ScheduledExecutorService)) {
            throw new ConfigurationException("Configured executor service named ["+ScheduledExecutorServiceConfig.NAME+"] must be an instance of " + ScheduledExecutorService.class.getName());
        }
        this.executorService = (ScheduledExecutorService) executorService;
        this.heartbeatConfiguration = heartbeatConfiguration;
        this.eventPublisher = publisher;
    }

    @Override
    public void onApplicationEvent(AbstractServiceInstanceEvent event) {
        if(heartbeatConfiguration.isEnabled()) {

            if(event instanceof ServiceRegistrationEvent) {
                ServiceInstance source = event.getSource();
                if(source instanceof EmbeddedServerInstance) {
                    long interval = heartbeatConfiguration.getInterval().toMillis();
                    this.scheduledFixture = executorService.scheduleAtFixedRate(
                            ()-> eventPublisher.publishEvent(new HeartbeatEvent(source)),
                            interval,
                            interval,
                            TimeUnit.MILLISECONDS
                    );
                }
            }
            else if(event instanceof ServiceDegistrationEvent && this.scheduledFixture != null) {
                ServiceInstance source = event.getSource();
                if(source instanceof EmbeddedServerInstance && !this.scheduledFixture.isCancelled()) {
                    this.scheduledFixture.cancel(false);
                }
            }
        }
    }
}
