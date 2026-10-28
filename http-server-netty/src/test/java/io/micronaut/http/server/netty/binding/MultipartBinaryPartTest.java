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
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A {@code @Part byte[]} argument gets the bytes of its part as sent, also when they are not
 * text, while an array of another type gets every field of a repeated name.
 */
class MultipartBinaryPartTest {
    private static final String SPEC_NAME = "MultipartBinaryPartTest";
    private static final String BOUNDARY = "binary-boundary";
    private static final byte[] FILE = new byte[1000];

    static {
        new Random(1).nextBytes(FILE);
    }

    private static ApplicationContext ctx;
    private static EmbeddedServer server;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME));
        server = ctx.getBean(EmbeddedServer.class).start();
    }

    @AfterAll
    static void stop() {
        ctx.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"bytes", "optional-bytes", "input-stream"})
    void aBinaryPartIsBoundAsSent(String path) throws Exception {
        assertEquals(HexFormat.of().formatHex(FILE), postFile(path, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"bytes", "optional-bytes", "input-stream"})
    void aBinaryFilePartIsBoundAsSent(String path) throws Exception {
        assertEquals(HexFormat.of().formatHex(FILE), postFile(path, true));
    }

    @Test
    void anArrayGetsEveryFieldOfARepeatedName() throws Exception {
        assertEquals("[a, b]|[1, 2]", post("repeated", field("name", "a") + field("name", "b") + field("number", "1") + field("number", "2")));
    }

    private static String postFile(String path, boolean filename) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"file\"" + (filename ? "; filename=\"f.bin\"" : "")
            + "\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        body.writeBytes(FILE);
        body.writeBytes(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        return send(path, body.toByteArray());
    }

    private static String post(String path, String fields) throws Exception {
        return send(path, (fields + "--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.US_ASCII));
    }

    private static String field(String name, String value) {
        return "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n";
    }

    private static String send(String path, byte[] body) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(server.getURL() + "/binary-part/" + path))
                    .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(),
                HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response.body());
            return response.body();
        }
    }

    @Controller("/binary-part")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class BinaryPartController {

        @Post(uri = "/bytes", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String bytes(@Part byte[] file) {
            return HexFormat.of().formatHex(file);
        }

        @Post(uri = "/optional-bytes", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String optionalBytes(@Part Optional<byte[]> file) {
            return HexFormat.of().formatHex(file.orElseThrow());
        }

        @Post(uri = "/input-stream", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String inputStream(@Part InputStream file) throws IOException {
            try (file) {
                return HexFormat.of().formatHex(file.readAllBytes());
            }
        }

        @Post(uri = "/repeated", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String repeated(@Part String[] name, @Part Integer[] number) {
            return Arrays.toString(name) + "|" + Arrays.toString(number);
        }
    }
}
