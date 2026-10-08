package io.micronaut.http.client.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawRequestOptions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The read idle timeout of a raw exchange lasts for the whole exchange: across interim responses
 * and redirects.
 */
class RawReadIdleTimeoutExchangeTest {

    @Test
    void anInterimResponseKeepsTheReadIdleTimeout() throws Exception {
        try (SlowServer server = new SlowServer("HTTP/1.1 103 Early Hints\r\nLink: </style.css>; rel=preload\r\n\r\n")) {
            Assertions.assertEquals("slow", exchange(server.uri("/slow")));
        }
    }

    @Test
    void aRedirectKeepsTheReadIdleTimeout() throws Exception {
        try (SlowServer server = new SlowServer(null)) {
            Assertions.assertEquals("slow", exchange(server.uri("/redirect")));
        }
    }

    private static String exchange(String uri) throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("micronaut.http.client.read-timeout", "500ms"));
             RawHttpClient client = ctx.createBean(RawHttpClient.class);
             ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) client.toAsyncRaw()
                 .exchange(HttpRequest.GET(uri), null, RawRequestOptions.builder().readIdleTimeout(Duration.ofSeconds(5)).build())
                 .toCompletableFuture().get(10, TimeUnit.SECONDS)) {
            Assertions.assertEquals(200, response.code());
            return response.byteBody().buffer().get(5, TimeUnit.SECONDS).toString(StandardCharsets.UTF_8);
        }
    }

    /**
     * Redirects /redirect to /slow, and answers /slow after 1.5 seconds, optionally after an
     * interim response.
     */
    private static final class SlowServer implements AutoCloseable {
        private final ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        private final Thread thread;

        SlowServer(String interim) throws Exception {
            thread = new Thread(() -> {
                while (!socket.isClosed()) {
                    try {
                        Socket connection = socket.accept();
                        Thread.startVirtualThread(() -> serve(connection, interim));
                    } catch (Exception ignored) {
                        return;
                    }
                }
            });
            thread.start();
        }

        String uri(String path) {
            return "http://127.0.0.1:" + socket.getLocalPort() + path;
        }

        private void serve(Socket connection, String interim) {
            try (connection) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.US_ASCII));
                OutputStream out = connection.getOutputStream();
                String line;
                while ((line = in.readLine()) != null) {
                    String path = line.split(" ")[1];
                    while (!in.readLine().isEmpty()) {
                        // skip the headers
                    }
                    if (path.equals("/redirect")) {
                        out.write("HTTP/1.1 307 Temporary Redirect\r\nLocation: /slow\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                        out.flush();
                        continue;
                    }
                    if (interim != null) {
                        out.write(interim.getBytes(StandardCharsets.US_ASCII));
                        out.flush();
                    }
                    Thread.sleep(1500);
                    out.write("HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\nslow".getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                }
            } catch (Exception ignored) {
            }
        }

        @Override
        public void close() throws Exception {
            socket.close();
            thread.join();
        }
    }
}
