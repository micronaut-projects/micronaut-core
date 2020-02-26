/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.CollectionUtils;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.*;
import java.util.stream.Collectors;

/**
 * An implementation of {@link BeanMap} that uses a backing {@link BeanIntrospection}.
 *
 * @param <T> The bean type
 * @since 1.1
 * @author graemerocher
 */
@Internal
final class BeanIntrospectionMap<T> implements BeanMap<T> {
    private final BeanIntrospection<T> beanIntrospection;
    private final T bean;

    /**
     * Default constructor.
     * @param beanIntrospection The introspection
     * @param bean The bean
     */
    BeanIntrospectionMap(BeanIntrospection<T> beanIntrospection, T bean) {
        this.beanIntrospection = beanIntrospection;
        this.bean = bean;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BeanIntrospectionMap<?> that = (BeanIntrospectionMap<?>) o;
        return beanIntrospection.equals(that.beanIntrospection) &&
                bean.equals(that.bean);
    }

    @Override
    public int hashCode() {
        return Objects.hash(beanIntrospection, bean);
    }

    @Override
    public @NonNull Class<T> getBeanType() {
        return beanIntrospection.getBeanType();
    }

    @Override
    public int size() {
        return beanIntrospection.getPropertyNames().length;
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        if (key == null) {
            return false;
        }
        return beanIntrospection.getProperty(key.toString()).isPresent();
    }

    @Override
    public boolean containsValue(Object value) {
        return values().contains(value);
    }

    @Override
    public Object get(Object key) {
        if (key == null) {
            return null;
        }
        return beanIntrospection.getProperty(key.toString()).map(bp -> bp.get(bean)).orElse(null);
    }

    @Override
    public Object put(String key, Object value) {
        if (key == null) {
            return null;
        }
        beanIntrospection.getProperty(key).ifPresent(bp -> {
            final Class<Object> propertyType = bp.getType();
            if (value != null && !propertyType.isInstance(value)) {
                Optional<?> converted = ConversionService.SHARED.convert(value, propertyType);
                converted.ifPresent(o -> bp.set(bean, o));
            } else {
                bp.set(bean, value);
            }
        });
        return null;
    }

    @Override
    public Object remove(Object key) {
        throw new UnsupportedOperationException("Removal is not supported");
    }

    @Override
    public void putAll(Map<? extends String, ?> m) {
        for (Entry<? extends String, ?> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("Removal is not supported");
    }

    @Override
    public Set<String> keySet() {
        return CollectionUtils.setOf(beanIntrospection.getPropertyNames());
    }

    @Override
    public Collection<Object> values() {
        return keySet().stream().map(this::get).collect(Collectors.toList());
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return keySet().stream().map(key -> new Entry<String, Object>() {
            @Override
            public String getKey() {
                return key;
            }

            @Override
            public Object getValue() {
                return get(key);
            }

            @Override
            public Object setValue(Object value) {
                return put(key, value);
            }
        }).collect(Collectors.toSet());
    }
}
