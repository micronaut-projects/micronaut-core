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
package org.particleframework.core.beans;

import org.particleframework.core.reflect.ReflectionUtils;

import java.lang.reflect.Method;

/**
 * Simple reflection based BeanMap implementation
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class ReflectionBeanMap<T> extends BeanMap<T> {

    @SuppressWarnings("unchecked")
    ReflectionBeanMap(T bean) {
        super((Class<T>) bean.getClass(), buildPropertyReaders(bean));
    }

    private static <T> PropertyAccess[] buildPropertyReaders(T bean) {
        BeanInfo beanInfo = Introspector.getBeanInfo(bean.getClass());
        PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
        PropertyAccess[] propertyAccesses = new PropertyAccess[propertyDescriptors.length];
        for (int i = 0; i < propertyDescriptors.length; i++) {
            final PropertyDescriptor propertyDescriptor = propertyDescriptors[i];
            final Method readMethod = propertyDescriptor.getReadMethod();
            if(readMethod != null) {
                readMethod.setAccessible(true);
            }
            final Method writeMethod = propertyDescriptor.getWriteMethod();
            if(writeMethod != null) {
                writeMethod.setAccessible(true);
            }
            propertyAccesses[i] = new PropertyAccess() {
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
            };
        }
        return propertyAccesses;
    }
}
