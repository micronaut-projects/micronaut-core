/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseFactory;
import io.micronaut.http.MutableHttpResponse;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Optional;

/**
 * A response prepared with {@link DirectRouteBuilder#prepare(HttpResponse)} that no runtime
 * prepared, e.g. as several runtimes answer the direct routes: it is never changed, and a runtime
 * writes a new copy of it for each request, like the value of a route.
 *
 * @param template The copy of the response
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record SharedDirectResponse(ResponseTemplate template) implements HttpResponse<Object> {

    @Override
    public int code() {
        return template.code();
    }

    @Override
    public String reason() {
        return template.reason();
    }

    /**
     * @return A copy of the headers
     */
    @Override
    public HttpHeaders getHeaders() {
        return toMutableResponse().getHeaders();
    }

    /**
     * @return A copy of the attributes
     */
    @Override
    public MutableConvertibleValues<Object> getAttributes() {
        return MutableConvertibleValues.of(new LinkedHashMap<>(template.attributes()));
    }

    @Override
    public Optional<Object> getBody() {
        return Optional.ofNullable(body());
    }

    /**
     * @return The body, a copy of it if it is bytes
     */
    @Override
    public @Nullable Object body() {
        Object body = template.body();
        return body instanceof byte[] bytes ? bytes.clone() : body;
    }

    /**
     * @return A new response, which the runtime writes: it shares the bytes of the body, which
     *         the runtime never changes
     */
    @Override
    public MutableHttpResponse<?> toMutableResponse() {
        return template.create(HttpResponseFactory.INSTANCE);
    }

    @Override
    public String toString() {
        return "prepared " + template;
    }
}
