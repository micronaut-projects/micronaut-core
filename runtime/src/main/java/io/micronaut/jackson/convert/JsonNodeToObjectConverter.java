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
package io.micronaut.jackson.convert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.Optional;

/**
 * A {@link TypeConverter} that leverages Jackson {@link ObjectMapper} to convert from {@link JsonNode} instances to
 * objects.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class JsonNodeToObjectConverter implements TypeConverter<JsonNode, Object> {

    private final Provider<ObjectMapper> objectMapper;

    /**
     * @param objectMapper To read/write JSON
     */
    @Inject
    public JsonNodeToObjectConverter(Provider<ObjectMapper> objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<Object> convert(JsonNode node, Class<Object> targetType, ConversionContext context) {
        try {
            if (CharSequence.class.isAssignableFrom(targetType) && node instanceof ObjectNode) {
                return Optional.of(node.toString());
            } else {
                Object result = objectMapper.get().treeToValue(node, targetType);
                return Optional.ofNullable(result);
            }
        } catch (JsonProcessingException e) {
            context.reject(e);
            return Optional.empty();
        }
    }
}
