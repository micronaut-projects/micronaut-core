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
package io.micronaut.jackson.bind;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.core.bind.BeanPropertyBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.jackson.JacksonConfiguration;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * An {@link io.micronaut.core.bind.ArgumentBinder} capable of binding from an object from a map.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Primary
public class JacksonBeanPropertyBinder implements BeanPropertyBinder {

    private final ObjectMapper objectMapper;
    private final int arraySizeThreshhold;

    /**
     * @param objectMapper  To read/write JSON
     * @param configuration The configuration for Jackson JSON parser
     */
    public JacksonBeanPropertyBinder(ObjectMapper objectMapper, JacksonConfiguration configuration) {
        this.objectMapper = objectMapper;
        this.arraySizeThreshhold = configuration.getArraySizeThreshold();
    }

    @Override
    public BindingResult<Object> bind(ArgumentConversionContext<Object> context, Map<CharSequence, ? super Object> source) {
        try {
            Map objectNode = buildSourceMapNode(source.entrySet());
            TypeFactory typeFactory = objectMapper.getTypeFactory();
            JavaType javaType = JacksonConfiguration.constructType(context.getArgument(), typeFactory);
            Object result = objectMapper.convertValue(objectNode, javaType);
            return () -> Optional.of(result);
        } catch (Exception e) {
            context.reject(e);
            return new BindingResult<Object>() {
                @Override
                public List<ConversionError> getConversionErrors() {
                    return CollectionUtils.iterableToList(context);
                }

                @Override
                public boolean isSatisfied() {
                    return false;
                }

                @Override
                public Optional<Object> getValue() {
                    return Optional.empty();
                }
            };
        }
    }

    @Override
    public <T2> T2 bind(Class<T2> type, Set<? extends Map.Entry<? extends CharSequence, Object>> source) throws ConversionErrorException {
        try {
            Map objectNode = buildSourceMapNode(source);
            return objectMapper.convertValue(objectNode, type);
        } catch (Exception e) {
            throw newConversionError(null, e);
        }
    }

    @Override
    public <T2> T2 bind(T2 object, ArgumentConversionContext<T2> context, Set<? extends Map.Entry<? extends CharSequence, Object>> source) {
        try {
            Map objectNode = buildSourceMapNode(source);
            JsonNode jacksonTree = objectMapper.valueToTree(objectNode);
            objectMapper.readerForUpdating(object).readValue(jacksonTree);
        } catch (Exception e) {
            context.reject(e);
        }
        return object;
    }

    @Override
    public <T2> T2 bind(T2 object, Set<? extends Map.Entry<? extends CharSequence, Object>> source) throws ConversionErrorException {
        try {
            Map objectNode = buildSourceMapNode(source);
            JsonNode jacksonTree = objectMapper.valueToTree(objectNode);
            return objectMapper.readerForUpdating(object).readValue(jacksonTree);
        } catch (Exception e) {
            throw newConversionError(object, e);
        }
    }

    /**
     * @param object The bean
     * @param e      The exception object
     * @return The new conversion error
     */
    protected ConversionErrorException newConversionError(Object object, Exception e) {
        if (e instanceof InvalidFormatException) {
            InvalidFormatException ife = (InvalidFormatException) e;
            Object originalValue = ife.getValue();
            ConversionError conversionError = new ConversionError() {
                @Override
                public Exception getCause() {
                    return e;
                }

                @Override
                public Optional<Object> getOriginalValue() {
                    return Optional.ofNullable(originalValue);
                }
            };
            Class type = object != null ? object.getClass() : Object.class;
            List<JsonMappingException.Reference> path = ife.getPath();
            String name;
            if (!path.isEmpty()) {
                name = path.get(path.size() - 1).getFieldName();
            } else {
                name = NameUtils.decapitalize(type.getSimpleName());
            }
            return new ConversionErrorException(Argument.of(ife.getTargetType(), name), conversionError);
        } else {

            ConversionError conversionError = new ConversionError() {
                @Override
                public Exception getCause() {
                    return e;
                }

                @Override
                public Optional<Object> getOriginalValue() {
                    return Optional.empty();
                }
            };
            Class type = object != null ? object.getClass() : Object.class;
            return new ConversionErrorException(Argument.of(type), conversionError);
        }
    }

