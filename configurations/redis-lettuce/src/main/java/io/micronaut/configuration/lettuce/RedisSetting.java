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

/**
 * Interface for common Redis settings.
 *
 * @author Graeme Rocher
 */
public interface RedisSetting {
    /**
     * Prefix used for all settings.
     */
    String PREFIX = "redis";
    /**
     * The URI to the Redis server.
     */
    String REDIS_URI = PREFIX + ".uri";
    /**
     * The URIs to the Redis servers for cluster config.
     */
    String REDIS_URIS = PREFIX + ".uris";
    /**
     * The named redis servers.
     */
    String REDIS_SERVERS = PREFIX + ".servers";
    /**
     * Embedded Redis configuration.
     */
    String REDIS_EMBEDDED = PREFIX + ".embedded";
    /**
     * Configured Redis caches.
     */
    String REDIS_CACHES = PREFIX + ".caches";
}
