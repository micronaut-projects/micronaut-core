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

package io.micronaut.configuration.postgres.reactive;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.util.StringUtils;
import io.reactiverse.reactivex.pgclient.PgClient;
import io.reactiverse.reactivex.pgclient.PgPool;
import io.vertx.reactivex.core.Vertx;

import javax.annotation.Nullable;
import javax.inject.Singleton;

/**
 * The Factory for creating Reactive Postgres client.
 *
 * @author puneetbehl
 * @since 1.0
 */
@Factory
public class PgPoolClientFactory {

    private final PgPoolConfiguration pgPoolConfiguration;

    /**
     * The Vertx instance if you are running with Vert.x.
     */
    private final Vertx vertx;

    /**
     * Create the factory with given Posgres Pool configuration and
     * Vertx instance(can be null) if you are running with Vert.x.
     *
     * @param pgPoolConfiguration The Reactive Postgres configurations
     * @param vertx The Vertx instance
     */
    public PgPoolClientFactory(PgPoolConfiguration pgPoolConfiguration, @Nullable Vertx vertx) {
        this.pgPoolConfiguration = pgPoolConfiguration;
        this.vertx = vertx;
    }

    /**
     * @return client A pool of connections.
     */
    @Singleton
    @Bean(preDestroy = "close")
    public PgPool client() {
        if (this.vertx == null) {
            return createClient();
        } else {
            return createClient(vertx);
        }
    }

    /**
     * Create a connection pool to the database configured with the {@link PgPoolConfiguration}.
     *
     * @return A pool of connections.
     */
    private PgPool createClient() {
        PgPoolConfiguration configuration = this.pgPoolConfiguration;

        String connectionUri = configuration.getUri();
        if (StringUtils.isNotEmpty(connectionUri)) {
            return PgClient.pool(connectionUri);
        } else {
            return PgClient.pool(configuration.pgPoolOptions);
        }
    }

    /**
     * Create a connection pool to the database configured with the {@link PgPoolConfiguration}.
     *
     * @param vertx The Vertx instance.
     * @return A pool of connections.
     */
    private PgPool createClient(Vertx vertx) {
        PgPoolConfiguration configuration = this.pgPoolConfiguration;

        String connectionUri = configuration.getUri();
        if (StringUtils.isNotEmpty(connectionUri)) {
            return PgClient.pool(vertx, connectionUri);
        } else {
            return PgClient.pool(vertx, configuration.pgPoolOptions);
        }
    }
}
