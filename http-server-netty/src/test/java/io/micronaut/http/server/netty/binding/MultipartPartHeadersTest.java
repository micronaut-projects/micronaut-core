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
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.form.FileUpload;
import io.micronaut.http.form.FormData;
import io.micronaut.http.form.FormParts;
import io.micronaut.http.multipart.FormFieldMetadata;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The content type and the headers of the parts of a multipart form, of a text field as well as
 * of a file, are available from {@link FormData}, {@link FileUpload} and the parts of
 * {@link FormParts}.
 */
class MultipartPartHeadersTest {
    private static final String SPEC_NAME = "MultipartPartHeadersTest";
    private static final String BOUNDARY = "headers-boundary";
    private static final String EXPECTED = "metadata:application/json:42:{\"a\":1}|file:image/png:7:x.png";

    @Test
    void theFormDataExposesTheHeadersOfItsParts() throws Exception {
        String response = post("/part-headers/form");

        assertTrue(response.startsWith("HTTP/1.1 200"), response);
        assertTrue(response.endsWith(EXPECTED), response);
    }

    @Test
    void theFormPartsExposeTheirHeaders() throws Exception {
        String response = post("/part-headers/parts");

        assertTrue(response.startsWith("HTTP/1.1 200"), response);
        assertTrue(response.endsWith("metadata:application/json:42|file:image/png:7"), response);
    }

    @Test
    void aUrlEncodedFieldHasNoContentTypeOrHeaders() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            String body = "metadata=x";
            String response = send(server, "POST /part-headers/urlencoded HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Connection: close\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\n"
                + "Content-Length: " + body.length() + "\r\n"
                + "\r\n" + body);
            assertTrue(response.endsWith("x:1:null:true"), response);
        }
    }

    static String post(String path) throws IOException {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            String body = "--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"metadata\"\r\n"
                + "Content-Type: application/json\r\n"
                + "X-Part-Id: 42\r\n"
                + "\r\n"
                + "{\"a\":1}"
                + "\r\n--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"x.png\"\r\n"
                + "Content-Type: image/png\r\n"
                + "X-Part-Id: 7\r\n"
                + "\r\n"
                + "png"
                + "\r\n--" + BOUNDARY + "--\r\n";
            return send(server, "POST " + path + " HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Connection: close\r\n"
                + "Content-Type: multipart/form-data; boundary=" + BOUNDARY + "\r\n"
                + "Content-Length: " + body.length() + "\r\n"
                + "\r\n" + body);
        }
    }

    private static String send(EmbeddedServer server, String request) throws IOException {
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout(30_000);
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Controller("/part-headers")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class PartController {

        @Post(uri = "/form", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String form(FormData form) {
            FormFieldMetadata metadata = form.getFieldMetadata("metadata").get(0);
            FileUpload file = form.getFile("file");
            String disposition = file.headers().get("content-disposition");
            return "metadata:" + metadata.mediaType() + ":" + metadata.headers().get("X-Part-Id") + ":" + form.getString("metadata")
                + "|file:" + file.contentType().orElseThrow() + ":" + file.headers().get("x-part-id") + ":"
                + (disposition != null && disposition.contains("filename=\"x.png\"") ? file.fileName() : disposition);
        }

        @Post(uri = "/parts", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> parts(FormParts parts) {
            StringBuilder read = new StringBuilder();
            return parts.forEach(part -> {
                if (!read.isEmpty()) {
                    read.append('|');
                }
                read.append(part.name()).append(':').append(part.contentType().orElseThrow()).append(':').append(part.headers().get("X-Part-Id"));
                return part.closeAsync();
            }).thenApply(done -> read.toString());
        }

        @Post(uri = "/urlencoded", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        String urlencoded(FormData form) {
            List<FormFieldMetadata> metadata = form.getFieldMetadata("metadata");
            return form.getString("metadata") + ":" + metadata.size() + ":" + metadata.get(0).mediaType() + ":" + metadata.get(0).headers().isEmpty();
        }
    }
}
