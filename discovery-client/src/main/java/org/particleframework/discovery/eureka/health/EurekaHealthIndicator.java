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
package org.particleframework.discovery.eureka.health;

import io.reactivex.Flowable;
import org.particleframework.context.annotation.Requires;
import org.particleframework.discovery.eureka.client.v2.EurekaClient;
import org.particleframework.health.HealthStatus;
import org.particleframework.management.health.indicator.HealthIndicator;
import org.particleframework.management.health.indicator.HealthResult;

import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;

/**
 * A {@link HealthIndicator} for Eureka
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Requires(beans = EurekaClient.class)
@Requires(classes = HealthIndicator.class)
public class EurekaHealthIndicator implements HealthIndicator {
    private final EurekaClient eurekaClient;

    public EurekaHealthIndicator(EurekaClient eurekaClient) {
        this.eurekaClient = eurekaClient;
    }

    @Override
    public Flowable<HealthResult> getResult() {
        Flowable<List<String>> serviceIds = Flowable.fromPublisher(eurekaClient.getServiceIds());
        return serviceIds.map(ids -> {
            HealthResult.Builder builder = HealthResult.builder(EurekaClient.SERVICE_ID, HealthStatus.UP);
            return builder.details(Collections.singletonMap("available-services", ids))
                    .build();
        }).onErrorReturn(throwable -> {
            HealthResult.Builder builder = HealthResult.builder(EurekaClient.SERVICE_ID, HealthStatus.DOWN);
            builder.exception(throwable);
            return builder.build();
        });
    }
}
