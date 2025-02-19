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
package io.micronaut.http.server.tck.tests.forms;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.server.filter.FilterBodyParser;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.TestScenario;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@SuppressWarnings({
        "java:S5960", // We're allowed assertions, as these are used in tests only
        "checkstyle:MissingJavadocType",
        "checkstyle:DesignForExtension"
})
public class FormUrlEncodedBodyInRequestFilterTest {
    public static final String SPEC_NAME = "FormUrlEncodedBodyInRequestFilterTest";

    @Test
    public void bodyParsingInFilter() throws IOException {
        String csrfToken = "abcde";
        String body = "username=sherlock&csrfToken=" + csrfToken + "&password=elementary";
        assertOk(body, MediaType.APPLICATION_FORM_URLENCODED_TYPE);

        PasswordChangeForm json = new PasswordChangeForm("sherlock", "elementary", null);
        assertUnauthorized(json, MediaType.APPLICATION_JSON_TYPE);

        json = new PasswordChangeForm("sherlock", "elementary", csrfToken);
        assertOk(json, MediaType.APPLICATION_JSON_TYPE);

        body = "username=sherlock&password=elementary";
        assertUnauthorized(body, MediaType.APPLICATION_FORM_URLENCODED_TYPE);
    }

    void assertOk(Object body, MediaType contentType) throws IOException {
        TestScenario.builder()
                .specName(SPEC_NAME)
                .request(HttpRequest.POST("/password/change", body).contentType(contentType))
                .assertion((server, request) ->
                        AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                                .status(HttpStatus.ACCEPTED)
                                .build()))
                .run();
    }

    void assertUnauthorized(Object body, MediaType contentType) throws IOException {
        TestScenario.builder()
                .specName(SPEC_NAME)
                .request(HttpRequest.POST("/password/change", body).contentType(contentType))
                .assertion((server, request) ->
                        AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                                .status(HttpStatus.UNAUTHORIZED)
                                .build()))
                .run();
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller
    static class PasswordChangeController {
        @Produces(MediaType.TEXT_HTML)
        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        @Post("/password/change")
        @Status(HttpStatus.ACCEPTED)
        void changePasswordForm(@Body PasswordChange passwordChangeForm) {
        }

        @Produces(MediaType.TEXT_HTML)
        @Post("/password/change")
        @Status(HttpStatus.ACCEPTED)
        void changePasswordJson(@Body PasswordChange passwordChangeForm) {
        }
    }

    @Requires(property = "spec.name", value = FormUrlEncodedBodyInRequestFilterTest.SPEC_NAME)
    @ServerFilter(ServerFilter.MATCH_ALL_PATTERN)
    static class CsrfFilter {
        private final FilterBodyParser bodyParser;

        CsrfFilter(FilterBodyParser bodyParser) {
            this.bodyParser = bodyParser;
        }

        @ExecuteOn(TaskExecutors.BLOCKING)
        @RequestFilter
        @Nullable
        public HttpResponse<?> csrfFilter(@NonNull HttpRequest<?> request) {
            Map<String, Object> body = null;
            try {
                body = bodyParser.parseBody(request).get();
            } catch (InterruptedException e) {
                return HttpResponse.unauthorized();
            } catch (ExecutionException e) {
                return HttpResponse.unauthorized();
            }
            return body.containsKey("csrfToken") && body.get("csrfToken").equals("abcde") ? null : HttpResponse.unauthorized();
        }
    }

    @Introspected
    record PasswordChange(
            String username,
            String password) {
    }

    @Introspected
    record PasswordChangeForm(
            String username,
            String password,
            String csrfToken) {
    }
}
