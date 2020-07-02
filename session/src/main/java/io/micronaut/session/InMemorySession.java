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

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.value.MutableConvertibleValues;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A {@link Session} that is help in-memory.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class InMemorySession implements Session {

    protected final Map<CharSequence, Object> attributeMap = new LinkedHashMap<>();
    protected final MutableConvertibleValues<Object> attributes = MutableConvertibleValues.of(attributeMap);
    protected Instant lastAccessTime = Instant.now();

    private final String id;
    private final Instant creationTime;
    private Duration maxInactiveInterval;
    private boolean isNew = true;

    /**
     * Constructor.
     *
     * @param id The session id
     * @param maxInactiveInterval The max inactive interval
     */
    protected InMemorySession(String id, Duration maxInactiveInterval) {
        this(id, Instant.now(), maxInactiveInterval);
    }

    /**
     * Constructor.
     *
     * @param id The session id
     * @param creationTime The creation time
     * @param maxInactiveInterval The max inactive interval
     */
    protected InMemorySession(String id, Instant creationTime, Duration maxInactiveInterval) {
        this.id = id;
        this.creationTime = creationTime;
        this.maxInactiveInterval = maxInactiveInterval;
    }

    @Override
    @NonNull
    public String getId() {
        return id;
    }

    @Override
    @NonNull
    public Instant getLastAccessedTime() {
        return lastAccessTime;
    }

    @Override
    public Session setMaxInactiveInterval(Duration duration) {
        if (duration != null) {
            maxInactiveInterval = duration;
        }
        return this;
    }

    @Override
    public Session setLastAccessedTime(Instant instant) {
        if (instant != null) {
            this.lastAccessTime = instant;
        }
        return this;
    }

    @Override
    public Duration getMaxInactiveInterval() {
        return maxInactiveInterval;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @Override
    public boolean isModified() {
        return isNew;
    }

    @Override
    @NonNull
    public Instant getCreationTime() {
        return creationTime;
    }

    @Override
    public MutableConvertibleValues<Object> put(CharSequence key, Object value) {
        return attributes.put(key, value);
    }

    @Override
    public MutableConvertibleValues<Object> remove(CharSequence key) {
        return attributes.remove(key);
    }

    @Override
    public MutableConvertibleValues<Object> clear() {
        return attributes.clear();
    }

    @Override
    public Set<String> names() {
        return attributes.names();
    }

    @Override
    public Collection<Object> values() {
        return attributes.values();
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        return attributes.get(name, conversionContext);
    }

    /**
     * @param aNew Set is new
     */
    public void setNew(boolean aNew) {
        isNew = aNew;
    }
}
