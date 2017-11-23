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
package org.particleframework.session;

import org.particleframework.core.convert.value.MutableConvertibleValues;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Set;

/**
 * A {@link Session} that is help in-memory
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class InMemorySession implements Session {

    private final MutableConvertibleValues<Object> attributes = MutableConvertibleValues.of(new LinkedHashMap<>());
    private final Instant creationTime = Instant.now();
    private final String id;
    private Duration maxInactiveInterval;
    private Instant lastAccessTime = Instant.now();

    protected InMemorySession(String id, Duration maxInactiveInterval) {
        this.id = id;
        this.maxInactiveInterval = maxInactiveInterval;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Instant getLastAccessedTime() {
        return lastAccessTime;
    }

    @Override
    public void setMaxInactiveInterval(Duration duration) {
        if(duration != null) {
            maxInactiveInterval = duration;
        }
    }

    @Override
    public Duration getMaxInactiveInterval() {
        return maxInactiveInterval;
    }

    @Override
    public boolean isExpired() {
        Duration maxInactiveInterval = getMaxInactiveInterval();
        if(maxInactiveInterval.isNegative()) {
            return false;
        }
        else {
            Instant now = Instant.now();
            return now.minus(maxInactiveInterval).compareTo(lastAccessTime) >= 0;
        }
    }

    @Override
    public Instant getCreationTime() {
        return creationTime;
    }

    void setLastAccessTime(Instant lastAccessTime) {
        this.lastAccessTime = lastAccessTime;
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
    public Set<String> getNames() {
        return attributes.getNames();
    }

    @Override
    public Collection<Object> values() {
        return attributes.values();
    }


    @Override
    public <T> Optional<T> get(CharSequence name, Class<T> requiredType) {
        return attributes.get(name, requiredType);
    }

}
