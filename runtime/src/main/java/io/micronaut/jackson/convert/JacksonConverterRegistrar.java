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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ContainerNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.micronaut.core.convert.*;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.jackson.JacksonConfiguration;

import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.*;
import java.io.IOException;

/**
 * Converter registrar for Jackson.
 *
 * @author graemerocher
 * @since 2.0
 */
@Singleton
public class JacksonConverterRegistrar implements TypeConverterRegistrar {

    private final Provider<ObjectMapper> objectMapper;
    private final ConversionService<?> conversionService;

    /**
     * Default constructor.
     * @param objectMapper The object mapper provider
     * @param conversionService The conversion service
     */
    protected JacksonConverterRegistrar(
            Provider<ObjectMapper> objectMapper,
            ConversionService<?> conversionService) {
        this.objectMapper = objectMapper;
        this.conversionService = conversionService;
    }

    @Override
    public void register(ConversionService<?> conversionService) {
        conversionService.addConverter(
                ArrayNode.class,
                Object[].class,
                arrayNodeToObjectConverter()
        );
        conversionService.addConverter(
                ArrayNode.class,
                Iterable.class,
                arrayNodeToIterableConverter()
        );
        conversionService.addConverter(
                JsonNode.class,
                Object.class,
                jsonNodeToObjectConverter()
        );
        conversionService.addConverter(
                ObjectNode.class,
                ConvertibleValues.class,
                objectNodeToConvertibleValuesConverter()
        );
        conversionService.addConverter(
                Object.class,
                JsonNode.class,
                objectToJsonNodeConverter()
        );
        conversionService.addConverter(
                CharSequence.class,
                PropertyNamingStrategy.class,
                (charSequence, targetType, context) -> {
                    PropertyNamingStrategy propertyNamingStrategy = null;

                    if (charSequence != null) {
                        String stringValue = NameUtils.environmentName(charSequence.toString());

                        if (StringUtils.isNotEmpty(stringValue)) {
                            switch (stringValue) {
                                case "SNAKE_CASE":
                                    propertyNamingStrategy = PropertyNamingStrategy.SNAKE_CASE;
                                    break;
                                case "UPPER_CAMEL_CASE":
                                    propertyNamingStrategy = PropertyNamingStrategy.UPPER_CAMEL_CASE;
                                    break;
                                case "LOWER_CASE":
                                    propertyNamingStrategy = PropertyNamingStrategy.LOWER_CASE;
                                    break;
                                case "KEBAB_CASE":
                                    propertyNamingStrategy = PropertyNamingStrategy.KEBAB_CASE;
                                    break;
                                case "LOWER_CAMEL_CASE":
                                    propertyNamingStrategy = PropertyNamingStrategy.LOWER_CAMEL_CASE;
                                    break;
                                default:
                                    break;
                            }
                        }
                    }

                    if (propertyNamingStrategy == null) {
                        context.reject(charSequence, new IllegalArgumentException(String.format("Unable to convert '%s' to a com.fasterxml.jackson.databind.PropertyNamingStrategy", charSequence)));
                    }

                    return Optional.ofNullable(propertyNamingStrategy);
                }
        );
    }

    /**
     * @return A converter that converts an object to a json node
     */
    protected TypeConverter<Object, JsonNode> objectToJsonNodeConverter() {
        return (object, targetType, context) -> {
            try {
                return Optional.of(objectMapper.get().valueToTree(object));
            } catch (IllegalArgumentException e) {
                context.reject(e);
                return Optional.empty();
            }
        };
    }

    /**
     * @return A converter that converts object nodes to convertible values
     */
    protected TypeConverter<ObjectNode, ConvertibleValues> objectNodeToConvertibleValuesConverter() {
        return (object, targetType, context) -> Optional.of(new ObjectNodeConvertibleValues<>(object, conversionService));
    }

    /**
     * @return The JSON node to object converter
     */
    protected TypeConverter<JsonNode, Object> jsonNodeToObjectConverter() {
        return (node, targetType, context) -> {
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
        };
    }

    /**
     * @return Converts array nodes to iterables.
     */
    protected TypeConverter<ArrayNode, Iterable> arrayNodeToIterableConverter() {
        return (node, targetType, context) -> {
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
        };
    }

    /**
     * @return Converts array nodes to objects.
     */
    protected TypeConverter<ArrayNode, Object[]> arrayNodeToObjectConverter() {
        return (node, targetType, context) -> {
            try {
                Object[] result = objectMapper.get().treeToValue(node, targetType);
                return Optional.of(result);
            } catch (JsonProcessingException e) {
                context.reject(e);
                return Optional.empty();
            }
        };
    }
}
