/*
 * Copyright 2018 original authors
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
package io.micronaut.core.beans;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Simple class that provides a map interface over a bean
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class BeanMap<T> implements Map<String, Object> {

    private final Class<T> beanType;
    private final Map<String, PropertyAccess> propertyAccesses;

    BeanMap(Class<T> beanType, PropertyAccess...propertyAccesses) {
        this.beanType = beanType;
        this.propertyAccesses = new LinkedHashMap<>(propertyAccesses.length);
        for (PropertyAccess propertyAccess : propertyAccesses) {
            this.propertyAccesses.put(propertyAccess.getName(), propertyAccess);
        }
    }

    /**
     * @return The bean type
     */
    public Class<T> getBeanType() {
        return beanType;
    }

    @Override
    public int size() {
        return propertyAccesses.size();
    }

    @Override
    public boolean isEmpty() {
        return size() > 0;
    }

    @Override
    public boolean containsKey(Object key) {
        return keySet().contains(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return values().contains(value);
    }

    @Override
    public Object get(Object key) {
        PropertyAccess propertyAccess = propertyAccesses.get(key);
        if(propertyAccess != null) {
            return propertyAccess.read();
        }
        return null;
    }

    @Override
    public Object put(String key, Object value) {
        PropertyAccess propertyAccess = propertyAccesses.get(key);
        if(propertyAccess != null) {
            Object existing = propertyAccess.read();
            propertyAccess.write(value);
            return existing;
        }
        return null;
    }

    public void set(String key, Object value) {
        PropertyAccess propertyAccess = propertyAccesses.get(key);
        if(propertyAccess != null) {
            propertyAccess.write(value);
        }
    }

    @Override
    public Object remove(Object key) {
        throw new UnsupportedOperationException("BeanMap does not support removal");
    }

    @Override
    public void putAll(Map<? extends String, ?> m) {
        for (Entry<? extends String, ?> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("BeanMap does not support removal");
    }

    @Override
    public Set<String> keySet() {
        return propertyAccesses.keySet();
    }

    @Override
    public Collection<Object> values() {
        return propertyAccesses.values().stream().map(PropertyAccess::read).collect(Collectors.toSet());
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        Set<Entry<String, PropertyAccess>> entries = propertyAccesses.entrySet();
        Set<Entry<String, Object>> nonNullEntries = new HashSet<>();
        for (Entry<String, PropertyAccess> entry : entries) {
            Object v = entry.getValue().read();
            if(v != null) {
                nonNullEntries.add(new Entry<String, Object>() {
                    @Override
                    public String getKey() {
                        return entry.getKey();
                    }

                    @Override
                    public Object getValue() {
                        return v;
                    }

                    @Override
                    public Object setValue(Object value) {
                        entry.getValue().write(value);
                        return v;
                    }
                });
            }
        }

        return nonNullEntries;
    }

    /**
     * Interface used to access properties
     */
    interface PropertyAccess {
        String getName();

        Object read();

        void write(Object object);
    }

    /**
     * Creates a {@link BeanMap} for the given bean
     *
     * @param bean The bean
     * @param <B>
     * @return The bean map
     */
    public static <B> BeanMap<B> of(B bean) {
        return new ReflectionBeanMap<>(bean);
    }
}
