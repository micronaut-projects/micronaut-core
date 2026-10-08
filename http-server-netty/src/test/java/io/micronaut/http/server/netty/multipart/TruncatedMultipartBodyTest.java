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
package io.micronaut.http.server.netty.multipart;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A multipart body that ends before its closing boundary, e.g. a truncated upload or a body with
 * a wrong closing boundary, is malformed: it is answered with 400, not with the fields that were
 * read before the end.
 */
class TruncatedMultipartBodyTest {
    private static final String BOUNDARY = "bnd12345";
    private static final String FIRST = "--" + BOUNDARY + "\r\n"
        + "Content-Disposition: form-data; name=\"a\"\r\n"
        + "\r\n"
        + "1\r\n";
    private static final String SECOND_HEAD = "--" + BOUNDARY + "\r\n"
        + "Content-Disposition: form-data; name=\"b\"; filename=\"b.txt\"\r\n"
        + "Content-Type: text/plain\r\n"
        + "\r\n";

    private static ApplicationContext context;
    private static EmbeddedServer server;

    @BeforeAll
    static void start() {
        context = ApplicationContext.run(Map.of("spec.name", "TruncatedMultipartBodyTest"));
        server = context.getBean(EmbeddedServer.class).start();
    }

    @AfterAll
    static void stop() {
        context.close();
    }

