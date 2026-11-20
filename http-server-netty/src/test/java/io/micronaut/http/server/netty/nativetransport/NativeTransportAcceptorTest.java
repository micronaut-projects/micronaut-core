package io.micronaut.http.server.netty.nativetransport;

import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import io.netty.channel.epoll.Epoll;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The server channel type is derived from the worker event loop configuration, so the acceptor
 * ("parent") group created by the server has to use the same transport as the worker group.
 */
class NativeTransportAcceptorTest {

    @Test
    void acceptorFollowsNativeWorkerTransport() throws Exception {
        assumeTrue(Epoll.isAvailable(), "epoll is not available on this platform");
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.<String, Object>of(
            "spec.name", "NativeTransportAcceptorTest",
            "micronaut.server.port", -1,
            "micronaut.netty.event-loops.default.transport", "epoll,nio"
        ));
        try {
            // starting the server used to fail with "incompatible event loop type" because the acceptor
            // group stayed on NIO; now the acceptor and the worker both run on epoll
            HttpURLConnection connection = (HttpURLConnection) URI.create(server.getURL() + "/missing").toURL().openConnection();
            assertEquals(404, connection.getResponseCode());
        } finally {
            server.stop();
        }
    }
}
