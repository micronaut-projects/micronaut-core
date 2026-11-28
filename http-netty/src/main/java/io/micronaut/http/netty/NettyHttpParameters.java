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
import org.jspecify.annotations.Nullable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.value.ConvertibleMultiValuesMap;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.MutableHttpParameters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    @Nullable
    private LinkedHashMap<CharSequence, List<String>> valuesMap;
    @Nullable
    private ConvertibleMultiValuesMap<String> values;
    private ConversionService conversionService;
    @Nullable
    private final BiConsumer<CharSequence, List<String>> onChange;

    /**
     * @param parameters        The parameters
     * @param conversionService The conversion service
     * @param onChange A callback for changes
     */
    public NettyHttpParameters(Map<String, List<String>> parameters,
                               ConversionService conversionService,
                               @Nullable BiConsumer<CharSequence, List<String>> onChange) {
        this.conversionService = conversionService;
        this.onChange = onChange;
        if (parameters.isEmpty()) {
            this.valuesMap = null;
            this.values = null;
        } else {
            this.valuesMap = CollectionUtils.newLinkedHashMap(parameters.size());
            this.values = new ConvertibleMultiValuesMap<>(valuesMap, conversionService);
            for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
                valuesMap.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
            }
        }
    }

    /**
     * Create an empty parameters instance without allocating backing maps until mutated.
     *
     * @param conversionService The conversion service
     */
    public NettyHttpParameters(ConversionService conversionService) {
        this.conversionService = conversionService;
        this.onChange = null;
        this.valuesMap = null;
        this.values = null;
    }

    @Override
    public Set<String> names() {
        return values == null ? Collections.emptySet() : values.names();
    }

    @Override
    public Collection<List<String>> values() {
        return values == null ? Collections.emptyList() : values.values();
    }

    @Override
    public List<String> getAll(CharSequence name) {
        return values == null ? Collections.emptyList() : values.getAll(name);
    }

    @Override
    @Nullable
    public String get(CharSequence name) {
        return values == null ? null : values.get(name);
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        return values == null ? Optional.empty() : values.get(name, conversionContext);
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext, TypeConverter<String, T> converter) {
        if (getClass() != NettyHttpParameters.class) {
            // a subclass may override the lookup of a value
            return get(name, conversionContext);
        }
        return values == null ? Optional.empty() : values.get(name, conversionContext, converter);
    }

    @Override
    public MutableHttpParameters add(CharSequence name, List<CharSequence> values) {
        LinkedHashMap<CharSequence, List<String>> map = this.valuesMap;
        if (map == null) {
            map = CollectionUtils.newLinkedHashMap(4);
            this.valuesMap = map;
            this.values = new ConvertibleMultiValuesMap<>(map, conversionService);
        }
        List<String> valueList = map.compute(name, (key, val) -> {
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

    @Override
    public ConversionService getConversionService() {
        return values == null ? conversionService : values.getConversionService();
    }

    @Override
    public void setConversionService(ConversionService conversionService) {
        this.conversionService = conversionService;
        if (values != null) {
            values.setConversionService(conversionService);
        }
    }
}
