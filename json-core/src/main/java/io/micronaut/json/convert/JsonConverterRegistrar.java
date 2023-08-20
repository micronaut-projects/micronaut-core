/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.json.convert;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.bind.BeanPropertyBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.JsonSyntaxException;
import io.micronaut.json.tree.JsonNode;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Converter registrar for json.
 *
 * @author Jonas Konrad
 * @since 3.1
 */
@Experimental
@Prototype
public final class JsonConverterRegistrar implements TypeConverterRegistrar {
    private final BeanProvider<JsonMapper> objectCodecProvider;
    private JsonMapper objectCodec;
    private final ConversionService conversionService;
    private final BeanProvider<BeanPropertyBinder> beanPropertyBinder;

    @Inject
    public JsonConverterRegistrar(
            BeanProvider<JsonMapper> objectCodec,
            ConversionService conversionService,
            BeanProvider<BeanPropertyBinder> beanPropertyBinder
    ) {
        this.objectCodecProvider = objectCodec;
        this.conversionService = conversionService;
        this.beanPropertyBinder = beanPropertyBinder;
    }

    private JsonMapper objectCodec() {
        // JsonMapper is immutable, so we don't need safe publication here
        JsonMapper objectCodec = this.objectCodec;
        if (objectCodec == null) {
            this.objectCodec = objectCodec = objectCodecProvider.get();
        }
        return objectCodec;
    }

    @Override
    public void register(MutableConversionService conversionService) {
        conversionService.addConverter(
                JsonNode.class,
                ConvertibleValues.class,
                objectNodeToConvertibleValuesConverter()
        );
        conversionService.addConverter(
                LazyJsonNode.class,
                ConvertibleValues.class,
                unparsedNodeToConvertibleValuesConverter()
        );
        conversionService.addConverter(
                JsonNode.class,
                Object.class,
                jsonNodeToObjectConverter()
        );
        conversionService.addConverter(
                LazyJsonNode.class,
                Object.class,
                unparsedJsonNodeToObjectConverter()
        );
        // need to register the Object[] conversions explicitly because there is also an Object->Object[] converter
        conversionService.addConverter(
            JsonNode.class,
            Object[].class,
            (TypeConverter) jsonNodeToObjectConverter()
        );
        conversionService.addConverter(
                LazyJsonNode.class,
                Object[].class,
                (TypeConverter) unparsedJsonNodeToObjectConverter()
        );
        conversionService.addConverter(
                Map.class,
                Object.class,
                mapToObjectConverter()
        );
        conversionService.addConverter(
                Object.class,
                JsonNode.class,
                objectToJsonNodeConverter()
        );
    }

    /**
     * @return A converter that converts object nodes to convertible values
     */
    private TypeConverter<JsonNode, ConvertibleValues> objectNodeToConvertibleValuesConverter() {
        return (object, targetType, context) -> {
            if (object.isObject()) {
                return Optional.of(new JsonNodeConvertibleValues<>(object, conversionService));
            } else {
                // ConvertibleValues only works for objects
                return Optional.empty();
            }
        };
    }

    /**
     * @return A converter that converts object nodes to convertible values
     */
    private TypeConverter<LazyJsonNode, ConvertibleValues> unparsedNodeToConvertibleValuesConverter() {
        return (node, targetType, context) -> {
            // this is a bit convoluted, only release if we can convert or there is an error
            try {
                if (!node.isObject()) {
                    // ConvertibleValues only works for objects
                    return Optional.empty();
                }
            } catch (JsonSyntaxException e) {
                node.tryRelease();
                context.reject(e);
                return Optional.empty();
            }
            try {
                return Optional.of(new JsonNodeConvertibleValues<>(node.toJsonNode(objectCodec()), conversionService));
            } catch (IOException e) {
                context.reject(e);
                return Optional.empty();
            } finally {
                node.tryRelease();
            }
        };
    }

    /**
     * @return The map to object converter
     */
    protected TypeConverter<Map, Object> mapToObjectConverter() {
        return (map, targetType, context) -> {
            ArgumentConversionContext<Object> conversionContext;
            if (context instanceof ArgumentConversionContext argumentConversionContext) {
                conversionContext = argumentConversionContext;
            } else {
                conversionContext = ConversionContext.of(targetType);
            }
            ArgumentBinder binder = this.beanPropertyBinder.get();
            ArgumentBinder.BindingResult result = binder.bind(conversionContext, correctKeys(map));
            return result.getValue();
        };
    }

    private Map correctKeys(Map<?, ?> map) {
        Map mapWithExtraProps = new LinkedHashMap(map.size());
        for (Map.Entry entry : map.entrySet()) {
            Object key = entry.getKey();
            Object value = correctKeys(entry.getValue());
            mapWithExtraProps.put(NameUtils.decapitalize(NameUtils.dehyphenate(key.toString())), value);
        }
        return mapWithExtraProps;
    }

    private List correctKeys(List list) {
        List newList = new ArrayList(list.size());
        for (Object o : list) {
            newList.add(correctKeys(o));
        }
        return newList;
    }

    private Object correctKeys(Object o) {
        if (o instanceof List list) {
            return correctKeys(list);
        } else if (o instanceof Map map) {
            return correctKeys(map);
        }
        return o;
    }

    /**
     * @return A converter that converts an object to a json node
     */
    private TypeConverter<Object, JsonNode> objectToJsonNodeConverter() {
        return (object, targetType, context) -> {
            try {
                return Optional.of(objectCodec().writeValueToTree(object));
            } catch (IllegalArgumentException | IOException e) {
                context.reject(e);
                return Optional.empty();
            }
        };
    }

    @NonNull
    private static Argument<?> argument(Class<Object> targetType, ConversionContext context) {
        Argument<?> argument = null;
        if (context instanceof ArgumentConversionContext conversionContext) {
            argument = conversionContext.getArgument();
            if (targetType != argument.getType()) {
                argument = null;
            }
        }
        if (argument == null) {
            argument = Argument.of(targetType);
        }
        return argument;
    }

    /**
     * @return The JSON node to object converter
     */
    private TypeConverter<JsonNode, Object> jsonNodeToObjectConverter() {
        return (node, targetType, context) -> {
            try {
                if (CharSequence.class.isAssignableFrom(targetType) && node.isObject()) {
                    return Optional.of(new String(objectCodec().writeValueAsBytes(node), StandardCharsets.UTF_8));
                } else {
                    return Optional.ofNullable(objectCodec().readValueFromTree(node, argument(targetType, context)));
                }
            } catch (IOException e) {
                context.reject(e);
                return Optional.empty();
            }
        };
    }

    /**
     * @return The JSON node to object converter
     */
    private TypeConverter<LazyJsonNode, Object> unparsedJsonNodeToObjectConverter() {
        return (node, targetType, context) -> {
            try {
                JsonMapper mapper = objectCodec();
                if (CharSequence.class.isAssignableFrom(targetType) && node.isObject()) {
                    // parse once to JsonNode to ensure validity & sanitize the input
                    byte[] sanitized = mapper.writeValueAsBytes(node.toJsonNode(mapper));
                    return Optional.of(new String(sanitized, StandardCharsets.UTF_8));
                } else {
                    return Optional.ofNullable(node.parse(mapper, argument(targetType, context)));
                }
            } catch (IOException e) {
                context.reject(e);
                return Optional.empty();
            } finally {
                node.tryRelease();
            }
        };
    }
}
