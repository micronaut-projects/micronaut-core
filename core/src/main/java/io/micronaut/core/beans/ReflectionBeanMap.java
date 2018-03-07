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

import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.CollectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Simple reflection based BeanMap implementation
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class ReflectionBeanMap<T> extends BeanMap<T> {
    private static final Set<String> EXCLUDED_PROPERTIES = CollectionUtils.setOf("class", "metaClass");

    @SuppressWarnings("unchecked")
    ReflectionBeanMap(T bean) {
        super((Class<T>) bean.getClass(), buildPropertyReaders(bean));
    }

    private static <T> PropertyAccess[] buildPropertyReaders(T bean) {
        BeanInfo beanInfo = Introspector.getBeanInfo(bean.getClass());
        PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
        List<PropertyAccess> propertyAccesses = new ArrayList<>(propertyDescriptors.length);
        for (int i = 0; i < propertyDescriptors.length; i++) {
            final PropertyDescriptor propertyDescriptor = propertyDescriptors[i];
            String name = propertyDescriptor.getName();
            if(EXCLUDED_PROPERTIES.contains(name)) continue;

            final Method readMethod = propertyDescriptor.getReadMethod();
            final Method writeMethod = propertyDescriptor.getWriteMethod();
            propertyAccesses.add( new PropertyAccess() {
                @Override
                public String getName() {
                    return propertyDescriptor.getName();
                }

                @Override
                public Object read() {
                    if(readMethod != null) {
                        return ReflectionUtils.invokeMethod(bean, readMethod);
                    }
                    return null;
                }

                @Override
                public void write(Object object) {
                    if(writeMethod != null) {
                        ReflectionUtils.invokeMethod(bean, writeMethod, object);
                    }
                }
            });
        }
        return propertyAccesses.toArray(new PropertyAccess[propertyAccesses.size()]);
    }
}
