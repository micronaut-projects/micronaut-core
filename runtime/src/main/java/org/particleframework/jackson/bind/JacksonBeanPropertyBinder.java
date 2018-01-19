/*
 * Copyright 2017 original authors
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
package org.particleframework.jackson.bind;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.particleframework.context.annotation.Primary;
import org.particleframework.jackson.JacksonConfiguration;
import org.particleframework.core.bind.ArgumentBinder;
import org.particleframework.core.bind.BeanPropertyBinder;
import org.particleframework.core.convert.ArgumentConversionContext;
import org.particleframework.core.convert.ConversionError;
import org.particleframework.core.convert.exceptions.ConversionErrorException;
import org.particleframework.core.type.Argument;
import org.particleframework.core.util.CollectionUtils;
import org.particleframework.core.util.StringUtils;

import javax.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * An {@link ArgumentBinder} capable of binding from an object from a map
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Primary
public class JacksonBeanPropertyBinder implements BeanPropertyBinder {

    private final ObjectMapper objectMapper;
    private final int arraySizeThreshhold;

    public JacksonBeanPropertyBinder(ObjectMapper objectMapper, JacksonConfiguration configuration) {
        this.objectMapper = objectMapper;
        this.arraySizeThreshhold = configuration.getArraySizeThreshold();
    }

    @Override
    public BindingResult<Object> bind(ArgumentConversionContext<Object> context, Map<CharSequence, ? super Object> source) {

        try {
            ObjectNode objectNode = buildSourceObjectNode(source.entrySet());
            Object result = objectMapper.treeToValue(objectNode, context.getArgument().getType());
            return ()-> Optional.of(result);
        } catch (Exception e) {
            context.reject(e);
            return BindingResult.EMPTY;
        }
    }

    @Override
    public <T2> T2 bind(Class<T2> type, Set<? extends Map.Entry<? extends CharSequence, Object>> source) throws ConversionErrorException {
        try {
            ObjectNode objectNode = buildSourceObjectNode(source);
            return objectMapper.treeToValue(objectNode, type);
        } catch (Exception e) {
            throw newConversionError(null, e);
        }
    }

    @Override
    public <T2> T2 bind(T2 object, ArgumentConversionContext<T2> context, Set<? extends Map.Entry<? extends CharSequence, Object>> source) {
        try {
            ObjectNode objectNode = buildSourceObjectNode(source);
            objectMapper.readerForUpdating(object).readValue(objectNode);
        } catch (Exception e) {
            context.reject(e);
        }
        return object;
    }

    @Override
    public <T2> T2 bind(T2 object, Set<? extends Map.Entry<? extends CharSequence, Object>> source) throws ConversionErrorException {
        try {
            ObjectNode objectNode = buildSourceObjectNode(source);
            return objectMapper.readerForUpdating(object).readValue(objectNode);
        } catch (Exception e) {
            throw newConversionError(object, e);
        }
    }


    protected ConversionErrorException newConversionError(Object object, Exception e) {
        ConversionError conversionError = new ConversionError() {
            @Override
            public Exception getCause() {
                return e;
            }

            @Override
            public Optional<Object> getOriginalValue() {
                return Optional.ofNullable(object);
            }
        };
        Class type = object != null ? object.getClass() : Object.class;
        return new ConversionErrorException(Argument.of(type), conversionError);
    }

    private ObjectNode buildSourceObjectNode(Set<? extends Map.Entry<? extends CharSequence, Object>> source) {
        JsonNodeFactory nodeFactory = objectMapper.getNodeFactory();
        ObjectNode rootNode = new ObjectNode(nodeFactory);
        for (Map.Entry<? extends CharSequence, ? super Object> entry : source) {
            CharSequence key = entry.getKey();
            Object value = entry.getValue();
            String property = key.toString();
            String[] tokens = property.split("\\.");
            JsonNode current = rootNode;
            String index = null;
            for (int i = 0; i < tokens.length; i++) {
                String token = tokens[i];
                int j = token.indexOf('[');
                if(j > -1 && token.endsWith("]")) {
                    index = token.substring(j + 1, token.length()-1);
                    token = token.substring(0, j);
                }


                if(i == tokens.length - 1) {
                    if(current instanceof ObjectNode) {
                        ObjectNode objectNode = (ObjectNode) current;
                        if(index != null) {
                            JsonNode existing = objectNode.get(index);
                            if(!(existing instanceof ObjectNode)) {
                                existing = new ObjectNode(nodeFactory);
                                objectNode.set(index,existing);
                            }
                            ObjectNode node = (ObjectNode) existing;
                            node.set(token, objectMapper.valueToTree(value));
                            index = null;
                        }
                        else {
                            objectNode.set(token, objectMapper.valueToTree(value));
                        }
                    }
                    else if(current instanceof ArrayNode && index != null) {
                        ArrayNode arrayNode = (ArrayNode) current;
                        int arrayIndex = Integer.parseInt(index);
                        if(arrayIndex < arraySizeThreshhold) {

                            if(arrayIndex < arrayNode.size()) {
                                JsonNode jsonNode = arrayNode.get(arrayIndex);
                                if(jsonNode instanceof ObjectNode) {
                                    ((ObjectNode)jsonNode).set(token ,objectMapper.valueToTree(value));
                                }
                                else {

                                    arrayNode.set(arrayIndex, new ObjectNode(nodeFactory, CollectionUtils.mapOf(token, objectMapper.valueToTree(value))));
                                }
                            }
                            else {
                                expandArrayToThreshold(arrayIndex, arrayNode);
                                arrayNode.set(arrayIndex, new ObjectNode(nodeFactory, CollectionUtils.mapOf(token, objectMapper.valueToTree(value))));
                            }
                        }
                        index = null;
                    }
                }
                else {
                    if(current instanceof ObjectNode) {
                        ObjectNode objectNode = (ObjectNode) current;
                        JsonNode existing = objectNode.get(token);
                        if(index != null) {
                            JsonNode jsonNode;
                            if(StringUtils.isDigits(index)) {
                                int arrayIndex = Integer.parseInt(index);
                                ArrayNode arrayNode;
                                if(!(existing instanceof ArrayNode)) {
                                    arrayNode = new ArrayNode(nodeFactory);
                                    objectNode.set(token, arrayNode);
                                }
                                else {
                                    arrayNode = (ArrayNode) existing;
                                }
                                expandArrayToThreshold(arrayIndex, arrayNode);
                                jsonNode = getOrCreateNodeAtIndex(nodeFactory, arrayNode, arrayIndex);
                            }
                            else  {
                                if(!(existing instanceof ObjectNode)) {
                                    existing = new ObjectNode(nodeFactory);
                                    objectNode.set(token,existing);
                                }
                                jsonNode = existing.get(index);
                                if(!(jsonNode instanceof ObjectNode)) {
                                    jsonNode = new ObjectNode(nodeFactory);
                                    ((ObjectNode)existing).set(index, jsonNode);
                                }
                            }

                            current = jsonNode;
                            index = null;
                        }
                        else {
                            if(!(existing instanceof ObjectNode)) {
                                existing = new ObjectNode(nodeFactory);
                                objectNode.set(token,existing);
                            }
                            current = existing;
                        }
                    }
                    else if(current instanceof ArrayNode) {
                        if(StringUtils.isDigits(index)) {
                            ArrayNode arrayNode = (ArrayNode) current;
                            int arrayIndex = Integer.parseInt(index);
                            expandArrayToThreshold(arrayIndex, arrayNode);
                            JsonNode jsonNode = getOrCreateNodeAtIndex(nodeFactory, arrayNode, arrayIndex);

                            current = new ObjectNode(nodeFactory);
                            ((ObjectNode) jsonNode).set(token, current);
                            index = null;
                        }
                    }
                }
            }
        }
        return rootNode;
    }

    private JsonNode getOrCreateNodeAtIndex(JsonNodeFactory nodeFactory, ArrayNode arrayNode, int arrayIndex) {
        JsonNode jsonNode = arrayNode.get(arrayIndex);
        if(jsonNode == null || !(jsonNode instanceof ObjectNode)) {
            jsonNode = new ObjectNode(nodeFactory);
            arrayNode.set(arrayIndex, jsonNode);
        }
        return jsonNode;
    }

    private void expandArrayToThreshold(int arrayIndex, ArrayNode arrayNode) {
        if(arrayIndex < arraySizeThreshhold) {
            while(arrayNode.size() != arrayIndex + 1) {
                arrayNode.addNull();
            }
        }
    }

}
