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
 * @author puneetbehl
 * @since 1.0
 */
@Factory
public class PgPoolClientFactory {

    private final PgPoolConfiguration pgPoolConfiguration;
    private final Vertx vertx;

    /**
     * @param pgPoolConfiguration
     * @param vertx
     */
    public PgPoolClientFactory(PgPoolConfiguration pgPoolConfiguration, @Nullable Vertx vertx) {
        this.pgPoolConfiguration = pgPoolConfiguration;
        this.vertx = vertx;
    }

    /**
     * @return client
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
     * @return
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
     * @param vertx The Vertx instance.
     * @return
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
