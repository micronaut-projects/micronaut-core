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
package io.micronaut.http.server.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.multipart.PartData;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.server.multipart.MultipartBody;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A streaming body argument claims the request body when it is bound. If the route fails before it
 * returns, e.g. because another argument fails to bind, a {@code @Valid} constraint is violated or
 * the route throws, the server must not leave the connection stalled on the unread body. These tests use a raw socket so that the rest of the body and a
 * second request can be sent on the same connection after the rejection.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class UnconsumedStreamingBodyTest {
    public static final String SPEC_NAME = "UnconsumedStreamingBodyTest";

    private static final String BOUNDARY = "----boundary";
    private static final int FIRST_LENGTH = 1000;
    // much more than the read credit of the server, so that it stalls if it stops reading
    private static final int REST_LENGTH = 1_000_000;
    private static final int TIMEOUT_MILLIS = 10_000;

    @Test
    void publisherUnsatisfied() throws IOException {
        assertConnectionNotStalled("/unconsumed/publisher-unsatisfied", false);
    }

    @Test
    void publisherConversionError() throws IOException {
        assertConnectionNotStalled("/unconsumed/publisher-conversion?value=foo", false);
    }

    @Test
    void inputStreamUnsatisfied() throws IOException {
        assertConnectionNotStalled("/unconsumed/input-stream-unsatisfied", false);
    }

    @Test
    void inputStreamConversionError() throws IOException {
        assertConnectionNotStalled("/unconsumed/input-stream-conversion?value=foo", false);
    }

    @Test
    @Tag("multipart")
    void streamingUploadUnsatisfied() throws IOException {
        assertConnectionNotStalled("/unconsumed/streaming-upload-unsatisfied", true, true);
    }

    @Test
    @Tag("multipart")
    void streamingUploadConversionError() throws IOException {
        assertConnectionNotStalled("/unconsumed/streaming-upload-conversion?value=foo", true);
    }

    @Test
    @Tag("multipart")
    void partPublisherUnsatisfied() throws IOException {
        assertConnectionNotStalled("/unconsumed/part-publisher-unsatisfied", true, true);
    }

    @Test
    @Tag("multipart")
    void multipartBodyUnsatisfied() throws IOException {
        assertConnectionNotStalled("/unconsumed/multipart-body-unsatisfied", true);
    }

    @Test
    void publisherValidationError() throws IOException {
        assertConnectionNotStalled("/unconsumed/publisher-valid?value=1", false);
    }

    @Test
    void inputStreamValidationError() throws IOException {
        assertConnectionNotStalled("/unconsumed/input-stream-valid?value=1", false);
    }

    @Test
    @Tag("multipart")
    void streamingUploadValidationError() throws IOException {
        assertConnectionNotStalled("/unconsumed/streaming-upload-valid?value=1", true);
    }

    @Test
    @Tag("multipart")
    void partPublisherValidationError() throws IOException {
        assertConnectionNotStalled("/unconsumed/part-publisher-valid?value=1", true);
    }

    @Test
    void publisherRouteThrows() throws IOException {
        assertConnectionNotStalled("/unconsumed/publisher-throws", false);
    }

    @Test
    void inputStreamRouteThrows() throws IOException {
        assertConnectionNotStalled("/unconsumed/input-stream-throws", false);
    }

    @Test
    @Tag("multipart")
    void streamingUploadRouteThrows() throws IOException {
        assertConnectionNotStalled("/unconsumed/streaming-upload-throws", true);
    }

    @Test
    void publisherConsumedAfterReturn() throws IOException {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
             Socket socket = connect(server)) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(head("/unconsumed/publisher-later", false));
            out.flush();
            writeRestAsync(out, false, null);

            Response response = Response.read(in);
            assertEquals(200, response.status);
            assertEquals(String.valueOf(FIRST_LENGTH + REST_LENGTH), response.body);
        }
    }

    private static void assertConnectionNotStalled(String path, boolean multipart) throws IOException {
        assertConnectionNotStalled(path, multipart, false);
    }

    /**
     * @param bodyFirst Send the whole body before reading the rejection. A form route with an
     *                  unsatisfied argument only fails once the whole form has been received.
     */
    private static void assertConnectionNotStalled(String path, boolean multipart, boolean bodyFirst) throws IOException {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
             Socket socket = connect(server)) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(head(path, multipart));
            out.flush();
            String next = "GET /unconsumed/ok HTTP/1.1\r\nHost: localhost\r\n\r\n";
            if (bodyFirst) {
                writeRestAsync(out, multipart, next);
            }

            Response rejection = Response.read(in);
            assertEquals(400, rejection.status);

            try {
                if (!bodyFirst) {
                    // write on another thread, the server may not read all of it
                    writeRestAsync(out, multipart, next);
                }
                Response nextResponse = Response.read(in);
                assertEquals(200, nextResponse.status);
                assertEquals("ok", nextResponse.body);
            } catch (SocketTimeoutException e) {
                fail("The connection is stalled on the unread request body", e);
            } catch (EOFException | SocketException e) {
                // the server may close the connection instead of reading the rest of the body,
                // that's fine too
            }
        }
    }

    private static Socket connect(ServerUnderTest server) throws IOException {
        Assumptions.assumeTrue(server.getPort().isPresent() && server.getScheme().filter("http"::equals).isPresent(),
            "Test needs a plain HTTP/1.1 connection to the server");
        Socket socket = new Socket("localhost", server.getPort().get());
        socket.setSoTimeout(TIMEOUT_MILLIS);
        return socket;
    }

    private static byte[] head(String path, boolean multipart) {
        String contentType;
        String first;
        if (multipart) {
            contentType = "multipart/form-data; boundary=" + BOUNDARY;
            first = "--" + BOUNDARY + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"a.txt\"\r\n" +
                "Content-Type: text/plain\r\n" +
                "\r\n" +
                "x".repeat(FIRST_LENGTH);
        } else {
            contentType = MediaType.APPLICATION_OCTET_STREAM;
            first = "x".repeat(FIRST_LENGTH);
        }
        long contentLength = first.length() + REST_LENGTH + end(multipart).length();
        return ("POST " + path + " HTTP/1.1\r\n" +
            "Host: localhost\r\n" +
            "Content-Type: " + contentType + "\r\n" +
            "Content-Length: " + contentLength + "\r\n" +
            "\r\n" +
            first).getBytes(StandardCharsets.US_ASCII);
    }

    private static void writeRestAsync(OutputStream out, boolean multipart, String next) {
        CompletableFuture.runAsync(() -> {
            try {
                byte[] piece = "y".repeat(8192).getBytes(StandardCharsets.US_ASCII);
                for (int i = 0; i < REST_LENGTH; i += piece.length) {
                    out.write(piece, 0, Math.min(piece.length, REST_LENGTH - i));
                }
                out.write(end(multipart).getBytes(StandardCharsets.US_ASCII));
                if (next != null) {
                    out.write(next.getBytes(StandardCharsets.US_ASCII));
                }
                out.flush();
            } catch (IOException ignored) {
                // the socket was closed, by the server or at the end of the test
            }
        });
    }

    private static String end(boolean multipart) {
        return multipart ? "\r\n--" + BOUNDARY + "--\r\n" : "";
    }

    private record Response(int status, String body) {
        static Response read(InputStream in) throws IOException {
            String statusLine = readLine(in);
            int status = Integer.parseInt(statusLine.split(" ")[1]);
            long contentLength = -1;
            boolean chunked = false;
            for (String line = readLine(in); !line.isEmpty(); line = readLine(in)) {
                int colon = line.indexOf(':');
                String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(colon + 1).trim();
                if (name.equals("content-length")) {
                    contentLength = Long.parseLong(value);
                } else if (name.equals("transfer-encoding") && value.equalsIgnoreCase("chunked")) {
                    chunked = true;
                }
            }
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            if (chunked) {
                while (true) {
                    int size = Integer.parseInt(readLine(in).split(";")[0].trim(), 16);
                    if (size == 0) {
                        // skip trailers
                        String trailer = readLine(in);
                        while (!trailer.isEmpty()) {
                            trailer = readLine(in);
                        }
                        break;
                    }
                    body.write(in.readNBytes(size));
                    readLine(in);
                }
            } else if (contentLength > 0) {
                byte[] bytes = in.readNBytes((int) contentLength);
                if (bytes.length < contentLength) {
                    throw new EOFException();
                }
                body.write(bytes);
            }
            return new Response(status, body.toString(StandardCharsets.UTF_8));
        }

        private static String readLine(InputStream in) throws IOException {
            StringBuilder sb = new StringBuilder();
            while (true) {
                int b = in.read();
                if (b == -1) {
                    throw new EOFException();
                }
                if (b == '\n') {
                    int len = sb.length();
                    if (len > 0 && sb.charAt(len - 1) == '\r') {
                        sb.setLength(len - 1);
                    }
                    return sb.toString();
                }
                sb.append((char) b);
            }
        }
    }

    @Controller("/unconsumed")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class UnconsumedController {
        @Post(uri = "/publisher-unsatisfied", consumes = MediaType.APPLICATION_OCTET_STREAM)
        String publisherUnsatisfied(@Body Publisher<byte[]> body, @QueryValue String missing) {
            return "unreachable";
        }

        @Post(uri = "/publisher-conversion", consumes = MediaType.APPLICATION_OCTET_STREAM)
        String publisherConversion(@Body Publisher<byte[]> body, @QueryValue int value) {
            return "unreachable";
        }

        @Post(uri = "/input-stream-unsatisfied", consumes = MediaType.APPLICATION_OCTET_STREAM)
        String inputStreamUnsatisfied(@Body InputStream body, @QueryValue String missing) {
            return "unreachable";
        }

        @Post(uri = "/input-stream-conversion", consumes = MediaType.APPLICATION_OCTET_STREAM)
        String inputStreamConversion(@Body InputStream body, @QueryValue int value) {
            return "unreachable";
        }

        @Post(uri = "/streaming-upload-unsatisfied", consumes = MediaType.MULTIPART_FORM_DATA)
        String streamingUploadUnsatisfied(StreamingFileUpload file, @QueryValue String missing) {
            return "unreachable";
        }

        @Post(uri = "/streaming-upload-conversion", consumes = MediaType.MULTIPART_FORM_DATA)
        String streamingUploadConversion(StreamingFileUpload file, @QueryValue int value) {
            return "unreachable";
        }

        @Post(uri = "/part-publisher-unsatisfied", consumes = MediaType.MULTIPART_FORM_DATA)
        String partPublisherUnsatisfied(@Part("file") Publisher<PartData> file, @QueryValue String missing) {
            return "unreachable";
        }

        @Post(uri = "/multipart-body-unsatisfied", consumes = MediaType.MULTIPART_FORM_DATA)
        String multipartBodyUnsatisfied(@Body MultipartBody body, @QueryValue String missing) {
            return "unreachable";
        }

        @Post(uri = "/publisher-valid", consumes = MediaType.APPLICATION_OCTET_STREAM)
        String publisherValid(@Body Publisher<byte[]> body, @QueryValue @Min(10) int value) {
            return "unreachable";
        }

        @Post(uri = "/input-stream-valid", consumes = MediaType.APPLICATION_OCTET_STREAM)
        String inputStreamValid(@Body InputStream body, @QueryValue @Min(10) int value) {
            return "unreachable";
        }

        @Post(uri = "/streaming-upload-valid", consumes = MediaType.MULTIPART_FORM_DATA)
        String streamingUploadValid(StreamingFileUpload file, @QueryValue @Min(10) int value) {
            return "unreachable";
        }

        @Post(uri = "/part-publisher-valid", consumes = MediaType.MULTIPART_FORM_DATA)
        String partPublisherValid(@Part("file") Publisher<PartData> file, @QueryValue @Min(10) int value) {
            return "unreachable";
        }

        @Post(uri = "/publisher-throws", consumes = MediaType.APPLICATION_OCTET_STREAM)
        String publisherThrows(@Body Publisher<byte[]> body) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "rejected");
        }

        @Post(uri = "/input-stream-throws", consumes = MediaType.APPLICATION_OCTET_STREAM)
        String inputStreamThrows(@Body InputStream body) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "rejected");
        }

        @Post(uri = "/streaming-upload-throws", consumes = MediaType.MULTIPART_FORM_DATA)
        String streamingUploadThrows(StreamingFileUpload file) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "rejected");
        }

        @Post(uri = "/publisher-later", consumes = MediaType.APPLICATION_OCTET_STREAM, produces = MediaType.TEXT_PLAIN)
        Mono<String> publisherLater(@Body Publisher<byte[]> body) {
            // the body is only subscribed to after the route returned
            return Flux.from(body).map(b -> (long) b.length).reduce(0L, Long::sum).map(String::valueOf);
        }

        @Get(uri = "/ok", produces = MediaType.TEXT_PLAIN)
        String ok() {
            return "ok";
        }
    }
}
