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
package io.micronaut.http.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleMultiValues;
import io.micronaut.core.convert.value.ConvertibleMultiValuesMap;
import io.micronaut.http.MutableHttpParameters;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Implementation of {@link MutableHttpParameters} for Netty.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class NettyHttpParameters implements MutableHttpParameters {

    private final LinkedHashMap<CharSequence, List<String>> valuesMap;
    private final ConvertibleMultiValues<String> values;
    private final BiConsumer<CharSequence, List<String>> onChange;

    /**
     * @param parameters        The parameters
     * @param conversionService The conversion service
     * @param onChange A callback for changes
     */
    public NettyHttpParameters(Map<String, List<String>> parameters,
                               ConversionService<?> conversionService,
                               @Nullable BiConsumer<CharSequence, List<String>> onChange) {
        this.valuesMap = new LinkedHashMap<>(parameters.size());
        this.values = new ConvertibleMultiValuesMap<>(valuesMap, conversionService);
        this.onChange = onChange;
        for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
            valuesMap.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
    }

    @Override
    public Set<String> names() {
        return values.names();
    }

    @Override
    public Collection<List<String>> values() {
        return values.values();
    }

    @Override
    public List<String> getAll(CharSequence name) {
        return values.getAll(name);
    }

    @Override
    public String get(CharSequence name) {
        return values.get(name);
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        return values.get(name, conversionContext);
    }

    @Override
    public MutableHttpParameters add(CharSequence name, List<CharSequence> values) {
        List<String> valueList = valuesMap.compute(name, (key, val) -> {
            List<String> newValues = values.stream().map(v -> v == null ? null : v.toString()).collect(Collectors.toList());
            if (val == null) {
                val = new ArrayList<>(newValues.size());
            } else {
                //val is unmodifiable
                val = new ArrayList<>(val);
            }
            val.addAll(newValues);

            return Collections.unmodifiableList(val);
        });

        if (onChange != null) {
            onChange.accept(name, valueList);
        }
        return this;
    }
}
