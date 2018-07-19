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
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.dynamic.RedisCommandFactory;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import io.micronaut.configuration.lettuce.RedisConnectionUtil;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.serialize.ObjectSerializer;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.session.*;
import io.micronaut.session.event.SessionCreatedEvent;
import io.micronaut.session.event.SessionDeletedEvent;
import io.micronaut.session.event.SessionExpiredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.micronaut.configuration.lettuce.session.RedisSessionStore.RedisSession.*;

/**
 * <p>An implementation of the {@link SessionStore} interface for Redis. Partially inspired by Spring Session.</p>
 *
 * <h2>Session serialization</h2>
 *
 * <p>Sessions are stored within Redis hashes. The values contained within the sessions are serialized by the {@link ObjectSerializer} configured by {@link RedisHttpSessionConfiguration#getValueSerializer()}
 * which by default uses Java serialization. The Jackson Micronaut module includes the ability the configure JSON serialization as an alternative.</p>
 *
 * <h2>Storage Details</h2>
 *
 * <p>Sessions are stored within Redis hashes by default prefixed with {@code micronaut:session:sessions:[SESSION_ID]}. The expiry of the hash is set to 5 minutes after the actual expiry and
 * expired sessions are simply not returned by {@link #findSession(String)}</p>
 *
 * <p>More exact session expiry entries are stored with keys {@code micronaut:session:expiry:[SESSION_ID]} and current active sessions are tracked within sorted set at the key {@code micronaut:session:active-sessions}.
 * The entries within the set are sorted by expiry time and a scheduled job that runs every minute periodically touches the keys within the set that match the last minute thus ensuring Redis propagates expiry events in a timely manner.</p>
 *
 * <h2>Redis Pub/Sub</h2>
 *
 * <p>This implementation requires the Redis instance to have keyspace event notification enabled with {@code notify-keyspace-events Egx}. The implementation will attempt to enable this programmatically. This behaviour can be disabled with {@link RedisHttpSessionConfiguration#isEnableKeyspaceEvents()} </p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Primary
@Requires(property = RedisSessionStore.REDIS_SESSION_ENABLED, value = "true")
@Replaces(InMemorySessionStore.class)
public class RedisSessionStore extends RedisPubSubAdapter<String, String> implements SessionStore<RedisSessionStore.RedisSession> {

    public static final String REDIS_SESSION_ENABLED = SessionSettings.HTTP + ".redis.enabled";
    private static final int EXPIRATION_SECONDS = 5;
    private static final Logger LOG  = LoggerFactory.getLogger(RedisSessionStore.class);
    private final RedisSessionCommands sessionCommands;
    private final RedisHttpSessionConfiguration sessionConfiguration;
    private final SessionIdGenerator sessionIdGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectSerializer valueSerializer;
    private final Charset charset;
    private final String expiryPrefix;
    private final byte[] sessionCreatedTopic;
    private final byte[] activeSessionsSet;
    private final RedisHttpSessionConfiguration.WriteMode writeMode;

    /**
     * Constructor.
     * @param sessionIdGenerator sessionIdGenerator
     * @param sessionConfiguration sessionConfiguration
     * @param beanLocator beanLocator
     * @param defaultSerializer The default value serializer
     * @param scheduledExecutorService scheduledExecutorService
     * @param eventPublisher eventPublisher
     */
    public RedisSessionStore(
            SessionIdGenerator sessionIdGenerator,
            RedisHttpSessionConfiguration sessionConfiguration,
            BeanLocator beanLocator,
            ObjectSerializer defaultSerializer,
            @Named(TaskExecutors.SCHEDULED) ExecutorService scheduledExecutorService,
            ApplicationEventPublisher eventPublisher) {
        this.writeMode = sessionConfiguration.getWriteMode();
        this.sessionIdGenerator = sessionIdGenerator;
        this.valueSerializer = sessionConfiguration
                .getValueSerializer()
                .flatMap(beanLocator::findOrInstantiateBean)
                .orElse(defaultSerializer);
        this.eventPublisher = eventPublisher;
        this.sessionConfiguration = sessionConfiguration;
        this.charset = sessionConfiguration.getCharset();
        StatefulConnection statefulConnection = findRedisConnection(sessionConfiguration, beanLocator);
        StatefulRedisPubSubConnection<String, String> pubSubConnection = findRedisPubSubConnection(sessionConfiguration, beanLocator);


        this.expiryPrefix = sessionConfiguration.getNamespace() + "expiry:";
        this.sessionCreatedTopic = sessionConfiguration.getSessionCreatedTopic().getBytes(charset);
        this.activeSessionsSet = sessionConfiguration.getActiveSessionsKey().getBytes(charset);
        pubSubConnection.addListener(this);
        RedisPubSubCommands<String, String> sync = pubSubConnection.sync();
        try {
            sync.psubscribe(
                    "__keyevent@*:del",
                    "__keyevent@*:expired"
            );
            sync.subscribe(sessionConfiguration.getSessionCreatedTopic());
        } catch (Exception e) {
            throw new ConfigurationException("Unable to subscribe to session topics: " + e.getMessage(), e);
        }
        RedisCommandFactory redisCommandFactory = new RedisCommandFactory(
                statefulConnection
        );
        this.sessionCommands = redisCommandFactory.getCommands(RedisSessionCommands.class);

        if (sessionConfiguration.isEnableKeyspaceEvents()) {

            try {
                String result = this.sessionCommands.configSet(
                        "notify-keyspace-events", "Egx"
                );
                if (!result.equalsIgnoreCase("ok")) {
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("Failed to enable keyspace events on the Redis server. Manual configuration my be required");
                    }
                }
            } catch (Exception e) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Failed to enable keyspace events on the Redis server. Manual configuration my be required", e);
                }
            }
        }
        if (scheduledExecutorService instanceof ScheduledExecutorService) {

            long checkDelayMillis = sessionConfiguration.getExpiredSessionCheck().toMillis();
            ((ScheduledExecutorService) scheduledExecutorService).scheduleAtFixedRate(
                    () -> {
                        long oneMinuteFromNow = Instant.now().plus(1, ChronoUnit.MINUTES).toEpochMilli();
                        long oneMinuteAgo = Instant.now().minus(1, ChronoUnit.MINUTES).toEpochMilli();
                        sessionCommands.zrangebyscore(
                                activeSessionsSet, Range.create(Long.valueOf(oneMinuteAgo).doubleValue(), Long.valueOf(oneMinuteFromNow).doubleValue())
                        ).thenAccept((aboutToExpire) -> {
                            if (aboutToExpire != null) {
                                for (byte[] bytes : aboutToExpire) {
                                    byte[] expiryKey = getExpiryKey(new String(bytes, charset));
                                    sessionCommands.get(expiryKey);
                                }
                            }
                        });
                    },
                    checkDelayMillis,
                    checkDelayMillis,
                    TimeUnit.MILLISECONDS
            );
        } else {
            throw new ConfigurationException("Configured scheduled executor service is not an instanceof ScheduledExecutorService");
        }
    }

    /**
     * Getter.
     * @return ObjectSerializer
     */
    public ObjectSerializer getValueSerializer() {
        return valueSerializer;
    }

    @Override
    public void message(String channel, String message) {
        if (channel.equals(sessionConfiguration.getSessionCreatedTopic())) {
            findSessionInternal(message, false).whenComplete((optional, throwable) -> {
                if (throwable == null && optional.isPresent()) {
                    RedisSession session = optional.get();
                    eventPublisher.publishEvent(new SessionCreatedEvent(session));
                }
            });
        }
    }

    @Override
    public void message(String pattern, String channel, String message) {
        if (message.startsWith(expiryPrefix)) {
            boolean expired = pattern.endsWith(":expired");
            if (pattern.endsWith(":del") || expired) {
                String id = message.substring(expiryPrefix.length());
                sessionCommands.zrem(activeSessionsSet, id.getBytes(charset)).whenComplete((aVoid, throwable) -> {
                    if (throwable != null) {
                        if (LOG.isErrorEnabled()) {
                            LOG.error("Error removing session [" + id + "] from active sessions: " + throwable.getMessage(), throwable);
                        }
                    }
                });
                findSessionInternal(id, true).whenComplete((optional, throwable) -> {
                    if (throwable == null && optional.isPresent()) {
                        RedisSession session = optional.get();
                        eventPublisher.publishEvent(expired ? new SessionExpiredEvent(session) : new SessionDeletedEvent(session));
                    }
                });
            }
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
        byte[] sessionIdBytes = session.getId().getBytes(charset);
        if (expirySeconds == 0) {
            // delete the expired session
            CompletableFuture<Void> deleteOp = sessionCommands.del(getExpiryKey(session));
            deleteOp.whenComplete((aLong, throwable) -> {
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
                    if (session.isNew()) {
                        session.clearModifications();

                        sessionCommands.publish(sessionCreatedTopic, sessionIdBytes).whenComplete((aLong, throwable12) -> {
                            if (throwable12 != null) {
                                if (LOG.isErrorEnabled()) {
                                    LOG.error("Error publishing session creation event: " + throwable12.getMessage(), throwable12);
                                }
                            }
                        });
                    } else {
                        session.clearModifications();
                    }
                } catch (Throwable e) {
                    if (LOG.isErrorEnabled()) {
                        LOG.error("Error publishing session creation event: " + e.getMessage(), e);
                    }
                } finally {
                    long fiveMinutesAfterExpires = expirySeconds
                            + TimeUnit.MINUTES.toSeconds(EXPIRATION_SECONDS);
                    byte[] expiryKey = getExpiryKey(session);
                    double expireTimeScore = Long.valueOf(Instant.now().plus(expirySeconds, ChronoUnit.SECONDS).toEpochMilli()).doubleValue();

                    CompletableFuture<Boolean> expireOp = sessionCommands.expire(sessionKey, fiveMinutesAfterExpires);
                    CompletableFuture<Void> saveExpiryOp = sessionCommands.saveExpiry(expiryKey, String.valueOf(expirySeconds).getBytes());
                    CompletableFuture<Long> saveActiveSessionOp = sessionCommands.zadd(activeSessionsSet, expireTimeScore, sessionIdBytes);
                    CompletableFuture.allOf(expireOp, saveExpiryOp, saveActiveSessionOp).whenComplete((aBoolean, throwable1) -> {
                                if (throwable1 != null) {
                                    future.completeExceptionally(throwable1);
                                } else {
                                    future.complete(session);
                                }
                            });
                }

            }
        });
    }

    private byte[] getExpiryKey(RedisSession session) {
        String id = session.getId();
        return getExpiryKey(id);
    }

    private byte[] getExpiryKey(String id) {
        return (expiryPrefix + id).getBytes();
    }

    private CompletableFuture<Optional<RedisSession>> findSessionInternal(String id, boolean allowExpired) {
        CompletableFuture<Optional<RedisSession>> completableFuture = new CompletableFuture<>();
        CompletableFuture<Map<byte[], byte[]>> future = sessionCommands.findSessionData(
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
        return (sessionConfiguration.getNamespace() + "sessions:" + id).getBytes();
    }

    private StatefulConnection findRedisConnection(RedisHttpSessionConfiguration sessionConfiguration, BeanLocator beanLocator) {
        Optional<String> serverName = sessionConfiguration.getServerName();
        return RedisConnectionUtil.findRedisConnection(beanLocator, serverName, "No Redis server configured to store sessions");
    }

    @SuppressWarnings("unchecked")
    private StatefulRedisPubSubConnection<String, String> findRedisPubSubConnection(RedisHttpSessionConfiguration sessionConfiguration, BeanLocator beanLocator) {
        Optional<String> serverName = sessionConfiguration.getServerName();
        return (StatefulRedisPubSubConnection<String, String>)
                serverName.map(name -> beanLocator.findBean(StatefulRedisPubSubConnection.class, Qualifiers.byName(name))
                        .map(conn -> (StatefulConnection) conn)
                        .orElse(
                                beanLocator.findBean(StatefulRedisPubSubConnection.class, Qualifiers.byName(name)).orElseThrow(() ->
                                        new ConfigurationException("No Redis server configured to store sessions")
                                )
                        )).orElseGet(() -> beanLocator.findBean(StatefulRedisPubSubConnection.class)
                        .map(conn -> (StatefulConnection) conn)
                        .orElse(
                                beanLocator.findBean(StatefulRedisPubSubConnection.class).orElseThrow(() ->
                                        new ConfigurationException("No Redis server configured to store sessions")
                                )
                        ));
    }

    private static Instant readLastAccessTimed(Map<String, byte[]> data) {
        return readInstant(data, ATTR_LAST_ACCESSED);
    }

    private static Duration readMaxInactive(Map<String, byte[]> data) {
        if (data != null) {
            byte[] value = data.get(ATTR_MAX_INACTIVE_INTERVAL);
            if (value != null) {
                try {
                    Long seconds = Long.valueOf(new String(value));
                    return Duration.ofSeconds(seconds);
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
        return null;
    }

    private static Instant readCreationTime(Map<String, byte[]> data) {
        return readInstant(data, ATTR_CREATION_TIME);
    }

    private static Instant readInstant(Map<String, byte[]> data, String attr) {
        if (data != null) {
            byte[] value = data.get(attr);
            if (value != null) {
                try {
                    Long millis = Long.valueOf(new String(value));
                    return Instant.ofEpochMilli(millis);
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
        return Instant.now();
    }

    /**
     * Description states on how the session is modified.
     */
    enum Modification {
        CREATED,
        CLEARED,
        ADDITION,
        REMOVAL
    }

    /**
     * A new redis session that is in memory and not yet persisted.
     */
    class RedisSession extends InMemorySession implements Session {
        static final String ATTR_CREATION_TIME = "Creation-Time";
        static final String ATTR_LAST_ACCESSED = "Last-Accessed";
        static final String ATTR_MAX_INACTIVE_INTERVAL = "Max-Inactive-Interval";
        static final String ATTR_PREFIX = "attr:";
        final Set<String> removedKeys = new HashSet<>(2);
        final Set<String> modifiedKeys = new HashSet<>(2);
        private final Set<Modification> modifications = new HashSet<>();
        private final ObjectSerializer valueSerializer;

        /**
         * Construct a new Redis session not yet persisted.
         *
         * @param id The id of the session
         * @param valueSerializer The value serializer
         * @param maxInactiveInterval The initial max inactive interval
         */
        RedisSession(
                String id,
                ObjectSerializer valueSerializer,
                Duration maxInactiveInterval) {
            super(id, Instant.now(), maxInactiveInterval);
            this.valueSerializer = valueSerializer;
            this.modifications.add(Modification.CREATED);
        }


        /**
         * Construct a new Redis session from existing redis data.
         *
         * @param id The id of the session
         * @param valueSerializer valueSerializer
         * @param data The session data
         */
        RedisSession(
                String id,
                ObjectSerializer valueSerializer,
                Map<String, byte[]> data) {
            super(id, readCreationTime(data), readMaxInactive(data));
            this.valueSerializer = valueSerializer;
            this.lastAccessTime = readLastAccessTimed(data);

            for (String name: data.keySet()) {
                if (name.startsWith(ATTR_PREFIX)) {
                    String attrName = name.substring(ATTR_PREFIX.length());
                    attributeMap.put(attrName, data.get(name));
                }
            }
        }

        @Override
        public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
            Optional<T> result = super.get(name, conversionContext);
            if (!result.isPresent() && attributeMap.containsKey(name)) {
                Object val = attributeMap.get(name);
                if (val instanceof byte[]) {
                    Optional<T> deserialized = valueSerializer.deserialize((byte[]) val, conversionContext.getArgument().getType());
                    deserialized.ifPresent(t -> attributeMap.put(name, t));
                    return deserialized;
                }
            }
            return result;
        }

        @Override
        public Optional<Object> get(CharSequence attr) {
            Optional<Object> result = super.get(attr);
            if (result.isPresent()) {
                Object val = result.get();
                if (val instanceof byte[]) {
                    Optional<Object> deserialized = valueSerializer.deserialize((byte[]) val);
                    deserialized.ifPresent(t -> attributeMap.put(attr, t));
                    return deserialized;
                }
            }
            return result;
        }

        @Override
        public Session setLastAccessedTime(Instant instant) {
            if (instant != null) {
                if (!isNew()) {
                    this.modifications.add(Modification.ADDITION);
                }
                if (writeMode == RedisHttpSessionConfiguration.WriteMode.BACKGROUND) {
                    byte[] lastAccessedTimeBytes = String.valueOf(instant.toEpochMilli()).getBytes();
                    writeBehind(ATTR_LAST_ACCESSED, lastAccessedTimeBytes);
                }
            }
            return super.setLastAccessedTime(instant);
        }

        @Override
        public Session setMaxInactiveInterval(Duration duration) {
            if (duration != null) {

                if (!isNew()) {
                    this.modifications.add(Modification.ADDITION);
                }
                if (writeMode == RedisHttpSessionConfiguration.WriteMode.BACKGROUND) {
                    byte[] intervalBytes = String.valueOf(getMaxInactiveInterval().getSeconds()).getBytes();
                    writeBehind(ATTR_MAX_INACTIVE_INTERVAL, intervalBytes);
                }
            }
            return super.setMaxInactiveInterval(duration);
        }

        @Override
        public MutableConvertibleValues<Object> put(CharSequence key, Object value) {
            if (value == null) {
                return remove(key);
            } else {
                if (key != null && !isNew()) {
                    this.modifications.add(Modification.ADDITION);
                    String attr = key.toString();
                    this.modifiedKeys.add(attr);
                    if (writeMode == RedisHttpSessionConfiguration.WriteMode.BACKGROUND) {
                        byte[] bytes = value instanceof byte[] ? (byte[]) value : valueSerializer.serialize(value).orElse(null);
                        if (bytes != null) {
                            writeBehind(ATTR_PREFIX + attr, bytes);
                        }
                    }
                }
                return super.put(key, value);
            }
        }

        @Override
        public MutableConvertibleValues<Object> remove(CharSequence key) {
            if (key != null && !isNew()) {
                this.modifications.add(Modification.REMOVAL);
                String attr = key.toString();
                this.removedKeys.add(attr);
                if (writeMode == RedisHttpSessionConfiguration.WriteMode.BACKGROUND) {
                    sessionCommands.deleteAttributes(getSessionKey(getId()), getAttributeKey(attr))
                            .exceptionally(attributeErrorHandler(attr));
                }
            }
            this.modifications.add(Modification.REMOVAL);
            return super.remove(key);
        }

        private byte[] getAttributeKey(String attr) {
            return (ATTR_PREFIX + attr).getBytes(charset);
        }

        @Override
        public MutableConvertibleValues<Object> clear() {
            if (!isNew()) {

                this.modifications.add(Modification.CLEARED);
                Set<String> names = names();
                this.removedKeys.addAll(names);
                if (writeMode == RedisHttpSessionConfiguration.WriteMode.BACKGROUND) {
                    byte[][] attributes = names.stream().map(this::getAttributeKey).toArray(byte[][]::new);
                    sessionCommands.deleteAttributes(getSessionKey(getId()), attributes)
                            .exceptionally(throwable -> {
                                if (LOG.isErrorEnabled()) {
                                    LOG.error("Error writing behind session attributes: " + throwable.getMessage(), throwable);
                                }
                                return null;
                            });
                }
            }
            return super.clear();
        }

        @Override
        public boolean isNew() {
            return modifications.contains(Modification.CREATED);
        }

        /**
         * @param charset The charset to evaluate
         * @return Produces a modification delta with the changes necessary to save the session
         */
        Map<byte[], byte[]> delta(Charset charset) {
            if (modifications.isEmpty()) {
                return Collections.emptyMap();
            } else {
                Map<byte[], byte[]> delta = new LinkedHashMap<>();
                if (isNew()) {
                    byte[] creationTimeBytes = String.valueOf(getCreationTime().toEpochMilli()).getBytes();
                    delta.put(ATTR_CREATION_TIME.getBytes(charset), creationTimeBytes);
                    Instant lastAccessedTime = getLastAccessedTime();
                    byte[] lastAccessedTimeBytes = String.valueOf(lastAccessedTime.toEpochMilli()).getBytes();

                    delta.put(ATTR_LAST_ACCESSED.getBytes(charset), lastAccessedTimeBytes);
                    delta.put(ATTR_MAX_INACTIVE_INTERVAL.getBytes(charset), String.valueOf(getMaxInactiveInterval().getSeconds()).getBytes());
                    for (CharSequence key : attributeMap.keySet()) {
                        convertAttribute(key, delta, charset);
                    }
                } else {
                    delta.put(ATTR_LAST_ACCESSED.getBytes(charset), String.valueOf(getLastAccessedTime().toEpochMilli()).getBytes());
                    delta.put(ATTR_MAX_INACTIVE_INTERVAL.getBytes(charset), String.valueOf(getMaxInactiveInterval().getSeconds()).getBytes());
                    for (CharSequence modifiedKey : modifiedKeys) {
                        convertAttribute(modifiedKey, delta, charset);
                    }
                }

                return delta;
            }
        }

        /**
         * Clear member attributes.
         */
        void clearModifications() {
            modifications.clear();
            removedKeys.clear();
            modifiedKeys.clear();
        }

        private Function<Throwable, Void> attributeErrorHandler(String attr) {
            return throwable -> {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Error writing behind session attribute [" + attr + "]: " + throwable.getMessage(), throwable);
                }
                return null;
            };
        }

        private void writeBehind(String attr, byte[] lastAccessedTimeBytes) {
            sessionCommands.setAttribute(getSessionKey(getId()), attr.getBytes(charset), lastAccessedTimeBytes)
                    .exceptionally(attributeErrorHandler(attr));
        }

        private void convertAttribute(CharSequence key, Map<byte[], byte[]> delta, Charset charset) {
            Object rawValue = attributeMap.get(key);
            byte[] attributeKey = getAttributeKey(key.toString());
            if (rawValue instanceof byte[]) {
                delta.put(attributeKey, (byte[]) rawValue);
            } else if (rawValue != null) {
                Optional<byte[]> serialized = valueSerializer.serialize(rawValue);
                serialized.ifPresent(bytes -> delta.put(attributeKey, bytes));
            }
        }

    }
}
