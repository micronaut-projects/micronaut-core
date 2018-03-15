/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.micronaut.core.beans;

import io.micronaut.core.naming.Named;
import io.micronaut.core.reflect.ReflectionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * An interface that provides basic bean information. Designed as a simpler replacement for
 * {@link java.beans.PropertyDescriptor}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class PropertyDescriptor implements Named {
    private final String propertyName;
    private final Method getter;
    private final Method setter;

    PropertyDescriptor(String propertyName, Method getter, Method setter) {
        this.propertyName = propertyName;
        this.getter = getter;
        this.setter = setter;
        if(this.getter != null) {
            this.getter.setAccessible(true);
        }
        if(this.setter != null) {
            this.setter.setAccessible(true);
        }
    }

    @Override
    public String getName() {
        return propertyName;
    }

    public Class<?> getBeanClass() {
        if(getter != null) {
            return getter.getDeclaringClass();
        }
        else {
            return setter.getDeclaringClass();
        }
    }

    public Class<?> getType() {
        if(getter != null) {
            return getter.getReturnType();
        }
        else {
            return setter.getParameterTypes()[0];
        }
    }

    public Method getReadMethod() {
        return getter;
    }

    public Method getWriteMethod() {
        return setter;
    }
}
