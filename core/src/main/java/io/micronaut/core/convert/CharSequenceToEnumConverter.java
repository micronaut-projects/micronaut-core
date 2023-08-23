/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.core.convert;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.StringUtils;

import java.util.Arrays;
import java.util.Optional;

/**
 * The converter that converts {@link CharSequence} to an enum.
 *
 * @param <T> T The enum type
 * @author Denis Stepanov
 * @since 4.2.0
 */
@Internal
public final class CharSequenceToEnumConverter<T extends Enum<T>> implements TypeConverter<CharSequence, T> {

    @Override
    public Optional<T> convert(CharSequence charSequence, Class<T> targetType, ConversionContext context) {
        if (StringUtils.isEmpty(charSequence)) {
            return Optional.empty();
        }
        String stringValue = charSequence.toString();
        try {
            T val = Enum.valueOf(targetType, stringValue);
            return Optional.of(val);
        } catch (IllegalArgumentException e) {
            try {
                T val = Enum.valueOf(targetType, NameUtils.environmentName(stringValue));
                return Optional.of(val);
            } catch (Exception e1) {
                Optional<T> valOpt = Arrays.stream(targetType.getEnumConstants())
                        .filter(val -> val.toString().equals(stringValue))
                        .findFirst();
                if (valOpt.isPresent()) {
                    return valOpt;
                }
                context.reject(charSequence, e);
                return Optional.empty();
            }
        }
    }
}
