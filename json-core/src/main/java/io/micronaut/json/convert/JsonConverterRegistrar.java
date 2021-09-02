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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.bind.BeanPropertyBinder;
import io.micronaut.core.convert.*;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonArray;
import io.micronaut.json.tree.JsonNode;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.*;

/**
 * Converter registrar for json.
 *
 * @author Jonas Konrad
 * @since 3.1
 */
@Singleton
public final class JsonConverterRegistrar implements TypeConverterRegistrar {
    private final BeanProvider<JsonMapper> objectCodec;
    private final ConversionService<?> conversionService;
    private final BeanProvider<BeanPropertyBinder> beanPropertyBinder;

    @Inject
    public JsonConverterRegistrar(
            BeanProvider<JsonMapper> objectCodec,
            ConversionService<?> conversionService,
            BeanProvider<BeanPropertyBinder> beanPropertyBinder
    ) {
        this.objectCodec = objectCodec;
        this.conversionService = conversionService;
        this.beanPropertyBinder = beanPropertyBinder;
    }

    @Override
    public void register(ConversionService<?> conversionService) {
        conversionService.addConverter(
                JsonArray.class,
                Object[].class,
                arrayNodeToObjectConverter()
        );
        conversionService.addConverter(
                JsonArray.class,
                Iterable.class,
                arrayNodeToIterableConverter()
        );
        conversionService.addConverter(
                JsonNode.class,
                ConvertibleValues.class,
                objectNodeToConvertibleValuesConverter()
        );
        conversionService.addConverter(
                JsonNode.class,
                Object.class,
                jsonNodeToObjectConverter()
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
    @Internal
    public TypeConverter<JsonNode, ConvertibleValues> objectNodeToConvertibleValuesConverter() {
        return (object, targetType, context) -> Optional.of(new JsonNodeConvertibleValues<>(object, conversionService));
    }

    /**
     * @return Converts array nodes to iterables.
     */
    public TypeConverter<JsonArray, Iterable> arrayNodeToIterableConverter() {
        return (node, targetType, context) -> {
            Map<String, Argument<?>> typeVariables = context.getTypeVariables();
            Class elementType = typeVariables.isEmpty() ? Map.class : typeVariables.values().iterator().next().getType();
            List results = new ArrayList();
            for (int i = 0; i < node.size(); i++) {
                Optional converted = conversionService.convert(node.get(i), elementType, context);
                if (converted.isPresent()) {
                    results.add(converted.get());
                }
            }
            return Optional.of(results);
        };
    }

    /**
     * @return Converts array nodes to objects.
     */
    @Internal
    public TypeConverter<JsonArray, Object[]> arrayNodeToObjectConverter() {
        return (node, targetType, context) -> {
            try {
                JsonMapper om = this.objectCodec.get();
                Object[] result = om.readValueFromTree(node, targetType);
                return Optional.of(result);
            } catch (IOException e) {
                context.reject(e);
                return Optional.empty();
            }
        };
    }

    /**
     * @return The map to object converter
     */
    protected TypeConverter<Map, Object> mapToObjectConverter() {
        return (map, targetType, context) -> {
            ArgumentConversionContext<Object> conversionContext;
            if (context instanceof ArgumentConversionContext) {
                conversionContext = (ArgumentConversionContext<Object>) context;
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
        if (o instanceof List) {
            return correctKeys((List) o);
        } else if (o instanceof Map) {
            return correctKeys((Map) o);
        }
        return o;
    }

    /**
     * @return A converter that converts an object to a json node
     */
    protected TypeConverter<Object, JsonNode> objectToJsonNodeConverter() {
        return (object, targetType, context) -> {
            try {
                return Optional.of(objectCodec.get().writeValueToTree(object));
            } catch (IllegalArgumentException | IOException e) {
                context.reject(e);
                return Optional.empty();
            }
        };
    }

    /**
     * @return The JSON node to object converter
     */
    protected TypeConverter<JsonNode, Object> jsonNodeToObjectConverter() {
        return (node, targetType, context) -> {
            try {
                if (CharSequence.class.isAssignableFrom(targetType) && node.isObject()) {
                    return Optional.of(node.toString());
                } else {
                    Argument<?> argument = null;
                    if (node.isContainerNode() && context instanceof ArgumentConversionContext && targetType.getTypeParameters().length != 0) {
                        argument = ((ArgumentConversionContext<?>) context).getArgument();
                    }
                    if (argument == null) {
                        argument = Argument.of(targetType);
                    }
                    JsonMapper om = this.objectCodec.get();
                    return Optional.ofNullable(om.readValueFromTree(node, argument));
                }
            } catch (IOException e) {
                context.reject(e);
                return Optional.empty();
            }
        };
    }
}
