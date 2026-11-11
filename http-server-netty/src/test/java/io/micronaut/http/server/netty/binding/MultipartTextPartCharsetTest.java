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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.form.FormData;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The charset of a text part of a multipart form, declared by the content type of the part: a
 * {@code @Part String} argument of a controller is decoded with it, also when the form is
 * collected whole for a {@link FormData} argument of the route or of a filter, and so are the
 * text fields of the {@link FormData}.
 */
class MultipartTextPartCharsetTest {
    private static final String SPEC_NAME = "MultipartTextPartCharsetTest";
    private static final String BOUNDARY = "charset-boundary";
    private static final String GREETING = "Gr\u00fc\u00dfe";

    @Test
    void aPartArgumentIsDecodedWithTheCharsetOfItsPart() throws Exception {
        String response = post("/part-charset/part");

        assertTrue(response.startsWith("HTTP/1.1 200"), response);
        assertTrue(response.endsWith(GREETING), response);
    }

    @Test
    void aTextFieldOfTheFormDataIsDecodedWithTheCharsetOfItsPart() throws Exception {
        String response = post("/part-charset/form");

        assertTrue(response.startsWith("HTTP/1.1 200"), response);
        assertTrue(response.endsWith(GREETING), response);
    }

    @Test
    void aPartArgumentNextToTheFormDataIsDecodedWithTheCharsetOfItsPart() throws Exception {
        String response = post("/part-charset/part-and-form");

        assertTrue(response.startsWith("HTTP/1.1 200"), response);
        assertTrue(response.endsWith(GREETING + "|" + GREETING), response);
    }

    @Test
    void aPartArgumentIsDecodedWithTheCharsetOfItsPartAfterAFilterReadTheForm() throws Exception {
        String response = post("/part-charset/filtered");

        assertTrue(response.startsWith("HTTP/1.1 200"), response);
        assertTrue(response.endsWith(GREETING + "|" + GREETING), response);
    }

    static String post(String path) throws IOException {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            try (Socket socket = new Socket(server.getHost(), server.getPort())) {
                socket.setSoTimeout(30_000);
                ByteArrayOutputStream body = new ByteArrayOutputStream();
                body.write(("--" + BOUNDARY + "\r\n"
                    + "Content-Disposition: form-data; name=\"greeting\"\r\n"
                    + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
                    + "\r\n").getBytes(StandardCharsets.US_ASCII));
                // not valid UTF-8, the charset of the request
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

    @Controller("/part-charset")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class PartController {

        @Post(uri = "/part", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String part(@Part String greeting) {
            return greeting;
        }

        @Post(uri = "/form", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String form(FormData form) {
            return form.getString("greeting");
        }

        @Post(uri = "/part-and-form", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String partAndForm(@Part String greeting, FormData form) {
            return greeting + "|" + form.getString("greeting");
        }

        @Post(uri = "/filtered", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String filtered(@Part String greeting, HttpRequest<?> request) {
            return greeting + "|" + request.getAttribute(FormFilter.SEEN, String.class).orElse("none");
        }
    }

    @ServerFilter("/part-charset/filtered")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FormFilter {
        static final String SEEN = "MultipartTextPartCharsetTest.seen";

        @RequestFilter
        void read(FormData form, HttpRequest<?> request) {
            request.setAttribute(SEEN, form.getString("greeting"));
        }
    }
}
