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
package io.micronaut.http;

/**
 * A wrapper around a {@link HttpResponse}.
 *
 * @param <B> The Http body type
 * @since 1.0.1
 */
public class HttpResponseWrapper<B> extends HttpMessageWrapper<B> implements HttpResponse<B> {

    /**
     * @param delegate The Http Request
     */
    public HttpResponseWrapper(HttpResponse<B> delegate) {
        super(delegate);
    }

    @Override
    public HttpStatus getStatus() {
        return getDelegate().getStatus();
    }

    @Override
    public HttpResponse<B> getDelegate() {
        return (HttpResponse<B>) super.getDelegate();
    }

}
