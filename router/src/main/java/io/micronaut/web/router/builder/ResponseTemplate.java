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
import io.micronaut.http.HttpResponseFactory;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import org.jspecify.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

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
     * The template of a response given as a value to a direct route: a text body is encoded
     * once, a {@code byte[]} body copied, and any other body prepared by the server runtimes, so
     * that the server only wraps or writes a body the requests share, and never consumes it.
     *
     * @param response      The response
     * @param shareableBody Prepares a body of a server runtime, e.g. copies a buffer the runtime
     *                      releases once written, see
     *                      {@link io.micronaut.web.router.direct.DirectRouteSupport#shareableBody(Object)}
     * @return The template
     */
    static ResponseTemplate direct(HttpResponse<?> response, UnaryOperator<Object> shareableBody) {
        ResponseTemplate template = of(response);
        Object body = template.body();
        if (body == null) {
            return template;
        }
        if (body instanceof CharSequence text) {
            Charset charset = response.getContentType().flatMap(MediaType::getCharset).orElse(StandardCharsets.UTF_8);
            return template.withBody(text.toString().getBytes(charset));
        }
        if (body instanceof byte[] bytes) {
            // the caller keeps the array
            return template.withBody(bytes.clone());
        }
        Object shared = shareableBody.apply(body);
        return shared == body ? template : template.withBody(shared);
    }

    /**
     * @param newBody The body
     * @return A copy of the template with the body
     */
    ResponseTemplate withBody(Object newBody) {
        return new ResponseTemplate(code, reason, headers, attributes, newBody);
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
        return create(HttpResponseFactory.INSTANCE);
    }

    /**
     * @param responses The factory of the responses, e.g. the one of a server runtime
     * @return A new response of the factory
     */
    MutableHttpResponse<?> create(HttpResponseFactory responses) {
        MutableHttpResponse<Object> response = responses.status(code, reason);
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
