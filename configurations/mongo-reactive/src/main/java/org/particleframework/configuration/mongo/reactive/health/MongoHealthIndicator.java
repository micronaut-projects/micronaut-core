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
package org.particleframework.configuration.mongo.reactive.health;

import com.mongodb.reactivestreams.client.MongoClient;
import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import org.particleframework.context.annotation.Requires;
import org.particleframework.health.HealthStatus;
import org.particleframework.management.health.indicator.HealthIndicator;
import org.particleframework.management.health.indicator.HealthResult;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.util.Collections;

/**
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Requires(beans = MongoClient.class)
class MongoHealthIndicator implements HealthIndicator {

    private final MongoClient[] mongoClients;

    public MongoHealthIndicator(MongoClient[] mongoClients) {
        this.mongoClients = mongoClients;
    }

    @Override
    public Flowable<HealthResult> getResult() {
        Flowable<MongoClient> mongoClients = Flowable.fromArray(this.mongoClients);
        return mongoClients.flatMap((Function<MongoClient, Publisher<HealthResult>>) mongoClient -> {
            String name = "mongodb (" + mongoClient.getSettings().getApplicationName() + ")";
            return Flowable.fromPublisher(mongoClient.listDatabaseNames())
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
        });
    }
}
