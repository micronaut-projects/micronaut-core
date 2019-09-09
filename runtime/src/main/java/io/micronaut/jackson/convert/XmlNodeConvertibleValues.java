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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Simple facade over a Jackson {@link ObjectNode} that represent XML structure to make it a {@link ConvertibleValues}.
 *
 * @param <V> The generic type for values
 *
 * @author sergey.vishnyakov
 * @since 1.2
 */
public class XmlNodeConvertibleValues<V> implements ConvertibleValues<V> {

    private final ObjectNode objectNode;
    private final ConversionService<?> conversionService;

    /**
     * @param objectNode        The node that maps to JSON object structure
     * @param conversionService To convert the JSON node into given type
     */
    public XmlNodeConvertibleValues(ObjectNode objectNode, ConversionService<?> conversionService) {
        this.objectNode = objectNode;
        this.conversionService = conversionService;
    }

    @Override
    public Set<String> names() {
        Set<String> names  = new HashSet<>();
        for (Map.Entry<String, JsonNode> child: CollectionUtils.iteratorToSet(objectNode.fields())) {
            names.add(child.getKey());
            for (Map.Entry<String, JsonNode> grandChild: CollectionUtils.iteratorToSet(child.getValue().fields())) {
                names.add(grandChild.getKey());
            }
        }

        return Collections.unmodifiableSet(names);
    }

    @Override
    public Collection<V> values() {
        return (Collection<V>) Collections.unmodifiableSet(CollectionUtils.iteratorToSet(objectNode.iterator()));
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        String fieldName = name.toString();

        Class<T> argumentType = conversionContext.getArgument().getType();
        if (Set.class.isAssignableFrom(argumentType)) {
            Class<?> genericType = conversionContext.getFirstTypeVariable().get().getType();

            for (Map.Entry<String, JsonNode> child : CollectionUtils.iteratorToSet(objectNode.fields())) {
                if (child.getKey().equalsIgnoreCase(fieldName)) {

                    Set<Object> setResult = new HashSet<>(child.getValue().size());
                    for (JsonNode setElement: child.getValue()) {
                        conversionService.convert(setElement, genericType).ifPresent(setResult::add);
                    }

                    return Optional.of((T)setResult);
                }
            }
        } else if (List.class.isAssignableFrom(argumentType)) {
            Class<?> genericType = conversionContext.getFirstTypeVariable().get().getType();

            for (Map.Entry<String, JsonNode> child : CollectionUtils.iteratorToSet(objectNode.fields())) {
                if (child.getKey().equalsIgnoreCase(fieldName)) {

                    List<Object> listResult = new ArrayList<>(child.getValue().size());
                    for (JsonNode listElement : child.getValue()) {
                        conversionService.convert(listElement, genericType).ifPresent(listResult::add);
                    }

                    return Optional.of((T) listResult);
                }
            }
        } else if (Map.class.isAssignableFrom(argumentType)) {
            Class<?> valueType = conversionContext.getTypeParameters()[1].getType();
            Map<String, Object> mapResult = new HashMap<>(objectNode.size());

            for (Map.Entry<String, JsonNode> child : CollectionUtils.iteratorToSet(objectNode.fields())) {
                if (child.getKey().equalsIgnoreCase(fieldName)) {

                    conversionService.convert(child.getValue(), valueType).ifPresent(v -> mapResult.put(child.getKey(), v));
                }
            }

            return Optional.of((T) mapResult);
        }
        else {
            for (Map.Entry<String, JsonNode> child : CollectionUtils.iteratorToSet(objectNode.fields())) {
                if (child.getKey().equalsIgnoreCase(fieldName)) {
                    return conversionService.convert(child.getValue(), conversionContext);
                }
            }
        }

        return Optional.empty();
    }
}
