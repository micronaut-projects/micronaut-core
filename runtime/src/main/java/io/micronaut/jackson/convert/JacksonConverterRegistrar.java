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
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.bind.BeanPropertyBinder;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;

import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.*;
import java.util.function.BiFunction;

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
    private final Provider<BeanPropertyBinder> beanPropertyBinder;

    /**
     * Default constructor.
     * @param objectMapper The object mapper provider
     * @param beanPropertyBinder The bean property binder provider
     * @param conversionService The conversion service
     */
    protected JacksonConverterRegistrar(
            Provider<ObjectMapper> objectMapper,
            Provider<BeanPropertyBinder> beanPropertyBinder,
            ConversionService<?> conversionService) {
        this.objectMapper = objectMapper;
        this.conversionService = conversionService;
        this.beanPropertyBinder = beanPropertyBinder;
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
                Map.class,
                Object.class,
                mapToObjectConverter()
        );
        conversionService.addConverter(
                CharSequence.class,
                PropertyNamingStrategy.class,
                (TypeConverter<CharSequence, PropertyNamingStrategy>) (charSequence, targetType, context) -> {
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
     * @return The map to object converter
     */
    protected TypeConverter<Map, Object> mapToObjectConverter() {
        return (map, targetType, context) -> {
            final BiFunction<Object, Map<?, ?>, Object> propertiesBinderFunction = (object, properties) -> {
                Map bindMap = new LinkedHashMap(properties.size());
                for (Map.Entry entry : properties.entrySet()) {
                    Object key = entry.getKey();
                    bindMap.put(NameUtils.decapitalize(NameUtils.dehyphenate(key.toString())), entry.getValue());
                }
                return beanPropertyBinder.get().bind(object, bindMap);
            };

            Optional<Object> instance = InstantiationUtils.tryInstantiate(targetType, map, context)
                    .map(object -> propertiesBinderFunction.apply(object, map));

            if (instance.isPresent()) {
                return instance;
            } else if (targetType.isInstance(map)) {
                return Optional.of(map);
            } else {
                return InstantiationUtils
                        .tryInstantiate(targetType)
                        .map(object -> propertiesBinderFunction.apply(object, map));
            }
        };
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
                    Object result = objectMapper.get().treeToValue(node, targetType);
                    return Optional.ofNullable(result);
                }
            } catch (JsonProcessingException e) {
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
