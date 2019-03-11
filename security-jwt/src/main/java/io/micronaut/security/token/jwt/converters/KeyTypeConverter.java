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
package io.micronaut.security.token.jwt.converters;

import com.nimbusds.jose.jwk.KeyType;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * Converts a string to a {@link KeyType}.
 *
 * @author Sergio del Amo
 * @since 1.1.0
 */
@Singleton
public class KeyTypeConverter implements TypeConverter<CharSequence, KeyType> {

    /**
     *
     * @param object     e.g. EC
     * @param targetType The target type being converted to {@link com.nimbusds.jose.jwk.KeyType}
     * @param context    The {@link io.micronaut.core.convert.ConversionContext}
     * @return An optional {@link com.nimbusds.jose.jwk.KeyType}
     */
    @Override
    public Optional<KeyType> convert(CharSequence object, Class<KeyType> targetType, ConversionContext context) {
        if (object == null) {
            return Optional.empty();
        }
        String value = object.toString();
        KeyType keyType = KeyType.parse(value);
        return Optional.of(keyType);
    }
}
