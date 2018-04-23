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

import io.lettuce.core.RedisURI;
import io.micronaut.context.env.Environment;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Abstract configuration for Lettuce.
 */
public abstract class AbstractRedisConfiguration extends RedisURI {

    private RedisURI uri;
    private List<RedisURI> uris = Collections.emptyList();

    /**
     * Constructor.
     */
    protected AbstractRedisConfiguration() {
        setClientName(Environment.DEFAULT_NAME);
        setPort(RedisURI.DEFAULT_REDIS_PORT);
        setHost("localhost"); // localhost by default
    }

    /**
     * @return Get the Redis URI for configuration.
     */
    public Optional<RedisURI> getUri() {
        if (uri != null) {
            uri.setClientName(getClientName());
        }
        return Optional.ofNullable(uri);
    }

    /**
     * Sets the Redis URI for configuration by string.
     *
     * @param uri The URI
     */
    public void setUri(URI uri) {
        this.uri = RedisURI.create(uri);
    }

    /**
     * @return Get the Redis URIs for cluster configuration.
     */
    public List<RedisURI> getUris() {
        return uris;
    }

    /**
     * Sets the Redis URIs for cluster configuration.
     *
     * @param uris The URI
     */
    public void setUris(URI... uris) {
        this.uris = Arrays.stream(uris).map(RedisURI::create).collect(Collectors.toList());
    }
}
