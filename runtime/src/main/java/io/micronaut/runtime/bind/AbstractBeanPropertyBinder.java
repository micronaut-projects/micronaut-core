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
package io.micronaut.runtime.bind;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.bind.BeanPropertyBinder;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Internal implementation of {@link BeanPropertyBinder} for mapping properties to Map object.
 *
 * @author Denis Stepanov
 * @since 2.1.0
 */
@Internal
public abstract class AbstractBeanPropertyBinder implements BeanPropertyBinder {

    /**
     * The array size threshold.
     *
     * @return the value
     */
    protected abstract int getArraySizeThreshold();

    /**
     * Convert unknown value to something that we recognize.
     *
     * @param o unknown value
     * @return converted value
     */
    protected abstract Object convertUnknownValue(Object o);

    /**
     * Group properties in Map/List.
     *
     * @param source the properties
     * @return new map object
     */
    protected Map<String, Object> buildMapObject(Set<? extends Map.Entry<? extends CharSequence, Object>> source) {
        Map<String, Object> rootNode = new LinkedHashMap<>();
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
                            if (StringUtils.isDigits(index)) {
                                int arrayIndex = Integer.parseInt(index);
                                List arrayNode = getOrCreateListNodeAtKey(mapNode, token);
                                arrayNode.add(arrayIndex, processValue(value));
                            } else {
                                Map map = getOrCreateMapNodeAtKey(mapNode, token);
                                map.put(index, processValue(value));
                            }
                            index = null;
                        } else {
                            mapNode.put(token, processValue(value));
                        }
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
        if (arrayIndex < getArraySizeThreshold()) {
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
        // Not one of (Map, List, Number, String, Boolean) -> needs to be converted to and processed
        return processValue(convertUnknownValue(o));
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
