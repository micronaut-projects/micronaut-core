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
package org.particleframework.configuration.lettuce;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.particleframework.context.annotation.Bean;
import org.particleframework.context.annotation.Primary;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * Abstract version of the a factory class for creating Redis clients
 */
public abstract class AbstractRedisClientFactory {
    public RedisClient redisClient(AbstractRedisConfiguration config) {
        Optional<RedisURI> uri = config.getUri();
        return uri.map(RedisClient::create)
                  .orElseGet(() -> RedisClient.create(config));
    }

    public StatefulRedisConnection<String, String> redisConnection(RedisClient redisClient) {
        return redisClient.connect();
    }

    public StatefulRedisPubSubConnection<String, String> redisPubSubConnection(RedisClient redisClient) {
        return redisClient.connectPubSub();
    }
}
