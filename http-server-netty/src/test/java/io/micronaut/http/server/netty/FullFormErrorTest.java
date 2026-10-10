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
package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.runtime.server.EmbeddedServer;
import io.netty.contrib.multipart.FormDecoderException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FullFormErrorTest {
    private static final String FIELD = "--boundary\r\nContent-Disposition: form-data; name=\"field\"\r\n\r\nvalue\r\n";
    private static final String END = "--boundary--\r\n";

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void malformedMultipartReachesTheDecoderErrorHandler(boolean chunked) throws Exception {
        try (ApplicationContext context = run(Map.of());
             HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            EmbeddedServer server = context.getBean(EmbeddedServer.class).start();
            var valid = post(client, server, FIELD + END, chunked);
            assertEquals(200, valid.statusCode(), valid.body());
            assertEquals("value", valid.body());
            var response = post(client, server, FIELD.replace("\r\n\r\n", "\r\nContent-Transfer-Encoding: invalid\r\n\r\n") + END, chunked);
            assertEquals(400, response.statusCode(), response.body());
            assertEquals("malformed multipart", response.body());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void exceededFieldCountRetainsPayloadTooLarge(boolean chunked) throws Exception {
        try (ApplicationContext context = run(Map.of("micronaut.server.netty.form-max-fields", 1));
             HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            EmbeddedServer server = context.getBean(EmbeddedServer.class).start();
            var response = post(client, server, FIELD + FIELD + END, chunked);
            assertEquals(413, response.statusCode(), response.body());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void exceededFieldSizeRetainsPayloadTooLarge(boolean chunked) throws Exception {
        try (ApplicationContext context = run(Map.of("micronaut.server.netty.field-max-buffered-bytes", 4));
             HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            EmbeddedServer server = context.getBean(EmbeddedServer.class).start();
            var response = post(client, server, FIELD + END, chunked);
            assertEquals(413, response.statusCode(), response.body());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void malformedMultipartDefaultsToBadRequest(boolean chunked) throws Exception {
        try (ApplicationContext context = run(Map.of("spec.name", "FullFormErrorWithoutHandlerTest"));
             HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            EmbeddedServer server = context.getBean(EmbeddedServer.class).start();
            var response = post(client, server, FIELD.replace("\r\n\r\n", "\r\nContent-Transfer-Encoding: invalid\r\n\r\n") + END, chunked);
            assertEquals(400, response.statusCode(), response.body());
        }
    }

    @Controller("/full-form-errors")
    @Requires(property = "spec.name", value = "FullFormErrorWithoutHandlerTest")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_PLAIN)
    static class DefaultFormController {
        @Post("/")
        String form(@Body Map<String, String> body) {
            return body.get("field");
        }
    }

    private static ApplicationContext run(Map<String, Object> limits) {
        Map<String, Object> properties = new LinkedHashMap<>(limits);
        properties.putIfAbsent("spec.name", "FullFormErrorTest");
        properties.put("micronaut.server.port", -1);
        return ApplicationContext.run(properties);
    }

    private static java.net.http.HttpResponse<String> post(HttpClient client, EmbeddedServer server, String body, boolean chunked) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        var publisher = chunked ? HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes)) :
            HttpRequest.BodyPublishers.ofByteArray(bytes);
        var request = HttpRequest.newBuilder(URI.create(server.getURL() + "/full-form-errors"))
            .header("Content-Type", "multipart/form-data; boundary=boundary")
            .POST(publisher)
            .build();
        return client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    @Controller("/full-form-errors")
    @Requires(property = "spec.name", value = "FullFormErrorTest")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_PLAIN)
    static class FormController {
        @Post("/")
        String form(@Body Map<String, String> body) {
            return body.get("field");
        }

        @Error(exception = FormDecoderException.class)
        HttpResponse<String> malformed() {
            return HttpResponse.badRequest("malformed multipart");
        }
    }
}
