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

package io.micronaut.configuration.postgres.reactive.health;

import io.micronaut.context.annotation.Requires;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import io.reactiverse.reactivex.pgclient.PgPool;
import io.reactiverse.reactivex.pgclient.PgRowSet;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;

/**
 * A Health indicator for reactive Postgres client.
 *
 * @author puneetbehl
 * @since 1.0
 */
@Requires(classes = HealthIndicator.class)
@Singleton
public class PgPoolHealthIndicator implements HealthIndicator {

    public static final String NAME = "pgPool";
    private final PgPool client;

    /**
     * Constructor.
     *
     * @param client A pool of connections.
     */
    public PgPoolHealthIndicator(PgPool client) {
        this.client = client;
    }

    @Override
    public Publisher<HealthResult> getResult() {
        try {
            Single<HealthResult> healthResultSingle = Single.create(emitter -> {
                String query = "select datname as db, pg_size_pretty(pg_database_size(datname)) as size from pg_database order by pg_database_size(datname) desc;";
                client.query(query, ar -> {
                    if (ar.succeeded()) {
                        HealthResult.Builder status = HealthResult.builder(NAME, HealthStatus.UP);
                        PgRowSet result = ar.result();
                        String details = String.join(", ", result.columnsNames());
                        for (io.reactiverse.pgclient.Row row : result.getDelegate()) {
                            details = details.concat("\n" + row.getString("db") + ", " + row.getString("size"));
                        }
                        status.details(details);
                        emitter.onSuccess(status.build());
                    } else {
                        emitter.onSuccess(buildErrorResult(ar.cause()));
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
