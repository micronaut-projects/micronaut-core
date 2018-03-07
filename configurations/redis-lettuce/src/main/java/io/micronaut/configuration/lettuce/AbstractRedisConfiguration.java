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
package io.micronaut.configuration.lettuce;

import io.lettuce.core.RedisURI;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Abstract configuration for Lettuce
 */
public abstract class AbstractRedisConfiguration extends RedisURI {

    private RedisURI uri;
    private List<RedisURI> uris = Collections.emptyList();

    protected AbstractRedisConfiguration() {
        setPort(RedisURI.DEFAULT_REDIS_PORT);
        setHost("localhost"); // localhost by default
    }

    /**
     * Sets the Redis URI for configuration by string
     * @param uri The URI
     */
    public void setUri(URI uri) {
        this.uri = RedisURI.create(uri);
    }

    /**
     * Sets the Redis URIs for cluster config
     * @param uris The URI
     */
    public void setUris(URI... uris) {
        this.uris = Arrays.stream(uris).map(RedisURI::create).collect(Collectors.toList());
    }

    public List<RedisURI> getUris() {
        return uris;
    }

    public Optional<RedisURI> getUri() {
        return Optional.ofNullable(uri);
    }
}
