package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.context.event.HttpRequestLog;
import io.micronaut.http.context.event.HttpRequestTerminatedEvent;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Transport facts of the request log on the Netty server: HTTP/2, a client that goes away while
 * the response streams, an aggregated request body, a malformed request head, and the default of
 * keeping no log.
 */
class RequestLogNettyTest {

    @Test
    void http2() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", "RequestLogNettyTest",
            "micronaut.server.netty.request-log-enabled", true,
            "micronaut.server.port", -1,
            "micronaut.server.http-version", "2.0",
            "micronaut.server.ssl.enabled", false,
            "micronaut.http.client.plaintext-mode", "h2c_prior_knowledge"));
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
            Recorder recorder = ctx.getBean(Recorder.class);
            HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/request-log-netty/ok"), String.class);
            Assertions.assertEquals("ok", response.body());
            HttpRequestLog log = recorder.await();
            Assertions.assertEquals(HttpVersion.HTTP_2_0, log.httpVersion().orElseThrow());
            Assertions.assertEquals(200, log.status().getAsInt());
            Assertions.assertEquals("/request-log-netty/ok", log.routeTemplate().orElseThrow());
            Assertions.assertTrue(log.isCompleted());
            Assertions.assertEquals(0, log.requestBodyBytes().getAsLong());
            Assertions.assertEquals(2, log.responseBodyBytes().getAsLong());
            Assertions.assertTrue(log.responseWrittenNanos().getAsLong() >= log.responseProducedNanos().getAsLong());
            Assertions.assertTrue(log.responseProducedNanos().getAsLong() >= log.requestStartNanos().getAsLong());
        }
    }

    @Test
    void clientDisconnectMidResponse() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", "RequestLogNettyTest",
            "micronaut.server.netty.request-log-enabled", true,
            "micronaut.server.port", -1));
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start()) {
            Recorder recorder = ctx.getBean(Recorder.class);
            try (Socket socket = new Socket(server.getHost(), server.getPort())) {
                OutputStream out = socket.getOutputStream();
                out.write("GET /request-log-netty/endless HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                out.flush();
                InputStream in = socket.getInputStream();
                // wait for the start of the response, then go away
                byte[] buf = new byte[64];
                Assertions.assertTrue(in.read(buf) > 0);
            }
            HttpRequestLog log = recorder.await();
            Assertions.assertEquals(200, log.status().getAsInt());
            Assertions.assertFalse(log.isCompleted());
            Assertions.assertTrue(log.responseWrittenNanos().isEmpty());
            HttpRequestLog.FailurePhase phase = log.failurePhase().orElseThrow();
            Assertions.assertTrue(phase == HttpRequestLog.FailurePhase.CLIENT_DISCONNECT || phase == HttpRequestLog.FailurePhase.WRITING_RESPONSE, phase::toString);
            Assertions.assertTrue(log.responseBodyBytes().getAsLong() > 0);
        }
    }

    @Test
    void disabledByDefault() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", "RequestLogNettyTest",
            "micronaut.server.port", -1));
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
            Recorder recorder = ctx.getBean(Recorder.class);
            client.toBlocking().exchange(HttpRequest.GET("/request-log-netty/ok"), String.class);
            HttpRequestTerminatedEvent event = recorder.event.get(10, TimeUnit.SECONDS);
            Assertions.assertTrue(event.getRequestLog().isEmpty());
        }
    }

    @Test
    void aggregatedRequestBody() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", "RequestLogNettyTest",
            "micronaut.server.netty.request-log-enabled", true,
            "micronaut.server.netty.server-type", "full_content",
            "micronaut.server.port", -1));
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
            Recorder recorder = ctx.getBean(Recorder.class);
            HttpResponse<String> response = client.toBlocking().exchange(
                HttpRequest.POST("/request-log-netty/echo", "hello world").contentType(MediaType.TEXT_PLAIN), String.class);
            Assertions.assertEquals("hello world", response.body());
            HttpRequestLog log = recorder.await();
            Assertions.assertEquals(11, log.requestBodyBytes().getAsLong());
            Assertions.assertEquals(11, log.responseBodyBytes().getAsLong());
        }
    }

    @Test
    void malformedRequestHead() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", "RequestLogNettyTest",
            "micronaut.server.netty.request-log-enabled", true,
            "micronaut.server.port", -1));
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start()) {
            Recorder recorder = ctx.getBean(Recorder.class);
            try (Socket socket = new Socket(server.getHost(), server.getPort())) {
                OutputStream out = socket.getOutputStream();
                // a header line without a colon fails to decode
                out.write("GET /request-log-netty/ok HTTP/1.1\r\nHost: localhost\r\nbroken header\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                out.flush();
                byte[] buf = new byte[12];
                Assertions.assertTrue(socket.getInputStream().readNBytes(buf, 0, 12) == 12);
                Assertions.assertEquals("HTTP/1.1 400", new String(buf, StandardCharsets.US_ASCII));
            }
            HttpRequestLog log = recorder.await();
            Assertions.assertEquals(400, log.status().getAsInt());
            Assertions.assertEquals(HttpRequestLog.FailurePhase.READING_REQUEST, log.failurePhase().orElseThrow());
            Assertions.assertTrue(log.isCompleted());
        }
    }

    @Controller("/request-log-netty")
    @Requires(property = "spec.name", value = "RequestLogNettyTest")
    static class Route {
        @Get("/ok")
        @Produces(MediaType.TEXT_PLAIN)
        String ok() {
            return "ok";
        }

        @Post("/echo")
        @Consumes(MediaType.TEXT_PLAIN)
        @Produces(MediaType.TEXT_PLAIN)
        String echo(@Body String body) {
            return body;
        }

        @Get("/endless")
        @Produces(MediaType.TEXT_PLAIN)
        Flux<byte[]> endless() {
            byte[] chunk = new byte[1024];
            return Flux.interval(Duration.ofMillis(5)).map(i -> chunk);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "RequestLogNettyTest")
    static class Recorder implements ApplicationEventListener<HttpRequestTerminatedEvent> {
        final CompletableFuture<HttpRequestTerminatedEvent> event = new CompletableFuture<>();

        HttpRequestLog await() throws Exception {
            return event.get(10, TimeUnit.SECONDS).getRequestLog().orElseThrow();
        }

        @Override
        public void onApplicationEvent(HttpRequestTerminatedEvent e) {
            event.complete(e);
        }
    }
}
