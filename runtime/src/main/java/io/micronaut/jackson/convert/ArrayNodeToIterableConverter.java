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
package io.micronaut.jackson.convert;

import com.fasterxml.jackson.databind.node.ArrayNode;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.type.Argument;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author Graeme Rocher
 * @since 1.0
 * @deprecated Replaced by {@link JacksonConverterRegistrar}
 */
@Deprecated
public class ArrayNodeToIterableConverter implements TypeConverter<ArrayNode, Iterable> {

    private final ConversionService conversionService;

    /**
     * Create a new converter to convert from json to given type iteratively.
     *
     * @param conversionService Convert the given json node to the given target type.
     */
    public ArrayNodeToIterableConverter(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Optional<Iterable> convert(ArrayNode node, Class<Iterable> targetType, ConversionContext context) {
        Map<String, Argument<?>> typeVariables = context.getTypeVariables();
        Class elementType = typeVariables.isEmpty() ? Map.class : typeVariables.values().iterator().next().getType();
        List results = new ArrayList();
        node.elements().forEachRemaining(jsonNode -> {
            Optional converted = conversionService.convert(jsonNode, elementType, context);
            if (converted.isPresent()) {
                results.add(converted.get());
            }
        });
        return Optional.of(results);
    }
}
