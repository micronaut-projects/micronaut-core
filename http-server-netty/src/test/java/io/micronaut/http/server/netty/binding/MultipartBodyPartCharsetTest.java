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
package io.micronaut.http.server.netty.binding;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.runtime.server.EmbeddedServer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@code @Body} argument converted from a multipart form decodes each text part with the
 * charset of its part, like a {@code @Part String} argument and the text fields of a form a
 * filter read with {@code copy().form()}.
 */
class MultipartBodyPartCharsetTest {
    private static final String SPEC_NAME = "MultipartBodyPartCharsetTest";
    private static final String BOUNDARY = "body-charset-boundary";
    private static final String GREETING = "Grüße";

    @Test
    void aBodyRecordIsDecodedWithTheCharsetOfEachPart() throws Exception {
        String response = post("/body-charset/record");

        assertTrue(response.startsWith("HTTP/1.1 200"), response);
        assertTrue(response.endsWith(GREETING + "|" + GREETING), response);
    }

    @Test
    void aBodyMapIsDecodedWithTheCharsetOfEachPart() throws Exception {
        String response = post("/body-charset/map");

        assertTrue(response.startsWith("HTTP/1.1 200"), response);
        assertTrue(response.endsWith(GREETING + "|" + GREETING), response);
    }

    private static String post(String path) throws IOException {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            try (Socket socket = new Socket(server.getHost(), server.getPort())) {
                socket.setSoTimeout(30_000);
                ByteArrayOutputStream body = new ByteArrayOutputStream();
                body.write(("--" + BOUNDARY + "\r\n"
                    + "Content-Disposition: form-data; name=\"greeting\"\r\n"
                    + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
                    + "\r\n").getBytes(StandardCharsets.US_ASCII));
                body.write(GREETING.getBytes(StandardCharsets.ISO_8859_1));
                body.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.US_ASCII));
                OutputStream out = socket.getOutputStream();
                out.write(("POST " + path + " HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "Connection: close\r\n"
                    + "Content-Type: multipart/form-data; boundary=" + BOUNDARY + "\r\n"
                    + "Content-Length: " + body.size() + "\r\n"
                    + "\r\n").getBytes(StandardCharsets.US_ASCII));
                out.write(body.toByteArray());
                out.flush();
                return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    @Introspected
    record Greeting(String greeting) {
    }

    @Controller("/body-charset")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class BodyController {

        @Post(uri = "/record", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN + ";charset=UTF-8")
        String record(@Body Greeting body, HttpRequest<?> request) {
            return request.getAttribute(FormFilter.SEEN, String.class).orElse("none") + "|" + body.greeting();
        }

        @Post(uri = "/map", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN + ";charset=UTF-8")
        String map(@Body Map<String, Object> body, HttpRequest<?> request) {
            return request.getAttribute(FormFilter.SEEN, String.class).orElse("none") + "|" + body.get("greeting");
        }
    }

    @ServerFilter("/body-charset/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FormFilter {
        static final String SEEN = "MultipartBodyPartCharsetTest.seen";

        @RequestFilter
        CompletionStage<@Nullable HttpResponse<?>> read(AsyncRequestBody body, HttpRequest<?> request) {
            return body.copy().form().thenApply(form -> {
                request.setAttribute(SEEN, form.getString("greeting"));
                return null;
            });
        }
    }
}
