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

package io.micronaut.configuration.lettuce.health;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.aggregator.HealthAggregator;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.inject.Singleton;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;

/**
 * A Health Indicator for Redis.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Requires(classes = HealthIndicator.class)
public class RedisHealthIndicator implements HealthIndicator {
    /**
     * Default name to use for health indication for Redis.
     */
    public static final String NAME = "redis";

    private static final int TIMEOUT_SECONDS = 3;
    private static final int RETRY = 3;

    private final BeanContext beanContext;
    private final HealthAggregator<?> healthAggregator;
    private final StatefulRedisConnection[] connections;

    /**
     * Constructor.
     *
     * @param beanContext      beanContext
     * @param healthAggregator healthAggregator
     * @param connections      connections
     */
    public RedisHealthIndicator(BeanContext beanContext, HealthAggregator<?> healthAggregator, StatefulRedisConnection... connections) {
        this.beanContext = beanContext;
        this.healthAggregator = healthAggregator;
        this.connections = connections;
    }

    @Override
    public Publisher<HealthResult> getResult() {
        Collection<BeanRegistration<RedisClient>> registrations = beanContext.getActiveBeanRegistrations(RedisClient.class);
        Flux<BeanRegistration<RedisClient>> redisClients = Flux.fromIterable(registrations);

        Flux<HealthResult> healthResultFlux = redisClients.flatMap(client -> {
            StatefulRedisConnection<String, String> connection;
            String connectionName = client.getIdentifier().getName();
            String dbName = "redis(" + connectionName + ")";
            try {
                connection = client.getBean().connect();
            } catch (Exception e) {
                HealthResult result = HealthResult
                        .builder(dbName, HealthStatus.DOWN)
                        .exception(e)
                        .build();
                return Flux.just(result);
            }

            Mono<String> pingCommand = connection.reactive().ping();
            pingCommand = pingCommand.timeout(Duration.ofSeconds(TIMEOUT_SECONDS)).retry(RETRY);
            return pingCommand.map(s -> {
                try {
                    if (s.equalsIgnoreCase("pong")) {
                        return HealthResult
                                .builder(dbName, HealthStatus.UP)
                                .build();
                    }
                    return HealthResult
                            .builder(dbName, HealthStatus.DOWN)
                            .details(Collections.singletonMap("message", "Unexpected response: " + s))
                            .build();
                } finally {
                    try {
                        connection.close();
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }).onErrorResume(throwable -> {
                        try {
                            return Mono.just(HealthResult
                                    .builder(dbName, HealthStatus.DOWN)
                                    .exception(throwable)
                                    .build()
                            );
                        } finally {
                            try {
                                connection.close();
                            } catch (Exception e) {
                                // ignore
                            }
                        }
                    }
            );
        });

        return this.healthAggregator.aggregate(
                NAME,
                healthResultFlux
        );
    }
}
