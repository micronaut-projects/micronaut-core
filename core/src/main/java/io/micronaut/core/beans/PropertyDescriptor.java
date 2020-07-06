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

import io.micronaut.core.naming.Named;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.lang.reflect.Method;

/**
 * An interface that provides basic bean information. Designed as a simpler replacement for
 * {@link java.beans.PropertyDescriptor}.
 *
 * @author Graeme Rocher
 * @since 1.0
 * @deprecated Use {@link BeanProperty} instead
 */
@Deprecated
public class PropertyDescriptor implements Named {
    private final String propertyName;
    private final Method getter;
    private final Method setter;

    /**
     * Constructor.
     * @param propertyName propertyName
     * @param getter getter
     * @param setter setter
     */
    PropertyDescriptor(String propertyName, Method getter, Method setter) {
        this.propertyName = propertyName;
        this.getter = getter;
        this.setter = setter;
        if (this.getter != null) {
            this.getter.setAccessible(true);
        }
        if (this.setter != null) {
            this.setter.setAccessible(true);
        }
    }

    /**
     * @return The property name
     */
    @Override
    public String getName() {
        return propertyName;
    }

    /**
     * @return The bean type
     */
    public Class<?> getBeanClass() {
        if (getter != null) {
            return getter.getDeclaringClass();
        }
        return setter.getDeclaringClass();
    }

    /**
     * @return The property type
     */
    public Class<?> getType() {
        if (getter != null) {
            return getter.getReturnType();
        }
        return setter.getParameterTypes()[0];
    }

    /**
     * @return The read method
     */
    public @Nullable Method getReadMethod() {
        return getter;
    }

    /**
     * @return The write method
     */
    public @Nullable Method getWriteMethod() {
        return setter;
    }
}
