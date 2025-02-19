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
import io.micronaut.http.CaseInsensitiveMutableHttpHeaders;
import io.micronaut.http.MutableHttpHeaders;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Simple {@link MutableHttpHeaders} implementation.
 *
 * @author Vladimir Orany
 * @since 1.0
 */
public class SimpleHttpHeaders implements MutableHttpHeaders {

    private final CaseInsensitiveMutableHttpHeaders headers;
    private ConversionService conversionService;

    /**
     * Map-based implementation of {@link MutableHttpHeaders}.
     */
    public SimpleHttpHeaders() {
        this(ConversionService.SHARED);
    }

    /**
     * Map-based implementation of {@link MutableHttpHeaders}.
     *
     * @param headers           The headers
     * @param conversionService The conversion service
     */
    public SimpleHttpHeaders(Map<String, String> headers, ConversionService conversionService) {
        this.headers = new CaseInsensitiveMutableHttpHeaders(conversionService);
        headers.forEach(this.headers::add);
        this.conversionService = conversionService;
    }

    /**
     * Map-based implementation of {@link MutableHttpHeaders}.
     *
     * @param conversionService The conversion service
     */
    public SimpleHttpHeaders(ConversionService conversionService) {
        this(new LinkedHashMap<>(), conversionService);
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        Optional<String> value = headers.getFirst(name.toString());
        return value.flatMap(it -> conversionService.convert(it, conversionContext));
    }

    @Override
    public List<String> getAll(CharSequence name) {
        return headers.getAll(name.toString());
    }

    @Override
    public Set<String> names() {
        return headers.names();
    }

    @Override
    public Collection<List<String>> values() {
        return headers.values();
    }

    @Override
    public String get(CharSequence name) {
        return headers.get(name.toString());
    }

    @Override
    public MutableHttpHeaders add(CharSequence header, CharSequence value) {
        if (value != null) {
            headers.add(header.toString(), value.toString());
        }
        return this;
    }

    @Override
    public MutableHttpHeaders remove(CharSequence header) {
        headers.remove(header.toString().toLowerCase(Locale.ROOT));
        return this;
    }

    @Override
    public void setConversionService(ConversionService conversionService) {
        this.conversionService = conversionService;
        this.headers.setConversionService(conversionService);
    }
}
