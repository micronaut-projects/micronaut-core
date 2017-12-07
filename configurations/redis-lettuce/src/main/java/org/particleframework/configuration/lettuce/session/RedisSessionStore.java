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

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.dynamic.RedisCommandFactory;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.particleframework.context.BeanLocator;
import org.particleframework.context.annotation.Primary;
import org.particleframework.context.annotation.Replaces;
import org.particleframework.context.annotation.Requires;
import org.particleframework.context.event.ApplicationEventPublisher;
import org.particleframework.context.exceptions.ConfigurationException;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.serialize.JdkSerializer;
import org.particleframework.core.serialize.ObjectSerializer;
import org.particleframework.core.util.CollectionUtils;
import org.particleframework.session.InMemorySessionStore;
import org.particleframework.session.SessionIdGenerator;
import org.particleframework.session.SessionStore;
import org.particleframework.session.event.SessionCreatedEvent;
import org.particleframework.session.event.SessionDeletedEvent;
import org.particleframework.session.event.SessionExpiredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import javax.inject.Singleton;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * An implementation of the {@link SessionStore} interface for Redis
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Primary
@Requires(beans = StatefulRedisConnection.class)
@Requires(property = "particle.redis.session.enabled", value = "true")
@Replaces(InMemorySessionStore.class)
public class RedisSessionStore extends RedisPubSubAdapter<String, String> implements SessionStore<RedisSession>, Closeable {

    private static final Logger LOG  = LoggerFactory.getLogger(RedisSessionStore.class);
    private final RedisSessionCommands sessionCommands;
    private final RedisHttpSessionConfiguration sessionConfiguration;
    private final SessionIdGenerator sessionIdGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectSerializer valueSerializer;
    private final RedisClient redisClient;
    private final AtomicBoolean shutdownClient = new AtomicBoolean();
    private final StatefulRedisConnection<byte[], byte[]> redisConnection;
    private final Charset charset;
    private final StatefulRedisPubSubConnection<String, String> pubSubConnection;
    private final String expiryPrefix;

    public RedisSessionStore(
            SessionIdGenerator sessionIdGenerator,
            RedisHttpSessionConfiguration sessionConfiguration,
            BeanLocator beanLocator,
            ConversionService<?> conversionService,
            @Primary Optional<RedisClient> primaryClient,
            ApplicationEventPublisher eventPublisher) {
        this.sessionIdGenerator = sessionIdGenerator;
        this.valueSerializer = sessionConfiguration
                .getValueSerializer()
                .flatMap(beanLocator::findOrInstantiateBean)
                .orElse(new JdkSerializer(conversionService));
        this.eventPublisher = eventPublisher;
        this.sessionConfiguration = sessionConfiguration;
        this.charset = sessionConfiguration.getCharset();
        this.redisClient = sessionConfiguration.getRedisURI().map(uri -> {
            shutdownClient.set(true);
            return RedisClient.create(uri);
        }).orElse(primaryClient.orElseThrow(() ->
                new ConfigurationException("Neither a specific Redis URI or a primary redis connection configured to store sessions")
        ));
        this.redisConnection = redisClient.connect(new ByteArrayCodec());
        if(sessionConfiguration.isEnableKeyspaceEvents()) {

            try {
                String result = this.redisConnection.sync().configSet(
                        "notify-keyspace-events", "Egx"
                );
                if(!result.equalsIgnoreCase("ok")) {
                    if(LOG.isWarnEnabled()) {
                        LOG.warn("Failed to enable keyspace events on the Redis server. Manual configuration my be required");
                    }
                }
            } catch (Exception e) {
                if(LOG.isWarnEnabled()) {
                    LOG.warn("Failed to enable keyspace events on the Redis server. Manual configuration my be required", e);
                }
            }
        }
        this.expiryPrefix = sessionConfiguration.getKeyPrefix() + "expiry:";
        this.pubSubConnection = redisClient.connectPubSub();
        this.pubSubConnection.addListener(this);
        this.pubSubConnection.sync().psubscribe(
                "__keyevent@*:del",
                "__keyevent@*:expired"
        );
        RedisCommandFactory redisCommandFactory = new RedisCommandFactory(
                redisConnection
        );
        this.sessionCommands = redisCommandFactory.getCommands(RedisSessionCommands.class);

    }

    @Override
    public void message(String pattern, String channel, String message) {
        if(message.startsWith(expiryPrefix)) {
            if(pattern.endsWith(":del")) {
                String id = message.substring(expiryPrefix.length());
                findSessionInternal(id, true).whenComplete((optional, throwable) -> {
                    if(throwable == null && optional.isPresent()) {
                        RedisSession session = optional.get();
                        eventPublisher.publishEvent(new SessionDeletedEvent(session));
                    }
                });
            }
            else if(pattern.endsWith(":expired")) {
                String id = message.substring(expiryPrefix.length());
                findSessionInternal(id, true).whenComplete((optional, throwable) -> {
                    if(throwable == null && optional.isPresent()) {
                        RedisSession session = optional.get();
                        eventPublisher.publishEvent(new SessionExpiredEvent(session));
                    }
                });
            }
        }
    }


    @Override
    @PreDestroy
    public void close() throws IOException {
        if (shutdownClient.get()) {
            redisClient.shutdown();
        }
        if(pubSubConnection.isOpen()) {
            this.pubSubConnection.close();
        }
        if (redisConnection.isOpen()) {
            this.redisConnection.close();
        }
    }

