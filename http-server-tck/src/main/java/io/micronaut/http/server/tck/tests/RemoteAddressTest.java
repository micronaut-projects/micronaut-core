/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.server.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import java.io.IOException;
import java.util.Collections;

import static io.micronaut.http.tck.TestScenario.asserts;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class RemoteAddressTest {
    public static final String SPEC_NAME = "RemoteAddressTest";

    @Test
    void testRemoteAddressComesFromIdentitySourceIp() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/remoteAddress/fromSourceIp"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .headers(Collections.singletonMap("X-Captured-Remote-Address", "127.0.0.1"))
                .build()));
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/remoteAddress")
    static class TestController {
        @Get("fromSourceIp")
        void sourceIp() {
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Filter("/remoteAddress/**")
    static class CaptureRemoteAddressFiter implements HttpServerFilter {
        @Override
        public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            return Publishers.map(chain.proceed(request), httpResponse -> {
                httpResponse.getHeaders().add("X-Captured-Remote-Address", request.getRemoteAddress().getAddress().getHostAddress());
                return httpResponse;
            });
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Produces
    @Singleton
    static class CustomExceptionHandler implements ExceptionHandler<Exception, HttpResponse> {
        @Override
        public HttpResponse handle(HttpRequest request, Exception exception) {
            return HttpResponse.serverError(exception.toString());
        }
    }
}
