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

import org.particleframework.core.convert.ArgumentConversionContext;
import org.particleframework.core.convert.value.MutableConvertibleValues;
import org.particleframework.core.serialize.ObjectSerializer;
import org.particleframework.session.InMemorySession;
import org.particleframework.session.Session;

import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Implementation of the {@link Session} interface for Redis
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class RedisSession extends InMemorySession implements Session {
    public static final String ATTR_CREATION_TIME = "Creation-Time";
    public static final String ATTR_LAST_ACCESSED = "Last-Accessed";
    public static final String ATTR_MAX_INACTIVE_INTERVAL = "Max-Inactive-Interval";
    public static final String ATTR_PREFIX = "attr:";

    private final Set<Modification> modifications = new HashSet<>();

    final Set<String> removedKeys = new HashSet<>(2);
    final Set<String> modifiedKeys = new HashSet<>(2);
    private final ObjectSerializer valueSerializer;

    /**
     * Construct a new Redis session not yet persisted
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
     * Construct a new Redis session from existing redis data
     *
     * @param id The id of the session
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
            if(name.startsWith(ATTR_PREFIX)) {
                String attrName = name.substring(ATTR_PREFIX.length());
                attributeMap.put(attrName, data.get(name));
            }
        }
    }


    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        Optional<T> result = super.get(name, conversionContext);
        if(!result.isPresent() && attributeMap.containsKey(name)) {
            Object val = attributeMap.get(name);
            if(val instanceof byte[]) {
                Optional<T> deserialized = valueSerializer.deserialize((byte[]) val, conversionContext.getArgument().getType());
                deserialized.ifPresent(t -> attributeMap.put(name, t));
                return deserialized;
            }
        }
        return result;
    }

    @Override
    public Session setLastAccessedTime(Instant instant) {
        if(!isNew()) {
            this.modifications.add(Modification.ADDITION);
        }
        return super.setLastAccessedTime(instant);
    }

    @Override
    public Session setMaxInactiveInterval(Duration duration) {
        if(!isNew()) {
            this.modifications.add(Modification.ADDITION);
        }
        return super.setMaxInactiveInterval(duration);
    }

    @Override
    public MutableConvertibleValues<Object> put(CharSequence key, Object value) {
        if(value == null) {
            return remove(key);
        }
        else {
            if(key != null && !isNew()) {
                this.modifications.add(Modification.ADDITION);
                this.modifiedKeys.add(key.toString());
            }
            return super.put(key, value);
        }
    }

    @Override
    public MutableConvertibleValues<Object> remove(CharSequence key) {
        if(key != null && !isNew()) {
            this.modifications.add(Modification.REMOVAL);
            this.removedKeys.add(key.toString());
        }
        this.modifications.add(Modification.REMOVAL);
        return super.remove(key);
    }

    @Override
    public MutableConvertibleValues<Object> clear() {
        if(!isNew()) {

            this.modifications.add(Modification.CLEARED);
            this.removedKeys.addAll(getNames());
        }
        return super.clear();
    }

    boolean isNew() {
        return modifications.contains(Modification.CREATED);
    }

    /**
     * @return Produces a modification delta with the changes necessary to save the session
     */
    Map<byte[], byte[]> delta(Charset charset) {
        if(modifications.isEmpty()) {
            return Collections.emptyMap();
        }
        else {
            Map<byte[], byte[]> delta = new LinkedHashMap<>();
            if(isNew()) {
                byte[] creationTimeBytes = String.valueOf(getCreationTime().toEpochMilli()).getBytes();
                delta.put(ATTR_CREATION_TIME.getBytes(charset), creationTimeBytes);
                Instant lastAccessedTime = getLastAccessedTime();
                byte[] lastAccessedTimeBytes = String.valueOf(lastAccessedTime.toEpochMilli()).getBytes();

                delta.put(ATTR_LAST_ACCESSED.getBytes(charset), lastAccessedTimeBytes);
                delta.put(ATTR_MAX_INACTIVE_INTERVAL.getBytes(charset), String.valueOf( getMaxInactiveInterval().getSeconds()).getBytes());
                for (CharSequence key : attributeMap.keySet()) {
                    convertAttribute(key, delta, charset);
                }
            }
            else {
                delta.put(ATTR_LAST_ACCESSED.getBytes(charset), String.valueOf(getLastAccessedTime().toEpochMilli()).getBytes());
                delta.put(ATTR_MAX_INACTIVE_INTERVAL.getBytes(charset), String.valueOf( getMaxInactiveInterval().getSeconds()).getBytes());
                for (CharSequence modifiedKey : modifiedKeys) {
                    convertAttribute(modifiedKey, delta, charset);
                }
            }

            return delta;
        }
    }

    void clearModifications() {
        modifications.clear();
        removedKeys.clear();
        modifiedKeys.clear();
    }


    enum Modification {
        CREATED,
        CLEARED,
        ADDITION,
        REMOVAL
    }

    private void convertAttribute(CharSequence key, Map<byte[], byte[]> delta, Charset charset) {
        Object rawValue = attributeMap.get(key);
        String attrKey = ATTR_PREFIX + key;
        if(rawValue instanceof byte[]) {
            delta.put(attrKey.getBytes(charset), (byte[]) rawValue);
        }
        else if(rawValue != null) {
            Optional<byte[]> serialized = valueSerializer.serialize(rawValue);
            serialized.ifPresent(bytes -> delta.put(attrKey.getBytes(charset), bytes));
        }
    }

    private static Duration readMaxInactive(Map<String, byte[]> data) {
        if(data != null) {
            byte[] value = data.get(ATTR_MAX_INACTIVE_INTERVAL);
            if(value != null) {
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

    private static Instant readLastAccessTimed(Map<String, byte[]> data) {
        return readInstant(data, ATTR_LAST_ACCESSED);
    }

    private static Instant readCreationTime(Map<String, byte[]> data) {
        return readInstant(data, ATTR_CREATION_TIME);
    }

    private static Instant readInstant(Map<String, byte[]> data, String attr) {
        if(data != null) {
            byte[] value = data.get(attr);
            if(value != null) {
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
}