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

import io.micronaut.http.HttpResponseFactory;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;

/**
 * Simple {@link HttpResponseFactory} implementation.
 *
 * This is the default fallback factory.
 *
 * @author Vladimir Orany
 * @since 1.0
 */
public class SimpleHttpResponseFactory implements HttpResponseFactory {

    @Override
    public <T> MutableHttpResponse<T> ok(T body) {
        return new SimpleHttpResponse<T>().body(body);
    }

    @Override
    public <T> MutableHttpResponse<T> status(HttpStatus status, String reason) {
        return new SimpleHttpResponse<T>().status(status, reason);
    }

    @Override
    public <T> MutableHttpResponse<T> status(HttpStatus status, T body) {
        return new SimpleHttpResponse<T>().status(status).body(body);
    }

}
