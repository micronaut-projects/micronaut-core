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
package io.micronaut.discovery.consul.health;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.consul.ConsulConfiguration;
import io.micronaut.discovery.consul.client.v1.ConsulClient;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.util.Collections;

/**
 * A {@link HealthIndicator} for Consul.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Requires(classes = HealthIndicator.class)
@Requires(beans = ConsulClient.class)
@Requires(property = ConsulConfiguration.PREFIX + ".health-check", value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
public class ConsulHealthIndicator implements HealthIndicator {

    private final ConsulClient client;

    /**
     * @param client The Consul client
     */
    public ConsulHealthIndicator(ConsulClient client) {
        this.client = client;
    }

    @Override
    public Publisher<HealthResult> getResult() {
        Flowable<String> statusFlowable = Flowable.fromPublisher(client.status());

        return statusFlowable.map(s -> {
            HealthResult.Builder builder = HealthResult.builder(ConsulClient.SERVICE_ID, HealthStatus.UP);
            builder.details(Collections.singletonMap("leader", s));
            return builder.build();
        }).onErrorReturn(throwable -> {
            HealthResult.Builder builder = HealthResult.builder(ConsulClient.SERVICE_ID, HealthStatus.DOWN);
            builder.exception(throwable);
            return builder.build();
        });
    }
}