    @Override
    public RedisSession newSession() {
        return new RedisSession(
                sessionIdGenerator.generateId(),
                valueSerializer,
                sessionConfiguration.getMaxInactiveInterval());
    }

    @Override
    public CompletableFuture<Optional<RedisSession>> findSession(String id) {
        return findSessionInternal(id, false);
    }

    @Override
    public CompletableFuture<Boolean> deleteSession(String id) {
        CompletableFuture<Boolean> completableFuture = new CompletableFuture<>();
        findSessionInternal(id, true).whenComplete((session, throwable) -> {
            if (throwable != null) {
                completableFuture.completeExceptionally(throwable);
            } else {
                if (session.isPresent()) {
                    RedisSession redisSession = session.get();
                    redisSession.setMaxInactiveInterval(Duration.ZERO);
                    save(redisSession).whenComplete((savedSession, throwable1) -> {
                        if (throwable1 != null) {
                            completableFuture.completeExceptionally(throwable1);
                        } else {
                            completableFuture.complete(true);
                        }
                    });
                } else {
                    completableFuture.complete(false);
                }
            }
        });
        return completableFuture;
    }

    @Override
    public CompletableFuture<RedisSession> save(RedisSession session) {
        Map<byte[], byte[]> changes = session.delta(charset);
        if (changes.isEmpty()) {
            return CompletableFuture.completedFuture(session);
        } else {
            Set<String> removedKeys = session.removedKeys;
            byte[][] removedKeyBytes = removedKeys.stream().map(str -> (RedisSession.ATTR_PREFIX + str).getBytes(charset)).toArray(byte[][]::new);
            if (!removedKeys.isEmpty()) {
                CompletableFuture<RedisSession> completableFuture = new CompletableFuture<>();
                byte[] sessionKey = getSessionKey(session.getId());
                sessionCommands.deleteAttributes(sessionKey, removedKeyBytes)
                        .whenComplete((aVoid, throwable) -> {
                            if (throwable == null) {
                                saveSessionDelta(session, changes, completableFuture);
                            } else {
                                completableFuture.completeExceptionally(throwable);
                            }
                        });
                return completableFuture;
            } else {

                CompletableFuture<RedisSession> future = new CompletableFuture<>();
                saveSessionDelta(session, changes, future);
                return future;
            }
        }
    }

    private void saveSessionDelta(RedisSession session, Map<byte[], byte[]> changes, CompletableFuture<RedisSession> future) {
        Duration maxInactiveInterval = session.getMaxInactiveInterval();
        long expirySeconds = maxInactiveInterval.getSeconds();
        byte[] sessionKey = getSessionKey(session.getId());
        if (expirySeconds == 0) {
            // delete the expired session
            redisConnection
                    .async()
                    .del(getExpiryKey(session))
                    .whenComplete((aLong, throwable) -> {
                        if (throwable != null) {
                            future.completeExceptionally(throwable);
                        } else {
                            future.complete(session);
                        }
                    });
        }
        sessionCommands.saveSessionData(
                sessionKey,
                changes
        ).whenComplete((s, throwable) -> {
            if (throwable != null) {
                future.completeExceptionally(throwable);
            } else {
                try {
                    if(session.isNew()) {
                        session.clearModifications();
                        eventPublisher.publishEvent(new SessionCreatedEvent(session));
                    }
                    else {
                        session.clearModifications();
                    }
                } catch(Throwable e) {
                    if(LOG.isErrorEnabled()){
                        LOG.error("Error publishing session creation event: " + e.getMessage(), e);
                    }
                } finally {
                    long fiveMinutesAfterExpires = expirySeconds
                            + TimeUnit.MINUTES.toSeconds(5);
                    redisConnection
                            .async()
                            .expire(sessionKey, fiveMinutesAfterExpires)
                            .whenComplete((aBoolean, throwable1) -> {
                                if (throwable1 != null) {
                                    future.completeExceptionally(throwable1);
                                } else {
                                    byte[] expiryKey = getExpiryKey(session);
                                    sessionCommands.saveExpiry(expiryKey, String.valueOf(expirySeconds).getBytes()).whenComplete((aVoid, throwable2) -> {
                                        if(throwable2 != null) {
                                            future.completeExceptionally(throwable2);
                                        }
                                        else {
                                            future.complete(session);
                                        }
                                    });
                                }
                            });
                }

            }
        });
    }

    private byte[] getExpiryKey(RedisSession session) {
        return (expiryPrefix + session.getId()).getBytes();
    }

    private CompletableFuture<Optional<RedisSession>> findSessionInternal(String id, boolean allowExpired) {
        CompletableFuture<Optional<RedisSession>> completableFuture = new CompletableFuture<>();
        RedisFuture<Map<byte[], byte[]>> future = sessionCommands.findSessionData(
                getSessionKey(id)
        );
        future.whenComplete((data, throwable) -> {

            if (CollectionUtils.isEmpty(data) || throwable != null) {
                completableFuture.complete(Optional.empty());
            } else {
                Map<String, byte[]> transformed = data.entrySet().stream().collect(
                        Collectors.toMap(
                                entry -> new String(entry.getKey(), charset),
                                Map.Entry::getValue
                        )
                );
                RedisSession session = new RedisSession(
                        id,
                        valueSerializer,
                        transformed);
                if (!session.isExpired() || allowExpired) {
                    completableFuture.complete(Optional.of(session));
                } else {
                    completableFuture.complete(Optional.empty());
                }
            }
        });
        return completableFuture;
    }

    private byte[] getSessionKey(String id) {
        return (sessionConfiguration.getKeyPrefix() + id).getBytes();
    }
}
