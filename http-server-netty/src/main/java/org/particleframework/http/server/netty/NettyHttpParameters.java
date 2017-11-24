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
package org.particleframework.http.server.netty;

import org.particleframework.core.convert.ArgumentConversionContext;
import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.value.ConvertibleMultiValues;
import org.particleframework.core.convert.value.ConvertibleMultiValuesMap;
import org.particleframework.core.type.Argument;
import org.particleframework.http.HttpParameters;

import java.util.*;
import java.util.function.Function;

/**
 * Implementation of {@link HttpParameters} for Netty
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class NettyHttpParameters implements HttpParameters {
    private final ConvertibleMultiValues<String> values;

    NettyHttpParameters(Map<String, List<String>> parameters, ConversionService conversionService) {
        LinkedHashMap<CharSequence, List<String>> values = new LinkedHashMap<>(parameters.size());
        this.values = new ConvertibleMultiValuesMap<>(values, conversionService);
        for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
            values.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
    }


    @Override
    public Set<String> getNames() {
        return values.getNames();
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
}
