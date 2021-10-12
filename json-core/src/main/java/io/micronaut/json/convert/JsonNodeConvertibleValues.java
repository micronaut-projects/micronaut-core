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
package io.micronaut.json.convert;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.json.tree.JsonNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Simple facade over a {@link JsonNode} to make it a {@link ConvertibleValues}.
 *
 * @param <V> The generic type for values
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class JsonNodeConvertibleValues<V> implements ConvertibleValues<V> {

    private final JsonNode objectNode;
    private final ConversionService<?> conversionService;

    /**
     * @param objectNode        The node that maps to JSON object structure
     * @param conversionService To convert the JSON node into given type
     */
    public JsonNodeConvertibleValues(JsonNode objectNode, ConversionService<?> conversionService) {
        if (!objectNode.isObject()) {
            throw new IllegalArgumentException("Expected object node");
        }
        this.objectNode = objectNode;
        this.conversionService = conversionService;
    }

    @Override
    public Set<String> names() {
        Set<String> set = new LinkedHashSet<>();
        for (Map.Entry<String, JsonNode> entry : objectNode.entries()) {
            set.add(entry.getKey());
        }
        return Collections.unmodifiableSet(set);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<V> values() {
        List<V> values = new ArrayList<>();
        objectNode.values().forEach(v -> values.add((V) v));
        return Collections.unmodifiableCollection(values);
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        String fieldName = name.toString();
        JsonNode jsonNode = objectNode.get(fieldName);
        if (jsonNode == null) {
            return Optional.empty();
        } else {
            return conversionService.convert(jsonNode, conversionContext);
        }
    }
}
