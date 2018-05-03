/*
 * Copyright 2017-2018 original authors
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

import com.nimbusds.jose.JWSAlgorithm;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * Converts a string to a {@link JWSAlgorithm}.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
public class JWSAlgorithmConverter  implements TypeConverter<CharSequence, JWSAlgorithm> {

    /**
     *
     * @param object     e.g. HS256
     * @param targetType The target type being converted to {@link JWSAlgorithm}
     * @param context    The {@link ConversionContext}
     * @return  {@link JWSAlgorithm}
     */
    @Override
    public Optional<JWSAlgorithm> convert(CharSequence object, Class<JWSAlgorithm> targetType, ConversionContext context) {
        if (object == null) {
            return Optional.empty();
        }
        String value = object.toString();
        JWSAlgorithm algorithm = JWSAlgorithm.parse(value);
        if (algorithm.getRequirement() != null) {
            return Optional.of(algorithm);
        } else {
            return Optional.empty();
        }
    }
}
