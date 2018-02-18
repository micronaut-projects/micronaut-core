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

import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.particleframework.context.annotation.*;
import org.particleframework.context.exceptions.ConfigurationException;
import org.particleframework.core.util.ArrayUtils;
import org.particleframework.core.util.CollectionUtils;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Allows connecting to a Redis cluster via the the {@code "redis.uris"} setting
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Requires(property = RedisSetting.REDIS_URIS)
@Singleton
@Factory
public class DefaultRedisClusterClientFactory {

    @Bean(preDestroy = "shutdown")
    @Singleton
    @Primary
    public RedisClusterClient redisClient(@Primary AbstractRedisConfiguration config) {
        List<RedisURI> uris = config.getUris();
        if(CollectionUtils.isEmpty(uris)) {
            throw new ConfigurationException("Redis URIs must be specified");
        }
        return RedisClusterClient.create(uris);
    }

    @Bean(preDestroy = "close")
    @Singleton
    @Primary
    public StatefulRedisClusterConnection<String, String> redisConnection(@Primary RedisClusterClient redisClient) {
        return redisClient.connect();
    }

    @Bean(preDestroy = "close")
    @Singleton
    public StatefulRedisPubSubConnection<String, String> redisPubSubConnection(@Primary RedisClusterClient redisClient) {
        return redisClient.connectPubSub();
    }

}
