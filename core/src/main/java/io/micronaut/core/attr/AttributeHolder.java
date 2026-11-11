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
package io.micronaut.core.attr;

import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.StringUtils;
import java.util.Optional;

/**
 * An interface for objects that have attributes.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface AttributeHolder {

    /**
     * <p>A {@link io.micronaut.core.convert.value.MutableConvertibleValues} of the attributes for object.</p>
     *
     * @return The attributes of the object
     */
 ConvertibleValues<Object> getAttributes();

    /**
     * Obtain the value of an attribute on the HTTP method.
     *
     * @param name The name of the attribute
     * @return An {@link Optional} value
     */
    default Optional<Object> getAttribute(CharSequence name) {
        if (StringUtils.isNotEmpty(name)) {
            return getAttributes().get(name.toString(), Object.class);
        }
        return Optional.empty();
    }

    /**
     * Obtain the value of an attribute on the HTTP method. A stored value that is already
     * an instance of the required type is returned as is, without conversion.
     *
     * @param name The name of the attribute
     * @param type The required type
     * @param <T>  type Generic
     * @return An {@link Optional} value
     */
    @SuppressWarnings("unchecked")
    default <T> Optional<T> getAttribute(CharSequence name, Class<T> type) {
        if (StringUtils.isNotEmpty(name)) {
            String key = name.toString();
            ConvertibleValues<Object> attributes = getAttributes();
            Object value = attributes.getValue(key);
            if (value == null) {
                return Optional.empty();
            }
            if (isInstance(value, type)) {
                return Optional.of((T) value);
            }
            return attributes.get(key, type);
        }
        return Optional.empty();
    }

    /**
     * A value that is already an instance of the required type is used unchanged. A raw
     * type has no type arguments to convert, so a converted copy (of a map or a collection,
     * for example) would only lose changes made to the returned value.
     *
     * @param value The stored value
     * @param type  The required type
     * @return Whether the value needs no conversion
     */
    private static boolean isInstance(Object value, Class<?> type) {
        return (type.isPrimitive() ? ReflectionUtils.getWrapperType(type) : type).isInstance(value);
    }
}
