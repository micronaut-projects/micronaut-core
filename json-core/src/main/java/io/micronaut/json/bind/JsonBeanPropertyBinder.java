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
package io.micronaut.json.bind;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Primary;
import io.micronaut.core.bind.BeanPropertyBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.JsonConfiguration;
import io.micronaut.json.tree.JsonNode;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An {@link io.micronaut.core.bind.ArgumentBinder} capable of binding from an object from a map.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Primary
final class JsonBeanPropertyBinder implements BeanPropertyBinder {

    private final JsonMapper jsonMapper;
    private final int arraySizeThreshhold;
    private final BeanProvider<JsonBeanPropertyBinderExceptionHandler> exceptionHandlers;

    /**
     * @param jsonMapper        To read/write JSON
     * @param configuration     The configuration for Jackson JSON parser
     * @param exceptionHandlers Exception handlers for binding exceptions
     */
    JsonBeanPropertyBinder(JsonMapper jsonMapper, JsonConfiguration configuration, BeanProvider<JsonBeanPropertyBinderExceptionHandler> exceptionHandlers) {
        this.jsonMapper = jsonMapper;
        this.arraySizeThreshhold = configuration.getArraySizeThreshold();
        this.exceptionHandlers = exceptionHandlers;
    }

    @Override
    public BindingResult<Object> bind(ArgumentConversionContext<Object> context, Map<CharSequence, ? super Object> source) {
        try {
            JsonNode objectNode = buildSourceObjectNode(source.entrySet());
            Object result = jsonMapper.readValueFromTree(objectNode, context.getArgument());
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
            JsonNode objectNode = buildSourceObjectNode(source);
            return jsonMapper.readValueFromTree(objectNode, type);
        } catch (Exception e) {
            throw newConversionError(null, e);
        }
    }

    @Override
    public <T2> T2 bind(T2 object, ArgumentConversionContext<T2> context, Set<? extends Map.Entry<? extends CharSequence, Object>> source) {
        try {
            JsonNode objectNode = buildSourceObjectNode(source);
            jsonMapper.updateValueFromTree(object, objectNode);
        } catch (Exception e) {
            context.reject(e);
        }
        return object;
    }

    @Override
    public <T2> T2 bind(T2 object, Set<? extends Map.Entry<? extends CharSequence, Object>> source) throws ConversionErrorException {
        try {
            JsonNode objectNode = buildSourceObjectNode(source);
            jsonMapper.updateValueFromTree(object, objectNode);
        } catch (Exception e) {
            throw newConversionError(object, e);
        }
        return object;
    }

