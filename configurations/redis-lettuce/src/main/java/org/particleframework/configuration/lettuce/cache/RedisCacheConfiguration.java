/*
 * Copyright 2017 original authors
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
package org.particleframework.configuration.lettuce.cache;

import io.lettuce.core.RedisURI;
import org.particleframework.cache.CacheConfiguration;
import org.particleframework.context.annotation.Argument;
import org.particleframework.context.annotation.ForEach;
import org.particleframework.core.serialize.JdkSerializer;
import org.particleframework.core.serialize.ObjectSerializer;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Allows configuration of caches stored in Redis
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ForEach(property = "particle.redis.caches")
public class RedisCacheConfiguration extends CacheConfiguration {

    protected String uri;

    protected String server;

    protected Class<? extends ObjectSerializer> keySerializer;

    protected Class<? extends ObjectSerializer> valueSerializer = JdkSerializer.class;

    public RedisCacheConfiguration(@Argument String cacheName) {
        super(cacheName);
    }

    /**
     * @return The optional uri of the cache
     */
    public Optional<RedisURI> getRedisURI() {
        if(uri != null) {
            return Optional.of(RedisURI.create(uri));
        }
        else {
            return Optional.empty();
        }
    }

    /**
     * @return The name of the server to use
     */
    public Optional<String> getServer() {
        if(server != null) {
            return Optional.of(server);
        }
        else {
            return Optional.empty();
        }
    }

    /**
     * @return The {@link ObjectSerializer} type to use for serializing values
     */
    public Optional<Class<? extends ObjectSerializer>> getValueSerializer() {
        return Optional.ofNullable(valueSerializer);
    }

    public Optional<Class<? extends ObjectSerializer>> getKeySerializer() {
        return Optional.ofNullable(keySerializer);
    }
}
