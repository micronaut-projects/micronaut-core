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

package io.micronaut.configuration.lettuce;

import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.util.Optional;

/**
 * Internal utility methods for configuration.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class RedisConnectionUtil {
    /**
     * Utility method for establishing a redis connection.
     *
     * @param beanLocator  The bean locator to use
     * @param serverName   The server name to use
     * @param errorMessage The error message to use if the connection can't be found
     * @return The connection
     * @throws ConfigurationException If the connection cannot be found
     */
    public static StatefulConnection findRedisConnection(
        BeanLocator beanLocator,
        Optional<String> serverName,
        String errorMessage) {

        return serverName.map(name -> beanLocator.findBean(StatefulRedisClusterConnection.class, Qualifiers.byName(name))
            .map(conn -> (StatefulConnection) conn)
            .orElse(
                beanLocator.findBean(StatefulRedisClusterConnection.class, Qualifiers.byName(name)).orElseThrow(() ->
                    new ConfigurationException(errorMessage)
                )
            )).orElseGet(() -> beanLocator.findBean(StatefulRedisConnection.class)
            .map(conn -> (StatefulConnection) conn)
            .orElse(
                beanLocator.findBean(StatefulRedisConnection.class).orElseThrow(() ->
                    new ConfigurationException("No Redis server configured to store sessions")
                )
            ));
    }
}
