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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.BodyAssertion;
import io.micronaut.http.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.stream.IntStream;

import static io.micronaut.http.tck.TestScenario.asserts;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class OctetTest {

    public static final String SPEC_NAME = "OctetTest";

    @Test
    void canReadOctetEncodedData() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/octets"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body(BodyAssertion.builder().body(OctetController.BODY_BYTES).equals())
                .build()));
    }

    @Test
    void byteBody() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/octets/byteBody"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body(BodyAssertion.builder().body(OctetController.BODY_BYTES).equals())
                .build()));
    }

    @Controller("/octets")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class OctetController {

        static final byte[] BODY_BYTES = IntStream.iterate(1, i -> i + 1)
            .limit(256)
            .map(i -> (byte) i)
            .collect(ByteArrayOutputStream::new, ByteArrayOutputStream::write, (a, b) -> a.write(b.toByteArray(), 0, b.size()))
            .toByteArray();

        @Get(produces = MediaType.APPLICATION_OCTET_STREAM)
        HttpResponse<byte[]> byteArray() {
            return HttpResponse.ok(BODY_BYTES);
        }

        @Get(value = "/byteBody", produces = MediaType.APPLICATION_OCTET_STREAM)
        ByteBody byteBody(ServerHttpRequest<?> request) {
            return request.byteBodyFactory().adapt(BODY_BYTES.clone());
        }
    }
}
