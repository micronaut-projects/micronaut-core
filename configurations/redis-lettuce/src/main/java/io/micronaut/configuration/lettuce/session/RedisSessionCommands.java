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

package io.micronaut.configuration.lettuce.session;

import io.lettuce.core.Range;
import io.lettuce.core.dynamic.Commands;
import io.lettuce.core.dynamic.annotation.Command;
import io.lettuce.core.dynamic.annotation.Param;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Commands for storing and retrieving {@link io.micronaut.session.Session} instances.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface RedisSessionCommands extends Commands {

    /**
     * Set multiple hash fields to multiple values.
     *
     * @param sessionId  The session ID
     * @param attributes The attributes
     * @return String simple-string-reply
     */
    @Command("HMSET :sessionId :value")
    CompletableFuture<Void> saveSessionData(@Param("sessionId") byte[] sessionId, @Param("value") Map<byte[], byte[]> attributes);

    /**
     * Set a single attribute of a session.
     *
     * @param sessionId The session ID
     * @param attribute The attribute
     * @param value     The value
     * @return String simple-string-reply
     */
    @Command("HSET :sessionId :attribute :value")
    CompletableFuture<Void> setAttribute(@Param("sessionId") byte[] sessionId, @Param("attribute") byte[] attribute, @Param("value") byte[] value);

    /**
     * Removes a single attribute of a session.
     *
     * @param sessionId  The session ID
     * @param attributes The attributes to delete
     * @return String simple-string-reply
     */
    @Command("HDEL :sessionId :attributes")
    CompletableFuture<Void> deleteAttributes(@Param("sessionId") byte[] sessionId, @Param("attributes") byte[]... attributes);

    /**
     * Get all the fields and values in a hash.
     *
     * @param sessionId The session ID
     * @return Map&lt;K,V&gt; array-reply list of fields and their values stored in the hash, or an empty list when {@code key}
     * does not exist.
     */
    @Command("HGETALL")
    CompletableFuture<Map<byte[], byte[]>> findSessionData(byte[] sessionId);

    /**
     * Save an expiry.
     *
     * @param expiryKey The expiry key
     * @param seconds   The seconds until expiration
     * @return A future
     */
    @Command("SET :expiryKey :seconds EX :seconds")
    CompletableFuture<Void> saveExpiry(@Param("expiryKey") byte[] expiryKey, @Param("seconds") byte[] seconds);

    /**
     * Delete a key.
     *
     * @param key The key to delete
     * @return the future
     */
    CompletableFuture<Void> del(byte[] key);

    /**
     * Remove an item from the given sorted set.
     *
     * @param key    The key of the set
     * @param member The member to remove
     * @return the future
     */
    CompletableFuture<Void> zrem(byte[] key, byte[] member);

    /**
     * Touch one or more keys. Touch sets the last accessed time for a key. Non-exsitent keys wont get created.
     *
     * @param key The key to get
     * @return Long integer-reply the number of found keys.
     */
    CompletableFuture<byte[]> get(@Param("keys") byte[] key);

    /**
     * Return a range of members in a sorted set, by score.
     *
     * @param key   The key
     * @param range The range
     * @return List&lt;V&gt; array-reply list of elements in the specified score range.
     * @since 4.3
     */
    CompletableFuture<List<byte[]>> zrangebyscore(byte[] key, Range<? extends Number> range);

    /**
     * Add one or more members to a sorted set, or update its score if it already exists.
     *
     * @param key    The key
     * @param score  The score
     * @param member The member
     * @return Long integer-reply specifically:
     * <p>
     * The number of elements added to the sorted sets, not including elements already existing for which the score was
     * updated.
     */
    CompletableFuture<Long> zadd(byte[] key, double score, byte[] member);

    /**
     * Set a key's time to live in seconds.
     *
     * @param key     The key
     * @param seconds The seconds type: long
     * @return Boolean integer-reply specifically:
     * <p>
     * {@literal true} if the timeout was set. {@literal false} if {@code key} does not exist or the timeout could not
     * be set.
     */
    CompletableFuture<Boolean> expire(byte[] key, long seconds);

    /**
     * Post a message to a channel.
     *
     * @param channel The channel type: key
     * @param message The message type: value
     * @return Long integer-reply the number of clients that received the message.
     */
    CompletableFuture<Long> publish(byte[] channel, byte[] message);

    /**
     * Set a configuration parameter to the given value.
     *
     * @param parameter The parameter name
     * @param value     The parameter value
     * @return String simple-string-reply: {@code OK} when the configuration was set properly. Otherwise an error is returned.
     */
    String configSet(String parameter, String value);
}
