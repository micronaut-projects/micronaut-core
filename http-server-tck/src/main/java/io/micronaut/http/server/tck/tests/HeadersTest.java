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
package io.micronaut.http.server.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.server.tck.AssertionUtils;
import io.micronaut.http.server.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.micronaut.http.server.tck.TestScenario.asserts;

public class HeadersTest {
    public static final String SPEC_NAME = "HeadersTest";


    /**
     * Message header field names are case-insensitive
     *
     * @see <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.2">HTTP/1.1 Message Headers</a>
     */@Test
    void headersAreCaseInsensitiveAsPerMessageHeadersSpecification() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/bar/ok").header("aCcEpT", MediaType.APPLICATION_JSON),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("{\"status\":\"ok\"}")
                .build()));
    }

    /**
     * Message header field value MAY be preceded by any amount of LWS (linear white space), though a single SP is preferred
     *
     * The field-content does not include any leading or trailing LWS: linear white space occurring before the first non-whitespace
     * character of the field-value or after the last non-whitespace character of the field-value. Such leading or trailing
     * LWS MAY be removed without changing the semantics of the field value. Any LWS that occurs between field-content
     * MAY be replaced with a single SP before interpreting the field value or forwarding the message downstream.
     *
     * @see <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.2">HTTP/1.1 Message Headers</a>
     */@Test
    void headersValueLeadingAndTrailingSpaceAreStripped() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/bar/ok").header(HttpHeaders.ACCEPT, "    application/json,    text/html    "),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("{\"status\":\"ok\"}")
                .build()));
    }

    @Controller("/bar")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ProduceController {
        @Get(value = "/ok", produces = MediaType.APPLICATION_JSON)
        String getOkAsJson() {
            return "{\"status\":\"ok\"}";
        }

        @Get(value = "/ok", produces = MediaType.TEXT_HTML)
        String getOkAsHtml() {
            return "<div>ok</div>";
        }
    }
}