    @Test
    void aCompleteBodyIsRead() throws IOException {
        assertOk("1,22", post("/truncated-multipart/text", FIRST + SECOND_HEAD + "22\r\n--" + BOUNDARY + "--\r\n"));
        // the line break after the closing boundary is optional, and an epilogue is ignored
        assertOk("1,22", post("/truncated-multipart/text", FIRST + SECOND_HEAD + "22\r\n--" + BOUNDARY + "--"));
        assertOk("1,22", post("/truncated-multipart/text", FIRST + SECOND_HEAD + "22\r\n--" + BOUNDARY + "--\r\nepilogue"));
        assertOk("1,22", post("/truncated-multipart/file", FIRST + SECOND_HEAD + "22\r\n--" + BOUNDARY + "--\r\n"));
        assertOk("a,b", post("/truncated-multipart/form", FIRST + SECOND_HEAD + "22\r\n--" + BOUNDARY + "--\r\n"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        // the content of the last part is cut
        "22",
        // a closing boundary that is not the boundary of the body: the last part never ends
        "22\r\n--other--\r\n",
    })
    void aBodyThatEndsInAPartIsABadRequest(String end) throws IOException {
        assertBadRequest(post("/truncated-multipart/text", FIRST + SECOND_HEAD + end));
        assertBadRequest(post("/truncated-multipart/file", FIRST + SECOND_HEAD + end));
        assertBadRequest(post("/truncated-multipart/form", FIRST + SECOND_HEAD + end));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "22",
        "22\r\n--other--\r\n",
        // the delimiter of another part, and no part
        "22\r\n--" + BOUNDARY + "\r\n",
        // a closing delimiter that is cut before its end
        "22\r\n--" + BOUNDARY,
        "22\r\n--" + BOUNDARY + "-",
    })
    void aFormReadWholeFromABodyWithoutItsClosingBoundaryIsABadRequest(String end) throws IOException {
        // the parts of the body may be complete, but the body is not: the form is read until it
        // ends. A route that reads single parts may answer as soon as they ended
        assertBadRequest(post("/truncated-multipart/form", FIRST + SECOND_HEAD + end));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/truncated-multipart/text", "/truncated-multipart/file", "/truncated-multipart/form"})
    void aStreamedBodyThatEndsBeforeItsClosingBoundaryIsABadRequest(String path) throws Exception {
        // the body arrives in two pieces: it is streamed to the decoder, not read whole
        assertBadRequest(post(path, FIRST + SECOND_HEAD, "22\r\n--other--\r\n"));
        assertOk(path.endsWith("form") ? "a,b" : "1,22", post(path, FIRST + SECOND_HEAD, "22\r\n--" + BOUNDARY + "--\r\n"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/truncated-multipart/text", "/truncated-multipart/file", "/truncated-multipart/form"})
    void aBodyWithBareLineFeedsIsRead(String path) throws IOException {
        // the decoder accepts lines that end with a bare LF, in the delimiters too
        String body = (FIRST + SECOND_HEAD + "22\r\n--" + BOUNDARY + "--\r\n").replace("\r\n", "\n");
        assertOk(path.endsWith("form") ? "a,b" : "1,22", post(path, body));
        assertBadRequest(post(path, (FIRST + SECOND_HEAD + "22\r\n--other--\r\n").replace("\r\n", "\n")));
    }

    @Test
    void aBodyWithAPreambleOrAQuotedBoundaryIsRead() throws IOException {
        String body = FIRST + SECOND_HEAD + "22\r\n--" + BOUNDARY + "--\r\n";
        assertOk("a,b", post("/truncated-multipart/form", "This is the preamble.\r\n" + body));
        assertOk("a,b", send(request("/truncated-multipart/form", "multipart/form-data; boundary=\"" + BOUNDARY + "\"", body, false, true)));
    }

    @Test
    void aChunkedBodyThatEndsBeforeItsClosingBoundaryIsABadRequest() throws IOException {
        String ct = "multipart/form-data; boundary=" + BOUNDARY;
        assertBadRequest(send(request("/truncated-multipart/form", ct, FIRST + SECOND_HEAD + "22\r\n", true, true)));
        assertOk("a,b", send(request("/truncated-multipart/form", ct, FIRST + SECOND_HEAD + "22\r\n--" + BOUNDARY + "--\r\n", true, true)));
    }

    @Test
    void theNextRequestOnTheConnectionIsAnsweredAfterTheBadRequest() throws IOException {
        // the truncated body was read whole, so the connection stays usable
        String ct = "multipart/form-data; boundary=" + BOUNDARY;
        String response = send(request("/truncated-multipart/form", ct, FIRST + SECOND_HEAD + "22\r\n", false, false)
            + request("/truncated-multipart/form", ct, FIRST + SECOND_HEAD + "22\r\n--" + BOUNDARY + "--\r\n", false, true));
        assertBadRequest(response);
        int second = response.indexOf("HTTP/1.1 ", 1);
        assertTrue(second > 0, response);
        assertOk("a,b", response.substring(second));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/truncated-multipart/text", "/truncated-multipart/form"})
    void aMalformedBodyIsABadRequest(String path) throws IOException {
        // the decoder refuses the part: the form is malformed, like a truncated one
        String response = post(path, FIRST + "--" + BOUNDARY + "\r\n"
            + "Content-Disposition: form-data; name=\"b\"\r\n"
            + "Content-Transfer-Encoding: bogus\r\n"
            + "\r\n"
            + "22\r\n--" + BOUNDARY + "--\r\n");
        assertBadRequest(response);
        assertFalse(response.contains("bogus"), response);
    }

    @Test
    void aBodyThatEndsInTheHeadersOfAPartIsABadRequest() throws IOException {
        assertBadRequest(post("/truncated-multipart/text", FIRST + "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"b\""));
        assertBadRequest(post("/truncated-multipart/form", FIRST + "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"b\""));
    }

    private static void assertOk(String expectedBody, String response) {
        assertTrue(response.startsWith("HTTP/1.1 200 "), response);
        assertEquals(expectedBody, response.substring(response.indexOf("\r\n\r\n") + 4));
    }

    private static void assertBadRequest(String response) {
        assertTrue(response.startsWith("HTTP/1.1 400 "), response);
    }

    private static String request(String path, String contentType, String body, boolean chunked, boolean close) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return "POST " + path + " HTTP/1.1\r\n"
            + "Host: " + server.getHost() + "\r\n"
            + "Content-Type: " + contentType + "\r\n"
            + (chunked ? "Transfer-Encoding: chunked\r\n" : "Content-Length: " + bytes.length + "\r\n")
            + (close ? "Connection: close\r\n" : "")
            + "\r\n"
            + (chunked ? Integer.toHexString(bytes.length) + "\r\n" + body + "\r\n0\r\n\r\n" : body);
    }

    /**
     * Send the requests, and read the responses until the server closes the connection.
     */
    private static String send(String requests) throws IOException {
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout(30_000);
            OutputStream out = socket.getOutputStream();
            out.write(requests.getBytes(StandardCharsets.UTF_8));
            out.flush();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Post the body as it is, which the HTTP client refuses for a multipart request.
     */
    private static String post(String path, String body) throws IOException {
        try {
            return post(path, body, "");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    /**
     * Post the body in two pieces, with a pause between them.
     */
    private static String post(String path, String first, String second) throws IOException, InterruptedException {
        byte[] firstBytes = first.getBytes(StandardCharsets.UTF_8);
        byte[] secondBytes = second.getBytes(StandardCharsets.UTF_8);
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout(30_000);
            OutputStream out = socket.getOutputStream();
            out.write(("POST " + path + " HTTP/1.1\r\n"
                + "Host: " + server.getHost() + "\r\n"
                + "Content-Type: multipart/form-data; boundary=" + BOUNDARY + "\r\n"
                + "Content-Length: " + (firstBytes.length + secondBytes.length) + "\r\n"
                + "Connection: close\r\n"
                + "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(firstBytes);
            out.flush();
            if (secondBytes.length > 0) {
                Thread.sleep(100);
                out.write(secondBytes);
                out.flush();
            }
            InputStream in = socket.getInputStream();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Controller("/truncated-multipart")
    @Requires(property = "spec.name", value = "TruncatedMultipartBodyTest")
    static class FormController {
        @Post(value = "/text", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String text(@Part String a, @Nullable @Part String b) {
            return a + "," + b;
        }

        @Post(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String file(@Part String a, @Nullable @Part CompletedFileUpload b) throws IOException {
            if (b == null) {
                return a + ",null";
            }
            try (b) {
                return a + "," + new String(b.getBytes(), StandardCharsets.UTF_8);
            }
        }

        @Post(value = "/form", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String form(@Body Map<String, Object> form) {
            return String.join(",", form.keySet());
        }
    }
}
