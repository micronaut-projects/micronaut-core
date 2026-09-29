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
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A copy of the response of a {@link HttpRouteSpec#respond(HttpResponse)} route,
 * taken when the route is declared, that creates a new response for each request: the server
 * and the filters change the response they answer with, e.g. add headers, so a response is never
 * shared between requests.
 *
 * @param code       The status code
 * @param reason     The reason phrase
 * @param headers    The headers, a name and a value per entry, in their order
 * @param attributes The attributes
 * @param body       The body, shared by the responses, or {@code null}
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record ResponseTemplate(int code,
                        String reason,
                        List<Map.Entry<String, String>> headers,
                        Map<String, Object> attributes,
                        @Nullable Object body) implements Supplier<HttpResponse<?>> {

    ResponseTemplate {
        headers = List.copyOf(headers);
        attributes = Map.copyOf(attributes);
    }

    /**
     * @param response The response
     * @return The copy of the response
     */
    static ResponseTemplate of(HttpResponse<?> response) {
        Objects.requireNonNull(response, "response");
        List<Map.Entry<String, String>> headers = new ArrayList<>();
        response.getHeaders().forEach((name, values) -> {
            for (String value : values) {
                headers.add(Map.entry(name, value));
            }
        });
        Map<String, Object> attributes = new LinkedHashMap<>();
        response.getAttributes().forEach((name, value) -> {
            if (value != null) {
                attributes.put(name.toString(), value);
            }
        });
        return new ResponseTemplate(response.code(), response.reason(), headers, attributes, response.body());
    }

    /**
     * @return The content type of the response, or {@code null}
     */
    @Nullable MediaType contentType() {
        for (Map.Entry<String, String> header : headers) {
            if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(header.getKey())) {
                // without its parameters, e.g. the charset: the route produces the type
                return MediaType.of(MediaType.of(header.getValue()).getName());
            }
        }
        return null;
    }

    /**
     * @return A new response
     */
    @Override
    public HttpResponse<?> get() {
        MutableHttpResponse<Object> response = HttpResponse.status(code, reason);
        for (Map.Entry<String, String> header : headers) {
            response.header(header.getKey(), header.getValue());
        }
        if (!attributes.isEmpty()) {
            attributes.forEach(response::setAttribute);
        }
        if (body != null) {
            response.body(body);
        }
        return response;
    }

    @Override
    public String toString() {
        return "response " + code + ' ' + reason;
    }
}
