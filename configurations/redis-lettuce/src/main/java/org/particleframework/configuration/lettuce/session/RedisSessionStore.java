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

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.dynamic.RedisCommandFactory;
import org.particleframework.context.annotation.Primary;
import org.particleframework.context.annotation.Replaces;
import org.particleframework.context.annotation.Requires;
import org.particleframework.session.InMemorySessionStore;
import org.particleframework.session.SessionStore;

import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Primary
@Requires(beans = StatefulRedisConnection.class)
@Replaces(InMemorySessionStore.class)
public class RedisSessionStore implements SessionStore<RedisSession> {

    private final StatefulRedisConnection<String, String> statefulRedisConnection;
    private final RedisSessionCommands sessionCommands;
    private final RedisHttpSessionConfiguration sessionConfiguration;

    public RedisSessionStore(RedisHttpSessionConfiguration sessionConfiguration,
                             StatefulRedisConnection<String, String> statefulRedisConnection) {
        this.statefulRedisConnection = statefulRedisConnection;
        this.sessionConfiguration = sessionConfiguration;
        RedisCommandFactory redisCommandFactory = new RedisCommandFactory(statefulRedisConnection);
        this.sessionCommands = redisCommandFactory.getCommands(RedisSessionCommands.class);

    }

    @Override
    public RedisSession newSession() {
        return null;
    }

    @Override
    public CompletableFuture<Optional<RedisSession>> findSession(String id) {
        return null;
    }

    @Override
    public CompletableFuture<Boolean> deleteSession(String id) {
        return null;
    }

    @Override
    public CompletableFuture<RedisSession> save(RedisSession session) {
        return null;
    }
}
