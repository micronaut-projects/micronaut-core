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
package io.micronaut.http.simple;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleMultiValues;
import io.micronaut.core.convert.value.ConvertibleMultiValuesMap;
import io.micronaut.http.MutableHttpParameters;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Simple implementation of {@link MutableHttpParameters}.
 *
 * @author Graeme Rocher
 * @author Vladimir Orany
 *
 * @since 1.0
 */
public class SimpleHttpParameters implements MutableHttpParameters {

    private final Map<CharSequence, List<String>> valuesMap;
    private final ConvertibleMultiValues<String> values;

    /**
     * @param values The parameter values
     * @param conversionService The conversion service
     */
    public SimpleHttpParameters(Map<CharSequence, List<String>> values, ConversionService conversionService) {
        this.valuesMap = values;
        this.values = new ConvertibleMultiValuesMap<>(this.valuesMap, conversionService);
    }

    /**
     * @param conversionService The conversion service
     */
    public SimpleHttpParameters(ConversionService conversionService) {
        this(new LinkedHashMap<>(), conversionService);
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
        valuesMap.put(name, values.stream().map(v -> v == null ? null : v.toString()).collect(Collectors.toList()));
        return this;
    }
}
