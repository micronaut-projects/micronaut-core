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
import org.particleframework.context.annotation.Factory;
import org.particleframework.context.annotation.Primary;
import org.particleframework.context.annotation.Requires;

import javax.inject.Singleton;

/**
 * Factory for the default {@link RedisClient}. Creates the injectable {@link Primary} bean
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Requires(property = RedisSetting.PREFIX)
@Requires(missingProperty = RedisSetting.REDIS_URIS)
@Singleton
@Factory
public class DefaultRedisClientFactory {

    @Bean(preDestroy = "shutdown")
    @Singleton
    @Primary
    public RedisClient redisClient(@Primary RedisURI redisURI) {
        return RedisClient.create(redisURI);
    }

    @Bean(preDestroy = "close")
    @Singleton
    @Primary
    public StatefulRedisConnection<String, String> redisConnection(@Primary RedisClient redisClient) {
        return redisClient.connect();
    }

    @Bean(preDestroy = "close")
    @Singleton
    public StatefulRedisPubSubConnection<String, String> redisPubSubConnection(@Primary RedisClient redisClient) {
        return redisClient.connectPubSub();
    }

}
