/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.jackson.convert;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.value.ConvertibleValues;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * Converts an {@link ObjectNode} to a {@link ConvertibleValues} instance.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class ObjectNodeToConvertibleValuesConverter implements TypeConverter<ObjectNode, ConvertibleValues> {

    private final ConversionService<?> conversionService;

    /**
     * @param conversionService To convert from JSON node to a {@link ConvertibleValues} instance
     */
    public ObjectNodeToConvertibleValuesConverter(ConversionService<?> conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Optional<ConvertibleValues> convert(ObjectNode object, Class<ConvertibleValues> targetType, ConversionContext context) {
        return Optional.of(new ObjectNodeConvertibleValues<>(object, conversionService));
    }
}
