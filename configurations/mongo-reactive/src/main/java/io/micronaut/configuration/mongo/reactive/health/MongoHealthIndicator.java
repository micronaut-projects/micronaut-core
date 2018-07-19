/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.configuration.mongo.reactive.health;

import com.mongodb.reactivestreams.client.MongoClient;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.aggregator.HealthAggregator;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * A {@link HealthIndicator} for MongoDB.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Requires(beans = MongoClient.class)
public class MongoHealthIndicator implements HealthIndicator {
    /**
     * The name to expose details with.
     */
    public static final String NAME = "mongodb";
    private final BeanContext beanContext;
    private final HealthAggregator<?> healthAggregator;
    // needed to initialize beans, do not remove
    private final MongoClient[] mongoClients;

    /**
     * Constructor.
     *
     * @param beanContext beanContext
     * @param healthAggregator healthAggregator
     * @param mongoClients The mongo clients
     */
    public MongoHealthIndicator(BeanContext beanContext, HealthAggregator<?> healthAggregator, MongoClient... mongoClients) {
        this.beanContext = beanContext;
        this.healthAggregator = healthAggregator;
        this.mongoClients = mongoClients;
    }

    @Override
    public Publisher<HealthResult> getResult() {
        List<BeanRegistration<MongoClient>> registrations = new ArrayList();
        for (MongoClient mongoClient : mongoClients) {
            Optional<BeanRegistration<MongoClient>> registration = beanContext.findBeanRegistration(mongoClient);
            registration.ifPresent(registrations::add);
        }
        Flowable<BeanRegistration<MongoClient>> mongoClients = Flowable.fromIterable(registrations);
        Flowable<HealthResult> healthResultFlowable = mongoClients.flatMap(registration -> {
            MongoClient mongoClient = registration.getBean();
            String name = "mongodb (" + registration.getIdentifier().getName() + ")";
            Flowable<String> databaseNameFlowable = Flowable.fromPublisher(mongoClient.listDatabaseNames())
                                .timeout(10, TimeUnit.SECONDS)
                                .retry(3);
            return databaseNameFlowable
                .toList()
                .map(strings -> {
                    HealthResult.Builder builder = HealthResult.builder(name);
                    builder.status(HealthStatus.UP);
                    builder.details(Collections.singletonMap(
                        "databases", strings
                    ));
                    return builder.build();
                }).onErrorReturn(throwable -> {
                    HealthResult.Builder builder = HealthResult.builder(name);
                    builder.status(HealthStatus.DOWN);
                    builder.exception(throwable);
                    return builder.build();

                }).toFlowable();
        }).onErrorReturn(throwable -> {
            HealthResult.Builder builder = HealthResult.builder(NAME);
            builder.status(HealthStatus.DOWN);
            builder.exception(throwable);
            return builder.build();

        });

        return this.healthAggregator.aggregate(
                NAME,
                healthResultFlowable
        );
    }
}
