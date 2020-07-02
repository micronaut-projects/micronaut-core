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
package io.micronaut.core.beans;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ReflectionUtils;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Simple reflection based BeanMap implementation.
 * @param <T> type Generic
 * @author Graeme Rocher
 * @since 1.0
 * @deprecated Replaced by {@link BeanIntrospectionMap}
 */
@Internal
@Deprecated
class ReflectionBeanMap<T> implements BeanMap<T> {

    private final BeanInfo<T> beanInfo;
    private final Map<String, PropertyDescriptor> propertyDescriptors;
    private final T bean;

    /**
     * Constructor.
     * @param bean bean
     */
    @SuppressWarnings("unchecked")
    ReflectionBeanMap(T bean) {
        Objects.requireNonNull(bean, "Bean cannot be null");
        this.bean = bean;
        this.beanInfo = (BeanInfo<T>) Introspector.getBeanInfo(bean.getClass());
        this.propertyDescriptors = beanInfo.getPropertyDescriptors();
    }

    @Override
    public @NonNull Class<T> getBeanType() {
        return beanInfo.getBeanClass();
    }

    @Override
    public int size() {
        return propertyDescriptors.size();
    }

    @Override
    public boolean isEmpty() {
        return propertyDescriptors.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return propertyDescriptors.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return values().contains(value);
    }

    @Override
    public Object get(Object key) {
        PropertyDescriptor propertyDescriptor = propertyDescriptors.get(key);
        if (propertyDescriptor != null) {
            Method readMethod = propertyDescriptor.getReadMethod();
            if (readMethod != null) {
                return ReflectionUtils.invokeMethod(bean, readMethod);
            }
        }
        return null;
    }

    @Override
    public Object put(String key, Object value) {
        PropertyDescriptor propertyDescriptor = propertyDescriptors.get(key);
        if (propertyDescriptor != null) {
            Method writeMethod = propertyDescriptor.getWriteMethod();
            if (writeMethod != null) {
                Class<?> targetType = writeMethod.getParameterTypes()[0];
                Optional<?> converted = ConversionService.SHARED.convert(value, targetType);
                if (converted.isPresent()) {
                    return ReflectionUtils.invokeMethod(bean, writeMethod, converted.get());
                }
            }
        }
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
        return propertyDescriptors.keySet();
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
