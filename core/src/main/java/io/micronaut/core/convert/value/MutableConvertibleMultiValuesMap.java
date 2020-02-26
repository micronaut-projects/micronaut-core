/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.core.convert.value;

import io.micronaut.core.convert.ConversionService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implementation of {@link MutableConvertibleMultiValues} that operates against a backing {@link java.util.LinkedHashMap}.
 *
 * @param <V> The generic value
 * @author Graeme Rocher
 * @since 1.0
 */
public class MutableConvertibleMultiValuesMap<V> extends ConvertibleMultiValuesMap<V> implements MutableConvertibleMultiValues<V> {

    /**
     * Default constructor.
     */
    public MutableConvertibleMultiValuesMap() {
    }

    /**
     * @param values The values
     */
    public MutableConvertibleMultiValuesMap(Map<CharSequence, List<V>> values) {
        super(values, ConversionService.SHARED);
    }

    /**
     * @param values            The values
     * @param conversionService The conversion service
     */
    public MutableConvertibleMultiValuesMap(Map<CharSequence, List<V>> values, ConversionService<?> conversionService) {
        super(values, conversionService);
    }

    @Override
    public MutableConvertibleMultiValues<V> add(CharSequence key, V value) {
        this.values.computeIfAbsent(key, k -> new ArrayList<>())
            .add(value);
        return this;
    }

    @Override
    public MutableConvertibleValues<List<V>> put(CharSequence key, List<V> value) {
        if (value != null) {
            this.values.put(key, value);
        }
        return this;
    }

    @Override
    public MutableConvertibleValues<List<V>> remove(CharSequence key) {
        this.values.remove(key);
        return this;
    }

    @Override
    public MutableConvertibleMultiValues<V> remove(CharSequence key, V value) {
        this.values.computeIfAbsent(key, k -> new ArrayList<>())
            .remove(value);

        return this;
    }

    @Override
    public MutableConvertibleMultiValues<V> clear() {
        this.values.clear();
        return this;
    }

    @Override
    protected Map<CharSequence, List<V>> wrapValues(Map<CharSequence, List<V>> values) {
        return values;
    }
}
