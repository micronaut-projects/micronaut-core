package io.micronaut.http.client.jdk;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.StreamingHttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The JDK client reads streamed JSON, a JSON stream or the elements of a JSON array, with the
 * readers of json-core alone: neither Netty nor the Netty modules of Micronaut are on the class
 * path of this test, and the server is the HTTP server of the JDK. The body arrives in pieces
 * that split the values.
 */
class JdkClientJsonStreamWithoutNettyTest {

    private static final List<Book> BOOKS = List.of(new Book("The Stand"), new Book("It"));

    private static HttpServer server;
    private static URL url;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/stream", exchange -> respond(exchange, "application/x-json-stream",
            "{\"title\":\"The St", "and\"}\n{\"tit", "le\":\"It\"}\n"));
        server.createContext("/array", exchange -> respond(exchange, "application/json",
            "[{\"title\":\"The St", "and\"},{\"tit", "le\":\"It\"}]"));
        server.start();
        url = URI.create("http://127.0.0.1:" + server.getAddress().getPort()).toURL();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    /**
     * A chunked response, each piece flushed on its own.
     */
    private static void respond(HttpExchange exchange, String contentType, String... pieces) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream out = exchange.getResponseBody()) {
            for (String piece : pieces) {
                out.write(piece.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        }
    }

    @Test
    void nettyIsNotOnTheClassPath() {
        ClassLoader loader = JdkClientJsonStreamWithoutNettyTest.class.getClassLoader();
        assertNull(loader.getResource("io/netty/buffer/ByteBuf.class"));
        assertNull(loader.getResource("io/micronaut/http/netty/body/NettyByteBodyFactory.class"));
    }

    @Test
    void aJsonStreamIsReadAsAPublisher() {
        try (HttpClient client = HttpClient.create(url)) {
            assertEquals(BOOKS, Flux.from(streaming(client).jsonStream(HttpRequest.GET("/stream"), Book.class)).collectList().block());
        }
    }

    @Test
    void theElementsOfAnArrayAreReadAsAPublisher() {
        try (HttpClient client = HttpClient.create(url)) {
            assertEquals(BOOKS, Flux.from(streaming(client).jsonStream(HttpRequest.GET("/array"), Book.class)).collectList().block());
        }
    }

    @Test
    void aJsonStreamIsReadAsTheElementsOfTheAsyncClient() {
        try (HttpClient client = HttpClient.create(url)) {
            assertEquals(BOOKS, pull(streaming(client), "/stream"));
            assertEquals(BOOKS, pull(streaming(client), "/array"));
        }
    }

    @Test
    void theClientOfAnApplicationContextReadsAJsonStream() {
        try (ApplicationContext context = ApplicationContext.run();
             HttpClient client = context.createBean(HttpClient.class, url)) {
            assertEquals(BOOKS, Flux.from(streaming(client).jsonStream(HttpRequest.GET("/stream"), Book.class)).collectList().block());
            assertEquals(BOOKS, Flux.from(streaming(client).jsonStream(HttpRequest.GET("/array"), Book.class)).collectList().block());
            assertEquals(BOOKS, pull(streaming(client), "/stream"));
        }
    }

    private static StreamingHttpClient streaming(HttpClient client) {
        return assertInstanceOf(StreamingHttpClient.class, client);
    }

    private static List<Book> pull(StreamingHttpClient client, String path) {
        List<Book> books = new ArrayList<>();
        try (BodyElements<Book> elements = client.toAsyncStreaming().jsonStream(HttpRequest.GET(path), Argument.of(Book.class)).toCompletableFuture().join()) {
            for (Optional<Book> next = elements.next().toCompletableFuture().join(); next.isPresent();
                 next = elements.next().toCompletableFuture().join()) {
                books.add(next.get());
            }
        }
        return books;
    }

    record Book(String title) {
    }
}
