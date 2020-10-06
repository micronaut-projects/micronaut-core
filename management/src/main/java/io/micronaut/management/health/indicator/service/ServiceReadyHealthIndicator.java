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
package io.micronaut.management.health.indicator.service;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.Ordered;
import io.micronaut.discovery.event.ServiceReadyEvent;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import io.micronaut.management.health.indicator.annotation.Readiness;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;

/**
 * <p>A {@link io.micronaut.management.health.indicator.HealthIndicator} that signals when the service is ready to
 * service requests.</>
 *
 * @author Pavol Gressa
 * @since 2.1
 */
@Singleton
@Requires(beans = HealthEndpoint.class)
@Readiness
public class ServiceReadyHealthIndicator implements HealthIndicator {

    private static final String NAME = "service";
    private final boolean isService;

    private boolean serviceReady = false;

    /**
     * Default constructor.
     * @param applicationConfiguration The application configuration.
     */
    @Internal
    protected ServiceReadyHealthIndicator(ApplicationConfiguration applicationConfiguration) {
        this.isService = applicationConfiguration.getName().isPresent();
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public Publisher<HealthResult> getResult() {
        HealthResult.Builder builder = HealthResult.builder(NAME);
        if (serviceReady) {
            builder.status(HealthStatus.UP);
        } else {
            builder.status(HealthStatus.DOWN);
        }
        return Flowable.just(builder.build());
    }

    @EventListener
    void onServiceStarted(ServiceReadyEvent event) {
        serviceReady = true;
    }

    @EventListener
    void onServerStarted(ServerStartupEvent event) {
        if (!isService) {
            serviceReady = true;
        }
    }
}
