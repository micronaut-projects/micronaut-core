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

import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequestFactory;
import io.micronaut.http.MutableHttpRequest;

/**
 * Simple {@link HttpRequestFactory} implementation.
 *
 * This is the default fallback factory.
 *
 * @author Vladimir Orany
 * @since 1.0
 */
public class SimpleHttpRequestFactory implements HttpRequestFactory {
    @Override
    public <T> MutableHttpRequest<T> get(String uri) {
        return new SimpleHttpRequest<>(HttpMethod.GET, uri, null);
    }

    @Override
    public <T> MutableHttpRequest<T> post(String uri, T body) {
        return new SimpleHttpRequest<>(HttpMethod.POST, uri, body);
    }

    @Override
    public <T> MutableHttpRequest<T> put(String uri, T body) {
        return new SimpleHttpRequest<>(HttpMethod.PUT, uri, body);
    }

    @Override
    public <T> MutableHttpRequest<T> patch(String uri, T body) {
        return new SimpleHttpRequest<>(HttpMethod.PATCH, uri, body);
    }

    @Override
    public <T> MutableHttpRequest<T> head(String uri) {
        return new SimpleHttpRequest<>(HttpMethod.HEAD, uri, null);
    }

    @Override
    public <T> MutableHttpRequest<T> options(String uri) {
        return new SimpleHttpRequest<>(HttpMethod.OPTIONS, uri, null);
    }

    @Override
    public <T> MutableHttpRequest<T> delete(String uri, T body) {
        return new SimpleHttpRequest<>(HttpMethod.DELETE, uri, body);
    }

    @Override
    public <T> MutableHttpRequest<T> create(HttpMethod httpMethod, String uri) {
        return new SimpleHttpRequest<>(httpMethod, uri, null);
    }
}