    /**
     * @param object The bean
     * @param e      The exception object
     * @return The new conversion error
     */
    protected ConversionErrorException newConversionError(Object object, Exception e) {
        for (JsonBeanPropertyBinderExceptionHandler exceptionHandler : exceptionHandlers) {
            Optional<ConversionErrorException> handled = exceptionHandler.toConversionError(object, e);
            if (handled.isPresent()) {
                return handled.get();
            }
        }

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

    private JsonNode buildSourceObjectNode(Set<? extends Map.Entry<? extends CharSequence, Object>> source) throws IOException {
        ObjectBuilder rootNode = new ObjectBuilder();
        for (Map.Entry<? extends CharSequence, ? super Object> entry : source) {
            CharSequence key = entry.getKey();
            Object value = entry.getValue();
            String property = key.toString();
            ValueBuilder current = rootNode;
            String index = null;
            Iterator<String> tokenIterator = StringUtils.splitOmitEmptyStringsIterator(property, '.');
            while (tokenIterator.hasNext()) {
                String token = tokenIterator.next();
                int j = token.indexOf('[');
                if (j > -1 && token.endsWith("]")) {
                    index = token.substring(j + 1, token.length() - 1);
                    token = token.substring(0, j);
                }

                if (!tokenIterator.hasNext()) {
                    if (current instanceof ObjectBuilder) {
                        ObjectBuilder objectNode = (ObjectBuilder) current;
                        if (index != null) {
                            ValueBuilder existing = objectNode.values.get(index);
                            if (!(existing instanceof ObjectBuilder)) {
                                existing = new ObjectBuilder();
                                objectNode.values.put(index, existing);
                            }
                            ObjectBuilder node = (ObjectBuilder) existing;
                            node.values.put(token, new FixedValue(jsonMapper.writeValueToTree(value)));
                            index = null;
                        } else {
                            objectNode.values.put(token, new FixedValue(jsonMapper.writeValueToTree(value)));
                        }
                    } else if (current instanceof ArrayBuilder && index != null) {
                        ArrayBuilder arrayNode = (ArrayBuilder) current;
                        int arrayIndex = Integer.parseInt(index);
                        if (arrayIndex < arraySizeThreshhold) {

                            if (arrayIndex >= arrayNode.values.size()) {
                                expandArrayToThreshold(arrayIndex, arrayNode);
                            }
                            ValueBuilder jsonNode = arrayNode.values.get(arrayIndex);
                            if (!(jsonNode instanceof ObjectBuilder)) {
                                jsonNode = new ObjectBuilder();
                                arrayNode.values.set(arrayIndex, jsonNode);
                            }
                            ((ObjectBuilder) jsonNode).values.put(token, new FixedValue(jsonMapper.writeValueToTree(value)));
                        }
                        index = null;
                    }
                } else {
                    if (current instanceof ObjectBuilder) {
                        ObjectBuilder objectNode = (ObjectBuilder) current;
                        ValueBuilder existing = objectNode.values.get(token);
                        if (index != null) {
                            ValueBuilder jsonNode;
                            if (StringUtils.isDigits(index)) {
                                int arrayIndex = Integer.parseInt(index);
                                ArrayBuilder arrayNode;
                                if (!(existing instanceof ArrayBuilder)) {
                                    arrayNode = new ArrayBuilder();
                                    objectNode.values.put(token, arrayNode);
                                } else {
                                    arrayNode = (ArrayBuilder) existing;
                                }
                                expandArrayToThreshold(arrayIndex, arrayNode);
                                jsonNode = getOrCreateNodeAtIndex(arrayNode, arrayIndex);
                            } else {
                                if (!(existing instanceof ObjectBuilder)) {
                                    existing = new ObjectBuilder();
                                    objectNode.values.put(token, existing);
                                }
                                jsonNode = ((ObjectBuilder) existing).values.get(index);
                                if (!(jsonNode instanceof ObjectBuilder)) {
                                    jsonNode = new ObjectBuilder();
                                    ((ObjectBuilder) existing).values.put(index, jsonNode);
                                }
                            }

                            current = jsonNode;
                            index = null;
                        } else {
                            if (!(existing instanceof ObjectBuilder)) {
                                existing = new ObjectBuilder();
                                objectNode.values.put(token, existing);
                            }
                            current = existing;
                        }
                    } else if (current instanceof ArrayBuilder && StringUtils.isDigits(index)) {
                        ArrayBuilder arrayNode = (ArrayBuilder) current;
                        int arrayIndex = Integer.parseInt(index);
                        expandArrayToThreshold(arrayIndex, arrayNode);
                        ObjectBuilder jsonNode = getOrCreateNodeAtIndex(arrayNode, arrayIndex);

                        current = new ObjectBuilder();
                        jsonNode.values.put(token, current);
                        index = null;
                    }
                }
            }
        }
        return rootNode.build();
    }

    private ObjectBuilder getOrCreateNodeAtIndex(ArrayBuilder arrayNode, int arrayIndex) {
        ValueBuilder jsonNode = arrayNode.values.get(arrayIndex);
        if (!(jsonNode instanceof ObjectBuilder)) {
            jsonNode = new ObjectBuilder();
            arrayNode.values.set(arrayIndex, jsonNode);
        }
        return (ObjectBuilder) jsonNode;
    }

    private void expandArrayToThreshold(int arrayIndex, ArrayBuilder arrayNode) {
        if (arrayIndex < arraySizeThreshhold) {
            while (arrayNode.values.size() != arrayIndex + 1) {
                arrayNode.values.add(FixedValue.NULL);
            }
        }
    }

    private interface ValueBuilder {
        JsonNode build();
    }

    private static final class FixedValue implements ValueBuilder {
        static final FixedValue NULL = new FixedValue(JsonNode.nullNode());

        final JsonNode value;

        FixedValue(JsonNode value) {
            this.value = value;
        }

        @Override
        public JsonNode build() {
            return value;
        }
    }

    private static final class ObjectBuilder implements ValueBuilder {
        final Map<String, ValueBuilder> values = new LinkedHashMap<>();

        @Override
        public JsonNode build() {
            Map<String, JsonNode> built = new LinkedHashMap<>(values.size());
            for (Map.Entry<String, ValueBuilder> entry : values.entrySet()) {
                built.put(entry.getKey(), entry.getValue().build());
            }
            return JsonNode.createObjectNode(built);
        }
    }

    private static final class ArrayBuilder implements ValueBuilder {
        final List<ValueBuilder> values = new ArrayList<>();

        @Override
        public JsonNode build() {
            return JsonNode.createArrayNode(values.stream().map(ValueBuilder::build).collect(Collectors.toList()));
        }
    }
}