    private Map buildSourceMapNode(Set<? extends Map.Entry<? extends CharSequence, Object>> source) {
        Map rootNode = new LinkedHashMap();
        for (Map.Entry<? extends CharSequence, ? super Object> entry : source) {
            Object value = entry.getValue();
            String property = correctKey(entry.getKey().toString());
            String[] tokens = property.split("\\.");
            Object current = rootNode;
            String index = null;
            for (int i = 0; i < tokens.length; i++) {
                String token = tokens[i];
                int j = token.indexOf('[');
                if (j > -1 && token.endsWith("]")) {
                    index = token.substring(j + 1, token.length() - 1);
                    token = token.substring(0, j);
                }
                if (i == tokens.length - 1) {
                    if (current instanceof Map) {
                        Map mapNode = (Map) current;
                        if (index != null) {
                            mapNode = getOrCreateMapNodeAtKey(mapNode, index);
                            index = null;
                        }
                        mapNode.put(token, processValue(value));
                    } else if (current instanceof List && index != null) {
                        List arrayNode = (List) current;
                        int arrayIndex = Integer.parseInt(index);
                        Map mapNode = getOrCreateMapNodeAtIndex(arrayNode, arrayIndex);
                        mapNode.put(token, processValue(value));
                        index = null;
                    }
                } else {
                    if (current instanceof Map) {
                        Map objectNode = (Map) current;
                        if (index != null) {
                            if (StringUtils.isDigits(index)) {
                                int arrayIndex = Integer.parseInt(index);
                                List arrayNode = getOrCreateListNodeAtKey(objectNode, token);
                                current = getOrCreateMapNodeAtIndex(arrayNode, arrayIndex);
                            } else {
                                Map mapNode = getOrCreateMapNodeAtKey(objectNode, token);
                                current = getOrCreateMapNodeAtKey(mapNode, index);
                            }
                            index = null;
                        } else {
                            current = getOrCreateMapNodeAtKey(objectNode, token);
                        }
                    } else if (current instanceof List && StringUtils.isDigits(index)) {
                        int arrayIndex = Integer.parseInt(index);
                        Map jsonNode = getOrCreateMapNodeAtIndex((List) current, arrayIndex);

                        current = new LinkedHashMap<>();
                        jsonNode.put(token, current);
                        index = null;
                    }
                }
            }
        }
        return rootNode;
    }

    private Map getOrCreateMapNodeAtIndex(List arrayNode, int arrayIndex) {
        if (arrayIndex >= arrayNode.size()) {
            arrayNode = expandArrayToThreshold(arrayIndex, arrayNode);
        }
        Object jsonNode = arrayNode.get(arrayIndex);
        if (!(jsonNode instanceof Map)) {
            jsonNode = new LinkedHashMap<>();
            arrayNode.set(arrayIndex, jsonNode);
        }
        return (Map) jsonNode;
    }

    private Map getOrCreateMapNodeAtKey(Map objectNode, Object key) {
        Object jsonNode = objectNode.get(key);
        if (!(jsonNode instanceof Map)) {
            jsonNode = new LinkedHashMap<>();
            objectNode.put(key, jsonNode);
        }
        return (Map) jsonNode;
    }

    private List getOrCreateListNodeAtKey(Map objectNode, Object key) {
        Object jsonNode = objectNode.get(key);
        if (!(jsonNode instanceof List)) {
            jsonNode = new ArrayList<>();
            objectNode.put(key, jsonNode);
        }
        return (List) jsonNode;
    }

    private List expandArrayToThreshold(int arrayIndex, List arrayNode) {
        if (arrayIndex < arraySizeThreshhold) {
            ArrayList arrayListNode;
            if (arrayNode instanceof ArrayList) {
                arrayListNode = (ArrayList) arrayNode;
            } else {
                arrayListNode = new ArrayList(arrayNode);
            }
            while (arrayNode.size() != arrayIndex + 1) {
                arrayNode.add(arrayIndex, null);
            }
            return arrayListNode;
        }
        return arrayNode;
    }

    private Object processValue(Object o) {
        if (o instanceof List) {
            return processList((List) o);
        } else if (o instanceof Map) {
            return processMap((Map) o);
        } else if (o instanceof Number || o instanceof String || o instanceof Boolean) {
            return o;
        }
        // Not a JSON object (Map, List, Number, String, Boolean) -> needs to be converted to and processed
        return processValue(objectMapper.valueToTree(o));
    }

    private Map processMap(Map<?, ?> map) {
        Map newMap = new LinkedHashMap(map.size());
        for (Map.Entry entry : map.entrySet()) {
            Object key = correctKey(entry.getKey().toString());
            Object value = processValue(entry.getValue());
            newMap.put(key, value);
        }
        return newMap;
    }

    private List processList(List list) {
        List newList = new ArrayList(list.size());
        for (Object o : list) {
            newList.add(processValue(o));
        }
        return newList;
    }

    private String correctKey(String key) {
        return NameUtils.decapitalize(NameUtils.dehyphenate(key));
    }

}
