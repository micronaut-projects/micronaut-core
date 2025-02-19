/*
 * Copyright 2017-2024 original authors
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
import io.micronaut.http.*;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ErrorResponseProcessorExceptionHandler;
import io.micronaut.http.server.exceptions.NotFoundException;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.BodyAssertion;
import io.micronaut.http.tck.HttpResponseAssertion;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static io.micronaut.http.tck.TestScenario.asserts;

@SuppressWarnings({
        "java:S5960", // We're allowed assertions, as these are used in tests only
        "checkstyle:MissingJavadocType",
        "checkstyle:DesignForExtension"
})
public class NotFoundExceptionHandlerTest {
    public static final String SPEC_NAME = "NotFoundExceptionHandlerTest";
    private static final String EXPECTED = """
<!DOCTYPE html>
<html>
<head>
<title>Not Found</title>
</head>
<body>
<h1>Not Found</h1>
</body>
</html>""";

    @Test
    void testCatchingRouteNotFoundExceptions() throws IOException {
        asserts(SPEC_NAME,
                HttpRequest.GET("/blababalbabagbababababa"),
                (server, request) -> AssertionUtils.assertThrows(
                        server,
                        request,
                        HttpResponseAssertion.builder()
                                .status(HttpStatus.NOT_FOUND)
                                .body(BodyAssertion.builder().body(EXPECTED).equals())
                                .headers(Map.of(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML))
                                .build()
                )
        );
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Singleton
    @Produces
    static class NotFoundExceptionHandler extends ErrorResponseProcessorExceptionHandler<NotFoundException> {

        protected NotFoundExceptionHandler(ErrorResponseProcessor<?> responseProcessor) {
            super(responseProcessor);
        }

        @Override
        protected MutableHttpResponse<?> createResponse(NotFoundException exception) {
            return HttpResponse.notFound(EXPECTED).contentType(MediaType.TEXT_HTML);
        }

        @Override
        public HttpResponse<?> handle(HttpRequest request, NotFoundException exception) {
            return createResponse(exception);
        }
    }

}
