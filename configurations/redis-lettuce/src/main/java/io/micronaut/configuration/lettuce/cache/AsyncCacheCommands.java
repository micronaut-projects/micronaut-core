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

import io.lettuce.core.RedisFuture;
import io.lettuce.core.dynamic.Commands;
import io.lettuce.core.dynamic.annotation.Command;
import io.lettuce.core.dynamic.annotation.Param;

import java.util.List;

/**
 * The asynchronous operations required by {@link RedisCache}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface AsyncCacheCommands extends Commands {

    /**
     * See https://redis.io/commands/get.
     *
     * @param key The key to retrieve
     * @return result of completion
     */
    @Command("GET :key")
    RedisFuture<byte[]> get(@Param("key") byte[] key);

    /**
     * See https://redis.io/commands/del.
     *
     * @param key The key to delete
     * @return result of completion
     */
    @Command("DEL :key")
    RedisFuture<Long> remove(@Param("key") byte[] key);

    /**
     * See https://redis.io/commands/set.
     *
     * @param key   The key
     * @param value The value
     * @return result of completion
     */
    @Command("SET :key :value")
    RedisFuture<Void> put(@Param("key") byte[] key, @Param("value") byte[] value);

    /**
     * See https://redis.io/commands/set.
     *
     * @param key     The key
     * @param value   The value
     * @param timeout The timeout
     * @return result of completion
     */
    @Command("SET :key :value EX :timeout")
    RedisFuture<Void> put(@Param("key") byte[] key, @Param("value") byte[] value, @Param("timeout") long timeout);

    /**
     * See https://redis.io/commands/expire.
     *
     * @param key     The key to expire
     * @param timeout The timeout
     * @return result of completion
     */
    @Command("EXPIRE :key :timeout")
    RedisFuture<Void> expire(@Param("key") byte[] key, @Param("timeout") long timeout);

    /**
     * Find all keys matching the given pattern.
     *
     * @param pattern the pattern type: patternkey (pattern)
     * @return List&lt;K&gt; array-reply list of keys matching {@code pattern}.
     */
    RedisFuture<List<byte[]>> keys(byte[] pattern);

    /**
     * Delete one or more keys.
     *
     * @param keys the keys
     * @return Long integer-reply The number of keys that were removed.
     */
    RedisFuture<Long> del(byte[]... keys);
}
