package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.runtime.server.EmbeddedServer;
import io.netty.channel.Channel;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A listener given a socket that outlives the server accepts on it, leaves it bound when it stops, and a
 * connection made while no server accepts waits in the backlog for the next server.
 */
class RetainedServerSocketsTest {
    static final String SPEC = "RetainedServerSocketsTest";

    @Test
    void theNextServerServesWhatArrivedWhileNoneAccepted() throws Exception {
        Sockets sockets = new Sockets();
        try {
            ApplicationContext first = start(sockets, "one");
            int port = first.getBean(EmbeddedServer.class).getPort();
            assertEquals(sockets.socket.socket().getLocalPort(), port);
            assertEquals("one", body(get(port)));

            first.close();
            // the server stopped accepting, the socket is still bound: a client connects and sends its request
            assertTrue(sockets.socket.isOpen());
            assertFalse(sockets.channels.get(0).isOpen());
            try (Socket waiting = new Socket("localhost", port)) {
                waiting.getOutputStream().write(request());

                ApplicationContext second = start(sockets, "two");
                try {
                    assertEquals(port, second.getBean(EmbeddedServer.class).getPort());
                    assertEquals("two", body(new String(waiting.getInputStream().readAllBytes(), StandardCharsets.UTF_8)));
                    assertEquals("two", body(get(port)));
                    assertEquals(2, sockets.channels.size());
                    assertSame(sockets.socket, sockets.accepted.get(1));
                } finally {
                    second.close();
                }
            }
            assertTrue(sockets.socket.isOpen());
        } finally {
            sockets.socket.close();
        }
    }

    private static ApplicationContext start(Sockets sockets, String name) {
        ApplicationContext context = ApplicationContext.builder(Map.of("spec.name", SPEC, "generation.name", name))
            .singletons(sockets)
            .start();
        context.getBean(EmbeddedServer.class).start();
        return context;
    }

    private static byte[] request() {
        return "GET /retained HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
    }

    private static String get(int port) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            socket.getOutputStream().write(request());
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String body(String response) {
        assertTrue(response.startsWith("HTTP/1.1 200"), response);
        return response.substring(response.indexOf("\r\n\r\n") + 4);
    }

    static final class Sockets implements RetainedServerSockets {
        final ServerSocketChannel socket;
        final List<ServerSocketChannel> accepted = new CopyOnWriteArrayList<>();
        final List<Channel> channels = new CopyOnWriteArrayList<>();

        Sockets() throws IOException {
            socket = ServerSocketChannel.open().bind(new InetSocketAddress("localhost", 0));
        }

        @Override
        public ServerSocketChannel serverSocket(@Nullable String host, int port) {
            return socket;
        }

        @Override
        public void accepting(ServerSocketChannel socket, Channel channel) {
            accepted.add(socket);
            channels.add(channel);
        }
    }

    @Controller("/retained")
    @Requires(property = "spec.name", value = SPEC)
    static class RetainedController {
        @Value("${generation.name}")
        String name;

        @Get
        @Produces("text/plain")
        String get() {
            return name;
        }
    }
}
