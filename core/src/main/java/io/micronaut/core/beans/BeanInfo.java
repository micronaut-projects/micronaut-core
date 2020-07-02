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

import java.util.Map;

/**
 * An interface that provides basic bean information. Designed as a simpler replacement for {@link java.beans.BeanInfo}.
 *
 * @param <T> type Generic
 * @author Graeme Rocher
 * @since 1.0
 * @deprecated Use {@link BeanIntrospection} instead
 */
@Deprecated
public interface BeanInfo<T> {

    /**
     * @return The bean class
     */
    Class<T> getBeanClass();

    /**
     * The properties of the bean.
     *
     * @return The properties of the bean as a map where the key is the property name
     */
    Map<String, PropertyDescriptor> getPropertyDescriptors();
}
