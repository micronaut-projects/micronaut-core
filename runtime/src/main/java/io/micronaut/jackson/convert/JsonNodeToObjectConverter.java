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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ContainerNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.type.Argument;
import io.micronaut.jackson.JacksonConfiguration;
import java.io.IOException;
import javax.inject.Provider;
import java.util.Optional;

/**
 * A {@link TypeConverter} that leverages Jackson {@link ObjectMapper} to convert from {@link JsonNode} instances to
 * objects.
 *
 * @author Graeme Rocher
 * @since 1.0
 * @deprecated Replaced by {@link JacksonConverterRegistrar}
 */
@Deprecated
public class JsonNodeToObjectConverter implements TypeConverter<JsonNode, Object> {

    private final Provider<ObjectMapper> objectMapper;

    /**
     * @param objectMapper To read/write JSON
     */
    public JsonNodeToObjectConverter(Provider<ObjectMapper> objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<Object> convert(JsonNode node, Class<Object> targetType, ConversionContext context) {
        try {
            if (CharSequence.class.isAssignableFrom(targetType) && node instanceof ObjectNode) {
                return Optional.of(node.toString());
            } else {
                Argument<Object> argument = null;
                if (node instanceof ContainerNode && context instanceof ArgumentConversionContext && targetType.getTypeParameters().length != 0) {
                    argument = ((ArgumentConversionContext<Object>) context).getArgument();
                }
                Object result;
                if (argument != null) {
                    ObjectMapper om = this.objectMapper.get();
                    JsonParser jsonParser = om.treeAsTokens(node);
                    TypeFactory typeFactory = om.getTypeFactory();
                    JavaType javaType = JacksonConfiguration.constructType(argument, typeFactory);
                    result = om.readValue(jsonParser, javaType);
                } else {
                    result = this.objectMapper.get().treeToValue(node, targetType);
                }
                return Optional.ofNullable(result);
            }
        } catch (IOException e) {
            context.reject(e);
            return Optional.empty();
        }
    }
}
