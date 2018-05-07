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

package io.micronaut.configuration.neo4j.bolt.health;

import io.micronaut.context.annotation.Requires;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import org.neo4j.driver.v1.AccessMode;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResultCursor;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.util.Collections;
import java.util.concurrent.CompletionStage;

/**
 * A Health Indicator for Neo4j.
 *
 * @author graemerocher
 * @since 1.0
 */
@Requires(classes = HealthIndicator.class)
@Singleton
public class Neo4jHealthIndicator implements HealthIndicator {

    public static final String NAME = "neo4j";
    private final Driver boltDriver;

    /**
     * Constructor.
     * @param boltDriver driver
     */
    public Neo4jHealthIndicator(Driver boltDriver) {
        this.boltDriver = boltDriver;
    }

    @Override
    public Publisher<HealthResult> getResult() {
        try {
            Session session = boltDriver.session(AccessMode.READ);

            Single<HealthResult> healthResultSingle = Single.create(emitter -> {
                CompletionStage<StatementResultCursor> query =
                    session.runAsync("MATCH (n) RETURN COUNT(n) AS total");

                query.whenComplete((cursor, throwable) -> {
                    if (throwable != null) {
                        emitter.onSuccess(buildErrorResult(throwable));
                    } else {
                        CompletionStage<Record> record = cursor.nextAsync();
                        record.whenComplete((record1, throwable1) -> {
                            try {
                                if (throwable1 != null) {
                                    emitter.onSuccess(buildErrorResult(throwable1));
                                } else {
                                    HealthResult.Builder status = HealthResult.builder(NAME, HealthStatus.UP);
                                    status.details(Collections.singletonMap(
                                        "nodes", record1.get("total").asInt()
                                    ));
                                    emitter.onSuccess(status.build());
                                }
                            } catch (Throwable e) {
                                emitter.onSuccess(buildErrorResult(e));
                            } finally {
                                session.closeAsync();
                            }
                        });
                    }
                });
            });

            return healthResultSingle.toFlowable().subscribeOn(Schedulers.io());
        } catch (Throwable e) {
            return Flowable.just(buildErrorResult(e));
        }
    }

    private HealthResult buildErrorResult(Throwable throwable) {
        return HealthResult.builder(NAME, HealthStatus.DOWN).exception(throwable).build();
    }
}
