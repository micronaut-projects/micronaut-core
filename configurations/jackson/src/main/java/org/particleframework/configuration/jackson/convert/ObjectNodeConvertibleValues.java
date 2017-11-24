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
package org.particleframework.configuration.jackson.convert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.particleframework.core.convert.ArgumentConversionContext;
import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.value.ConvertibleValues;
import org.particleframework.core.type.Argument;
import org.particleframework.core.util.CollectionUtils;

import java.util.*;

/**
 * Simple facade over a Jackson {@link ObjectNode} to make it a {@link ConvertibleValues}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ObjectNodeConvertibleValues<V> implements ConvertibleValues<V> {

    private final ObjectNode objectNode;
    private final ConversionService<?> conversionService;

    public ObjectNodeConvertibleValues(ObjectNode objectNode, ConversionService<?> conversionService) {
        this.objectNode = objectNode;
        this.conversionService = conversionService;
    }

    @Override
    public Set<String> getNames() {
        Iterator<String> fieldNames = objectNode.fieldNames();
        return CollectionUtils.iteratorToSet(fieldNames);
    }

    @Override
    public Collection<V> values() {
        List<V> values = new ArrayList<>();
        for (JsonNode jsonNode : objectNode) {
            values.add((V) jsonNode);
        }
        return Collections.unmodifiableCollection(values);
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        String fieldName = name.toString();
        JsonNode jsonNode = objectNode.get(fieldName);
        if(jsonNode == null) {
            return Optional.empty();
        }
        else {
            return conversionService.convert(jsonNode, conversionContext);
        }
    }
}
