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
package io.micronaut.core.convert.value;

import io.micronaut.core.convert.ConversionService;

import java.util.Map;

/**
 * Mutable version of {@link ConvertibleMultiValuesMap}.
 *
 * @param <V> The generic value
 * @author Graeme Rocher
 * @since 1.0
 */
public class MutableConvertibleValuesMap<V> extends ConvertibleValuesMap<V> implements MutableConvertibleValues<V> {

    /**
     * Default constructor.
     */
    public MutableConvertibleValuesMap() {
    }

    /**
     * @param map The map
     */
    public MutableConvertibleValuesMap(Map<? extends CharSequence, V> map) {
        super(map);
    }

    /**
     * @param map               The map
     * @param conversionService The conversion service
     */
    public MutableConvertibleValuesMap(Map<? extends CharSequence, V> map, ConversionService<?> conversionService) {
        super(map, conversionService);
    }

    @Override
    public String toString() {
        return map.toString();
    }

    @Override
    public MutableConvertibleValues<V> put(CharSequence key, V value) {
        if (value == null) {
            this.map.remove(key);
        } else {
            //noinspection unchecked
            ((Map) this.map).put(key, value);
        }
        return this;
    }

    @Override
    public MutableConvertibleValues<V> remove(CharSequence key) {
        this.map.remove(key);
        return this;
    }

    @Override
    public MutableConvertibleValues<V> clear() {
        this.map.clear();
        return this;
    }
}
