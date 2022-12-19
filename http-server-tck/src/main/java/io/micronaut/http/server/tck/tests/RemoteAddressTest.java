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
import io.micronaut.http.server.tck.AssertionUtils;
import io.micronaut.http.server.tck.HttpResponseAssertion;
import io.micronaut.http.server.tck.TestScenario;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import java.io.IOException;
import java.util.Collections;


@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
})
public interface RemoteAddressTest {

    @Test
    default void testRemoteAddressComesFromIdentitySourceIp() throws IOException {
        TestScenario.builder()
            .specName("RemoteAddressTest")
            .request(HttpRequest.GET("/remoteAddress/fromSourceIp"))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .headers(Collections.singletonMap("X-Captured-Remote-Address", "127.0.0.1"))
                .build()))
            .run();
    }

    @Requires(property = "spec.name", value = "RemoteAddressTest")
    @Controller("/remoteAddress")
    class TestController {
        @Get("fromSourceIp")
        void sourceIp() {
        }
    }

    @Requires(property = "spec.name", value = "RemoteAddressTest")
    @Filter("/remoteAddress/**")
    class CaptureRemoteAddressFiter implements HttpServerFilter {
        @Override
        public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            return Publishers.map(chain.proceed(request), httpResponse -> {
                httpResponse.getHeaders().add("X-Captured-Remote-Address", request.getRemoteAddress().getAddress().getHostAddress());
                return httpResponse;
            });
        }
    }

    @Requires(property = "spec.name", value = "RemoteAddressTest")
    @Produces
    @Singleton
    class CustomExceptionHandler implements ExceptionHandler<Exception, HttpResponse> {
        @Override
        public HttpResponse handle(HttpRequest request, Exception exception) {
            return HttpResponse.serverError(exception.toString());
        }
    }
}
