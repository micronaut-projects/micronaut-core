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
import org.particleframework.context.annotation.*;
import org.particleframework.core.io.socket.SocketUtils;
import redis.embedded.RedisServer;
import redis.embedded.RedisServerBuilder;

import javax.inject.Singleton;
import java.util.List;

/**
 * An bean for an embedded Redis server
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Requires(classes = RedisServer.class)
@Requires(property = "particle.redis.type", value = "embedded")
@Factory
public class EmbeddedRedisServer {

    @Bean(preDestroy = "stop")
    @Context
    public RedisServer redisServer(Configuration configuration) {
        RedisServer server = configuration.builder.build();
        server.start();
        return server;
    }

    @Bean
    @Primary
    @Singleton
    public RedisURI redisURI(RedisServer redisServer) {
        List<Integer> ports = redisServer.ports();
        if(ports.isEmpty()) {
            throw new IllegalStateException("No configured Redis ports");
        }
        return RedisURI.create("localhost" , ports.get(0));
    }


    @ConfigurationProperties("particle.redis.embedded")
    @Requires(classes = RedisServerBuilder.class )
    public static class Configuration {
        @ConfigurationBuilder(
                prefixes = ""
        )
        RedisServerBuilder builder = new RedisServerBuilder().port(SocketUtils.findAvailableTcpPort());
    }
}
