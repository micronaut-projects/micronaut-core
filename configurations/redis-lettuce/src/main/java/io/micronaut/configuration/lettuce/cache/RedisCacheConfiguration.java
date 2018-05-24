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

package io.micronaut.configuration.lettuce.cache;

import io.micronaut.cache.CacheConfiguration;
import io.micronaut.configuration.lettuce.RedisSetting;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.serialize.ObjectSerializer;
import io.micronaut.runtime.ApplicationConfiguration;

import java.util.Optional;

/**
 * Allows configuration of caches stored in Redis.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@EachProperty(RedisSetting.REDIS_CACHES)
public class RedisCacheConfiguration extends CacheConfiguration {

    protected String server;
    protected Class<ObjectSerializer> keySerializer;
    protected Class<ObjectSerializer> valueSerializer;

    /**
     * Constructor.
     * @param cacheName cacheName
     * @param applicationConfiguration applicationConfiguration
     */
    public RedisCacheConfiguration(@Parameter String cacheName, ApplicationConfiguration applicationConfiguration) {
        super(cacheName, applicationConfiguration);
    }

    /**
     * @see io.micronaut.configuration.lettuce.NamedRedisServersConfiguration
     * @return The name of the server to use.
     */
    public Optional<String> getServer() {
        if (server != null) {
            return Optional.of(server);
        } else {
            return Optional.empty();
        }
    }

    /**
     * @return The {@link ObjectSerializer} type to use for serializing values.
     */
    public Optional<Class<ObjectSerializer>> getValueSerializer() {
        return Optional.ofNullable(valueSerializer);
    }

    /**
     * The {@link ObjectSerializer} to use for serializing keys. Defaults to {@link io.micronaut.cache.serialize.DefaultStringKeySerializer}.
     *
     * @return The optional {@link ObjectSerializer} class
     */
    public Optional<Class<ObjectSerializer>> getKeySerializer() {
        return Optional.ofNullable(keySerializer);
    }

}
