/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.core.attr;

import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.util.StringUtils;

import edu.umd.cs.findbugs.annotations.NonNull;
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
    @NonNull ConvertibleValues<Object> getAttributes();

    /**
     * Obtain the value of an attribute on the HTTP method.
     *
     * @param name The name of the attribute
     * @return An {@link Optional} value
     */
    default @NonNull Optional<Object> getAttribute(CharSequence name) {
        if (StringUtils.isNotEmpty(name)) {
            return getAttributes().get(name.toString(), Object.class);
        }
        return Optional.empty();
    }

    /**
     * Obtain the value of an attribute on the HTTP method.
     *
     * @param name The name of the attribute
     * @param type The required type
     * @param <T>  type Generic
     * @return An {@link Optional} value
     */
    default @NonNull <T> Optional<T> getAttribute(CharSequence name, Class<T> type) {
        if (StringUtils.isNotEmpty(name)) {
            return getAttributes().get(name.toString(), type);
        }
        return Optional.empty();
    }
}
