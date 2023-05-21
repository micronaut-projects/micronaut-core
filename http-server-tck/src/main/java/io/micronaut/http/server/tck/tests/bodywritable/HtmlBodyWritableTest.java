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
package io.micronaut.http.server.tck.tests.bodywritable;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.Writable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.server.tck.AssertionUtils;
import io.micronaut.http.server.tck.BodyAssertion;
import io.micronaut.http.server.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static io.micronaut.http.server.tck.TestScenario.asserts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HtmlBodyWritableTest {
    public static final String SPEC_NAME = "ControllerConstraintHandlerTest";
    private static final HttpResponseAssertion ASSERTION = HttpResponseAssertion.builder()
        .status(HttpStatus.OK)
        .body(BodyAssertion.builder().body("<!DOCTYPE html><html></html>").equals())
        .assertResponse(response -> {
            assertTrue(response.getContentType().isPresent());
            assertEquals(MediaType.TEXT_HTML_TYPE, response.getContentType().get());
        }).build();

    @Test
    void htmlWritable() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/html/writable"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, ASSERTION));
    }

    @Test
    void htmlWritableMono() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/html/writablemono"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, ASSERTION));
    }

    @Test
    void htmlWritableFluxFilter() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/html/writablefluxfilter"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, ASSERTION));
    }

    @Controller("/html")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class OnErrorMethodController {
        @Get("/writable")
        @Produces(MediaType.TEXT_HTML)
        Writable index() {
            return out -> out.write("<!DOCTYPE html><html></html>");
        }

        @Get("/writablemono")
        @Produces(MediaType.TEXT_HTML)
        Mono<Writable> indexmono() {
            Writable writable = out -> out.write("<!DOCTYPE html><html></html>");
            return Mono.just(writable);
        }

        @Get("/writablefluxfilter")
        Map<String, Object> indexfluxfilter() {
            return Collections.emptyMap();
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Filter("/html/writablefluxfilter")
    static class MockFilter implements HttpServerFilter {
        @Override
        public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            return Flux.from(chain.proceed(request))
                .switchMap(response -> {
                    Writable writable = out -> out.write("<!DOCTYPE html><html></html>");
                    response.body(writable);
                    response.contentType(MediaType.TEXT_HTML);
                    return Flux.just(response);
                });
        }
    }
}
