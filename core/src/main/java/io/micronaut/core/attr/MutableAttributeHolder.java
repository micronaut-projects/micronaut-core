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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.util.StringUtils;

import java.util.Optional;

/**
 * An interface for types that support mutating attributes.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface MutableAttributeHolder extends AttributeHolder {
    /**
     * Overrides the default {@link AttributeHolder#getAttributes()} method to return a mutable object.
     *
     * @return The mutable attributes
     */
    @Override
    @NonNull
    MutableConvertibleValues<Object> getAttributes();

    /**
     * Sets an attribute on the message.
     *
     * @param name  The name of the attribute
     * @param value The value of the attribute
     * @return This message
     */
    default @NonNull MutableAttributeHolder setAttribute(@NonNull CharSequence name, @Nullable Object value) {
        if (StringUtils.isNotEmpty(name)) {
            if (value == null) {
                getAttributes().remove(name.toString());
            } else {
                getAttributes().put(name.toString(), value);
            }
        }
        return this;
    }

    /**
     * Remove an attribute. Returning the old value if it is present.
     *
     * @param name The name of the attribute
     * @param type The required type
     * @param <T>  type Generic
     * @return An {@link Optional} value
     */
    default @NonNull <T> Optional<T> removeAttribute(@NonNull CharSequence name, @NonNull Class<T> type) {
        if (StringUtils.isNotEmpty(name)) {
            String key = name.toString();
            Optional<T> value = getAttribute(key, type);
            value.ifPresent(o -> getAttributes().remove(key));
            return value;
        }
        return Optional.empty();
    }
}
