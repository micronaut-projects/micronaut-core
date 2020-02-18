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
package io.micronaut.http;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import java.util.Optional;

/**
 * Converts a string to a {@link MediaType}.
 *
 * @author James Kleeh
 * @since 1.0
 * @deprecated Replaced by {@link io.micronaut.http.converters.HttpConverterRegistrar}
 */
@Deprecated
public class MediaTypeConverter implements TypeConverter<CharSequence, MediaType> {

    @Override
    public Optional<MediaType> convert(CharSequence object, Class<MediaType> targetType, ConversionContext context) {
        try {
            return Optional.of(MediaType.of(object));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
