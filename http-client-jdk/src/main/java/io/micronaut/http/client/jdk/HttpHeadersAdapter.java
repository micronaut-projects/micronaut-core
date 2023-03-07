/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.client.jdk;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpHeaders;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Adapter from {@link java.net.http.HttpHeaders} into {@link HttpHeaders}.
 * @author Sergio del Amo
 * @since 4.0.0
 */
@Internal
@Experimental
public class HttpHeadersAdapter implements HttpHeaders {

    private final java.net.http.HttpHeaders httpHeaders;
    private final ConversionService conversionService;

    /**
     *
     * @param httpHeaders HTTP Headers.
     * @param conversionService Conversion Service.
     */
    public HttpHeadersAdapter(java.net.http.HttpHeaders httpHeaders, ConversionService conversionService) {
        this.httpHeaders = httpHeaders;
        this.conversionService = conversionService;
    }

    @Override
    public List<String> getAll(CharSequence name) {
        return httpHeaders.allValues(name.toString());
    }

    @Override
    public String get(CharSequence name) {
        return httpHeaders.firstValue(name.toString()).orElse(null);
    }

    @Override
    public Set<String> names() {
        return httpHeaders.map().keySet();
    }

    @Override
    public Collection<List<String>> values() {
        return httpHeaders.map().values();
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        return conversionService.convert(get(name), conversionContext);
    }
}
