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
package org.particleframework.configuration.lettuce.session;

import io.lettuce.core.RedisFuture;
import io.lettuce.core.dynamic.Commands;
import io.lettuce.core.dynamic.annotation.Command;
import io.lettuce.core.dynamic.annotation.Param;

import java.util.Map;

/**
 *
 * Commands for storing and retrieving {@link org.particleframework.session.Session} instances
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface RedisSessionCommands extends Commands {


    /**
     * Set multiple hash fields to multiple values.
     *
     * @param sessionId the key
     * @param attributes the null
     * @return String simple-string-reply
     */
    @Command("HMSET :sessionId :value")
    RedisFuture<String> hmset(@Param("sessionId") String sessionId, @Param("value") Map<String, byte[]> attributes);

    /**
     * Set a single attribute of a session
     *
     * @param sessionId The session ID
     * @param attribute The attribute
     * @param value The value
     * @return String simple-string-reply
     */
    @Command("HSET :sessionId :value")
    RedisFuture<String> hset(@Param("sessionId") String sessionId, String attribute, byte[] value );

    /**
     * Get all the fields and values in a hash.
     *
     * @param sessionId the key
     * @return Map&lt;K,V&gt; array-reply list of fields and their values stored in the hash, or an empty list when {@code key}
     *         does not exist.
     */
    @Command("HGETALL :key")
    RedisFuture<Map<String, byte[]>> hgetall(@Param("key") String sessionId);
}
