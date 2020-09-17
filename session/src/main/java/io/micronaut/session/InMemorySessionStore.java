/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.session;

import com.github.benmanes.caffeine.cache.*;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Internal;
import io.micronaut.session.event.SessionCreatedEvent;
import io.micronaut.session.event.SessionDeletedEvent;
import io.micronaut.session.event.SessionDestroyedEvent;
import io.micronaut.session.event.SessionExpiredEvent;

import javax.inject.Singleton;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Default implementation that stores sessions in-memory.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Primary
public class InMemorySessionStore implements SessionStore<InMemorySession> {

    private final SessionConfiguration sessionConfiguration;
    private final ApplicationEventPublisher eventPublisher;
    private final Cache<String, InMemorySession> sessions;
    private final SessionIdGenerator sessionIdGenerator;

    /**
     * Constructor.
     *
     * @param sessionIdGenerator The session id generator
     * @param sessionConfiguration The sessions configuration
     * @param eventPublisher The application event publisher
     */
    public InMemorySessionStore(
        SessionIdGenerator sessionIdGenerator,
        SessionConfiguration sessionConfiguration,
        ApplicationEventPublisher eventPublisher) {

        this.sessionIdGenerator = sessionIdGenerator;
        this.eventPublisher = eventPublisher;
        this.sessionConfiguration = sessionConfiguration;
        this.sessions = newSessionCache(sessionConfiguration);
    }

    @Override
    public InMemorySession newSession() {
        return new InMemorySession(sessionIdGenerator.generateId(), sessionConfiguration.getMaxInactiveInterval());
    }

    @Override
    public CompletableFuture<Optional<InMemorySession>> findSession(String id) {
        InMemorySession session = sessions.getIfPresent(id);
        return CompletableFuture.completedFuture(
            Optional.ofNullable(session != null && !session.isExpired() ? session : null)
        );
    }

    @Override
    public CompletableFuture<Boolean> deleteSession(String id) {
        sessions.invalidate(id);
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<InMemorySession> save(InMemorySession session) {
        if (session == null) {
            throw new IllegalArgumentException("Session cannot be null");
        }
        String id = session.getId();
        session.setNew(false);
        InMemorySession existing = sessions.getIfPresent(id);
        // if the instance is the same then merely accessing it as above will
        // result in the expiry interval being reset so nothing else needs to be done
        if (session != existing) {
            sessions.put(id, session);
            if (existing == null) {
                eventPublisher.publishEvent(new SessionCreatedEvent(session));
            }
        }
        return CompletableFuture.completedFuture(session);
    }

    /**
     * Performs any pending maintenance operations needed by the cache.
     */
    @Internal
    void cleanUp() {
        sessions.cleanUp();
    }

    /**
     * Creates a new session cache.
     *
     * @param configuration The session configuration
     * @return The new cache
     */
    protected Cache<String, InMemorySession> newSessionCache(SessionConfiguration configuration) {
        Caffeine<String, InMemorySession> builder = Caffeine.newBuilder().removalListener(newRemovalListener());

        if (configuration.isPromptExpiration()) {
            configuration.getExecutorService()
                    .map(Scheduler::forScheduledExecutorService)
                    .ifPresent(builder::scheduler);
        }

        builder.expireAfter(newExpiry());
        configuration.getMaxActiveSessions().ifPresent(builder::maximumSize);

        return builder.build();
    }

    private Expiry<String, InMemorySession> newExpiry() {
        return new Expiry<String, InMemorySession>() {
            @Override
            public long expireAfterCreate(String key, InMemorySession value, long currentTime) {
                return newExpiry(value);
            }

            @Override
            public long expireAfterUpdate(String key, InMemorySession value, long currentTime, long currentDuration) {
                return newExpiry(value);
            }

            @Override
            public long expireAfterRead(String key, InMemorySession value, long currentTime, long currentDuration) {
                return newExpiry(value);
            }

            private long newExpiry(InMemorySession value) {
                Instant current = Instant.now();
                value.setLastAccessedTime(current);
                return value.getMaxInactiveInterval().toNanos();
            }
        };
    }

    private RemovalListener<String, Session> newRemovalListener() {
        return (key, value, cause) -> {
            switch (cause) {
                case REPLACED:
                    eventPublisher.publishEvent(new SessionDestroyedEvent(value));
                    break;
                case SIZE:
                case EXPIRED:
                    eventPublisher.publishEvent(new SessionExpiredEvent(value));
                    break;
                case EXPLICIT:
                    eventPublisher.publishEvent(new SessionDeletedEvent(value));
                    break;
                default:
                    throw new IllegalStateException("Session should never be garbage collectable");
            }
        };
    }
}
