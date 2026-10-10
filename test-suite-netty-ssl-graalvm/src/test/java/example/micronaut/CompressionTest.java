package example.micronaut;

import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compressing a response initializes Netty's {@code ReferenceCountUtil}, whose static initializer looks up its own
 * declared methods reflectively. The request is written over a plain socket so that the test sees the compressed
 * response as the server sent it.
 */
@MicronautTest
class CompressionTest {

    @Inject
    EmbeddedServer server;

    @Test
    void testGzipResponse() throws IOException {
        // the second request fails with NoClassDefFoundError if the first one failed to initialize the class
        for (int i = 0; i < 2; i++) {
            byte[] response = get("/compression");
            int headerEnd = indexOf(response, "\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            assertTrue(headerEnd > 0);
            String headers = new String(response, 0, headerEnd, StandardCharsets.US_ASCII).toLowerCase();
            assertTrue(headers.startsWith("http/1.1 200"), headers);
            assertTrue(headers.contains("content-encoding: gzip"), headers);

            byte[] body = Arrays.copyOfRange(response, headerEnd + 4, response.length);
            if (headers.contains("transfer-encoding: chunked")) {
                body = dechunk(body);
            }
            try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(body))) {
                assertEquals(CompressionController.BODY, new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    private byte[] get(String path) throws IOException {
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            OutputStream out = socket.getOutputStream();
            out.write(("GET " + path + " HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Accept-Encoding: gzip\r\n" +
                "Connection: close\r\n" +
                "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            return socket.getInputStream().readAllBytes();
        }
    }

    private static byte[] dechunk(byte[] chunked) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int pos = 0;
        byte[] crlf = "\r\n".getBytes(StandardCharsets.US_ASCII);
        while (true) {
            int lineEnd = pos + indexOf(Arrays.copyOfRange(chunked, pos, chunked.length), crlf);
            String sizeLine = new String(chunked, pos, lineEnd - pos, StandardCharsets.US_ASCII);
            int extension = sizeLine.indexOf(';');
            int size = Integer.parseInt((extension == -1 ? sizeLine : sizeLine.substring(0, extension)).trim(), 16);
            if (size == 0) {
                return out.toByteArray();
            }
            out.write(chunked, lineEnd + 2, size);
            pos = lineEnd + 2 + size + 2;
        }
    }

    private static int indexOf(byte[] array, byte[] target) {
        outer:
        for (int i = 0; i <= array.length - target.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
