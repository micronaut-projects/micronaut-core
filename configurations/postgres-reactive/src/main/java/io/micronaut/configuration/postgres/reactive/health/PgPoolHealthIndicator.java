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
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import io.reactiverse.pgclient.Row;
import io.reactiverse.reactivex.pgclient.PgPool;
import io.reactiverse.reactivex.pgclient.PgRowSet;
import io.reactivex.Single;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;

/**
 * A  {@link HealthIndicator} for reactive Postgres client.
 *
 * @author puneetbehl
 * @since 1.0
 */
@Requires(classes = HealthIndicator.class)
@Requires(property = HealthEndpoint.PREFIX + ".postgres.reactive.enabled", notEquals = "false")
@Singleton
public class PgPoolHealthIndicator implements HealthIndicator {

    public static final String NAME = "pgPool";
    public static final String QUERY = "SELECT version();";
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
        Single<PgRowSet> single = client.rxQuery(QUERY);
        Single<HealthResult> healthResultSingle = single.map(pgRowSet -> {
            HealthResult.Builder status = HealthResult.builder(NAME, HealthStatus.UP);
            String details = String.join(", ", pgRowSet.columnsNames());
            for (Row row : pgRowSet.getDelegate()) {
                details = details.concat("\n" + row.getString("db") + ", " + row.getString("size"));
            }
            status.details(details);
            return status.build();
        }).onErrorReturn(this::buildErrorResult);
        return healthResultSingle.toFlowable();
    }

    private HealthResult buildErrorResult(Throwable throwable) {
        return HealthResult.builder(NAME, HealthStatus.DOWN).exception(throwable).build();
    }
}
