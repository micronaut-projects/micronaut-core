package io.micronaut.http.server.netty.multipart;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Group 11 members that need a running server.
 */
class FormLimitsBypassTest {
    private static final String SPEC_NAME = "FormLimitsBypassTest";

    /**
     * ANT-2026-HT5Q9GDG: a part's own Content-Length header must not lift field-max-bytes and
     * form-max-bytes for that part.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void partContentLengthDoesNotLiftFieldLimit(boolean partHeader) throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.server.port", -1,
            "micronaut.server.netty.field-max-bytes", 1024,
            "micronaut.server.netty.form-max-bytes", 2048))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            int size = 50_000;
            String head = "--boundary\r\nContent-Disposition: form-data; name=\"data\"; filename=\"a.bin\"\r\n"
                + "Content-Type: application/octet-stream\r\n"
                + (partHeader ? "Content-Length: " + size + "\r\n" : "") + "\r\n";
            String tail = "\r\n--boundary--\r\n";
            byte[] request = RawHttp.withBody(
                "POST /g11/bytes HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\nContent-Type: multipart/form-data; boundary=boundary\r\n",
                (head + "a".repeat(size) + tail).getBytes(StandardCharsets.ISO_8859_1));
            // the part data has to span more than one read, the first one before the hand-over
            // to streaming
            int split = request.length - size - tail.length() + 512;
            String response;
            try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
                socket.setSoTimeout(10_000);
                OutputStream out = socket.getOutputStream();
                out.write(request, 0, split);
                out.flush();
                Thread.sleep(200);
                try {
                    out.write(request, split, request.length - split);
                    out.flush();
                } catch (IOException ignored) {
                    // the server may already have answered and closed
                }
                ByteArrayOutputStream received = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                try {
                    int n;
                    while ((n = socket.getInputStream().read(buf)) != -1) {
                        received.write(buf, 0, n);
                    }
                } catch (IOException ignored) {
                    // a reset after the response, the server closes without reading the rest
                }
                response = received.toString(StandardCharsets.ISO_8859_1);
            }
            assertTrue(response.startsWith("HTTP/1.1 413"), "a 50 KB part over a 1 KiB field-max-bytes was accepted: " + response.lines().findFirst().orElse("") + " " + tail(response));
        }
    }

    /**
     * A part's Content-Length that is not a number is a malformed request, not a server error.
     */
    @Test
    void nonNumericPartContentLength() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            String body = "--boundary\r\nContent-Disposition: form-data; name=\"data\"; filename=\"a.bin\"\r\n"
                + "Content-Length: abc\r\n\r\nxyz\r\n--boundary--\r\n";
            String response = RawHttp.exchange(server.getPort(), 10_000, RawHttp.withBody(
                "POST /g11/bytes HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\nContent-Type: multipart/form-data; boundary=boundary\r\n",
                body.getBytes(StandardCharsets.ISO_8859_1)));
            assertTrue(response.startsWith("HTTP/1.1 200"), response.lines().findFirst().orElse("") + " " + tail(response));
        }
    }

    /**
     * ANT-2026-I00GGIRT: the undecoded form limit is 4 MiB, and the decoder rescans everything it
     * holds back for every piece of body, on the event loop. A long field name followed by many
     * small chunks must not cost seconds of event loop time.
     */
    @Test
    void tinyChunksAfterLongFieldNameDoNotStallEventLoop() throws Exception {
        assertTrue(chunkedFormMillis("") < 5000, "control");
        long millis = chunkedFormMillis(null);
        assertTrue(millis < 5000, "a form with a 2 MB field name and 2000 one-byte chunks took " + millis + " ms");
    }

    private static long chunkedFormMillis(String prefix) throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            ByteArrayOutputStream req = new ByteArrayOutputStream();
            req.writeBytes(RawHttp.ascii("POST /g11/people HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\nTransfer-Encoding: chunked\r\n\r\n"));
            String first = (prefix == null ? "" : "firstName=") + "a".repeat(2_000_000);
            req.writeBytes(RawHttp.ascii(Integer.toHexString(first.length()) + "\r\n" + first + "\r\n"));
            for (int i = 0; i < 2000; i++) {
                req.writeBytes(RawHttp.ascii("1\r\na\r\n"));
            }
            String last = "&lastName=b";
            req.writeBytes(RawHttp.ascii(Integer.toHexString(last.length()) + "\r\n" + last + "\r\n0\r\n\r\n"));
            long start = System.nanoTime();
            String response = RawHttp.exchange(server.getPort(), 120_000, req.toByteArray());
            long millis = (System.nanoTime() - start) / 1_000_000;
            assertTrue(response.startsWith("HTTP/1.1"), "no response: " + response);
            return millis;
        }
    }

    private static String tail(String response) {
        int i = response.indexOf("\r\n\r\n");
        return i < 0 ? "" : response.substring(i + 4, Math.min(response.length(), i + 204));
    }

    @Controller("/g11")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Ctrl {
        @Post(value = "/bytes", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        @ExecuteOn(TaskExecutors.BLOCKING)
        String bytes(@Part("data") byte[] data) {
            return "received " + data.length;
        }

        @Post(value = "/people", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        String people(@io.micronaut.core.annotation.Nullable String firstName, @io.micronaut.core.annotation.Nullable String lastName) {
            return "ok";
        }
    }
}
