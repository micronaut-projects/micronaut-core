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
package io.micronaut.http.server.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static io.micronaut.http.tck.TestScenario.asserts;

/**
 * A request that carries no body at all must still reach a route that declares a body type.
 *
 * <p>A GET has no {@code Content-Length} header, so the body length is reported as unknown rather than zero. That is
 * indistinguishable from a body that has not arrived yet unless the server checks whether any bytes exist, and a
 * server that assumes "unknown means present" fails to decode the absent body and answers 400.</p>
 *
 * <p>The shape comes from the OAuth 2.0 authorization code callback, which is a browser redirect carrying only query
 * parameters into a controller whose parameter is a {@code HttpRequest<Map<String, Object>>}.</p>
 */
@SuppressWarnings({"java:S5960", "checkstyle:MissingJavadocType", "checkstyle:DesignForExtension"})
public class BodyWithoutContentLengthTest {
    public static final String SPEC_NAME = "BodyWithoutContentLengthTest";

    @Test
    void getWithABodyTypeAndNoBodyReachesTheRoute() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/body-without-content-length/callback?code=abc"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("code=abc body=empty")
                    .build()));
    }

    @Test
    void deleteWithABodyTypeAndNoBodyReachesTheRoute() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/body-without-content-length/optional-body"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("empty")
                    .build()));
    }

    @Controller("/body-without-content-length")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class BodyWithoutContentLengthController {

        @Get(value = "/callback", produces = MediaType.TEXT_PLAIN)
        String callback(@NonNull HttpRequest<Map<String, Object>> request, @QueryValue String code) {
            Optional<Map<String, Object>> body = request.getBody();
            return "code=" + code + " body=" + body.map(Object::toString).orElse("empty");
        }

        @Get(value = "/optional-body", produces = MediaType.TEXT_PLAIN)
        String optionalBody(@NonNull HttpRequest<String> request) {
            return request.getBody().orElse("empty");
        }
    }
}
