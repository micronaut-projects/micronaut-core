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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import javax.inject.Provider;
import java.util.Optional;

/**
 * Converts {@link ArrayNode} instances to arrays.
 *
 * @author Graeme Rocher
 * @since 1.0
 * @deprecated Replaced by {@link JacksonConverterRegistrar}
 */
@Deprecated
public class ArrayNodeToArrayConverter implements TypeConverter<ArrayNode, Object[]> {

    private final Provider<ObjectMapper> objectMapper;

    /**
     * Create a converter to convert form ArrayNode to Array.
     *
     * @param objectMapper To convert from Json to Array
     */
    public ArrayNodeToArrayConverter(Provider<ObjectMapper> objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<Object[]> convert(ArrayNode node, Class<Object[]> targetType, ConversionContext context) {
        try {
            Object[] result = objectMapper.get().treeToValue(node, targetType);
            return Optional.of(result);
        } catch (JsonProcessingException e) {
            context.reject(e);
            return Optional.empty();
        }
    }
}
