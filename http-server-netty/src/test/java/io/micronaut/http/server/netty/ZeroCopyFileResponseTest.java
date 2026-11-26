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
package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.micronaut.http.server.types.files.SystemFile;
import io.micronaut.runtime.server.EmbeddedServer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.handler.logging.ByteBufFormat;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import javax.net.ssl.SSLContext;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A file response over a plain HTTP/1.x connection is sent from the file directly to the
 * connection, as a {@code FileRegion}. A compressed response, a TLS connection, or a server that
 * disables it reads the file as a stream.
 */
class ZeroCopyFileResponseTest {
    private static final String SPEC_NAME = "ZeroCopyFileResponseTest";
    private static final int FILE_SIZE = 1024 * 1024 + 17;

    private static final int LARGE_FILE_SIZE = 32 * 1024 * 1024;
    private static final String RECORDER_LOGGER = "io.micronaut.http.server.netty.ZeroCopyFileResponseTest.recorder";

    private static File file;
    private static byte[] contents;
    private static File largeFile;
    private static byte[] largeContents;

    @BeforeAll
    static void createFile() throws IOException {
        contents = new byte[FILE_SIZE];
        new Random(42).nextBytes(contents);
        file = File.createTempFile("zero-copy", ".bin");
        file.deleteOnExit();
        Files.write(file.toPath(), contents);
        largeContents = new byte[LARGE_FILE_SIZE];
        new Random(7).nextBytes(largeContents);
        largeFile = File.createTempFile("zero-copy-large", ".bin");
        largeFile.deleteOnExit();
        Files.write(largeFile.toPath(), largeContents);
    }

    @AfterAll
    static void deleteFile() {
        if (file != null) {
            file.delete();
        }
        if (largeFile != null) {
            largeFile.delete();
        }
    }

    @Test
    void fileIsSentAsRegion() throws IOException {
        try (Server server = Server.start(Map.of())) {
            Response response = server.exchange("GET /zero-copy/file HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
            assertEquals(200, response.status);
            assertEquals(String.valueOf(FILE_SIZE), response.header("content-length"));
            assertNull(response.header("transfer-encoding"));
            assertArrayEquals(contents, response.body);
            assertEquals(1, server.recorder().regions.get());
        }
    }

    @Test
    void systemFileIsSentAsRegion() throws IOException {
        try (Server server = Server.start(Map.of())) {
            Response response = server.exchange("GET /zero-copy/system-file HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
            assertEquals(200, response.status);
            assertArrayEquals(contents, response.body);
            assertEquals(1, server.recorder().regions.get());
        }
    }

    @Test
    void rangeIsSentAsRegion() throws IOException {
        try (Server server = Server.start(Map.of())) {
            Response response = server.exchange("GET /zero-copy/file HTTP/1.1\r\nHost: localhost\r\nRange: bytes=1000-200999\r\nConnection: close\r\n\r\n");
            assertEquals(206, response.status);
            assertEquals("bytes 1000-200999/" + FILE_SIZE, response.header("content-range"));
            assertEquals("200000", response.header("content-length"));
            assertArrayEquals(Arrays.copyOfRange(contents, 1000, 201000), response.body);
            assertEquals(1, server.recorder().regions.get());
        }
    }

    @Test
    void pipelinedResponsesAreSentInOrder() throws IOException {
        try (Server server = Server.start(Map.of())) {
            try (Socket socket = server.socket()) {
                socket.getOutputStream().write((
                    "GET /zero-copy/file HTTP/1.1\r\nHost: localhost\r\nRange: bytes=0-9\r\n\r\n" +
                        "GET /zero-copy/text HTTP/1.1\r\nHost: localhost\r\n\r\n" +
                        "GET /zero-copy/file HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"
                ).getBytes(StandardCharsets.US_ASCII));
                InputStream in = socket.getInputStream();
                Response first = Response.read(in);
                Response second = Response.read(in);
                Response third = Response.read(in);
                assertEquals(206, first.status);
                assertArrayEquals(Arrays.copyOfRange(contents, 0, 10), first.body);
                assertEquals("text", new String(second.body, StandardCharsets.UTF_8));
                assertEquals(200, third.status);
                assertArrayEquals(contents, third.body);
            }
            assertEquals(2, server.recorder().regions.get());
        }
    }

    @Test
    void http10ResponseIsSentAsRegion() throws IOException {
        try (Server server = Server.start(Map.of())) {
            Response response = server.exchange("GET /zero-copy/file HTTP/1.0\r\nHost: localhost\r\n\r\n");
            assertEquals(200, response.status);
            assertArrayEquals(contents, response.body);
            assertEquals(1, server.recorder().regions.get());
        }
    }

    @Test
    void compressedFileIsStreamed() throws IOException {
        try (Server server = Server.start(Map.of())) {
            Response response = server.exchange("GET /zero-copy/text-file HTTP/1.1\r\nHost: localhost\r\nAccept-Encoding: gzip\r\nConnection: close\r\n\r\n");
            assertEquals(200, response.status);
            assertEquals("gzip", response.header("content-encoding"));
            try (GZIPInputStream gzip = new GZIPInputStream(new java.io.ByteArrayInputStream(response.body))) {
                assertArrayEquals(contents, gzip.readAllBytes());
            }
            assertEquals(0, server.recorder().regions.get());
        }
    }

    @Test
    void disabledServerStreamsFile() throws IOException {
        try (Server server = Server.start(Map.of("micronaut.server.netty.zero-copy-file-responses", false))) {
            Response response = server.exchange("GET /zero-copy/file HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
            assertEquals(200, response.status);
            assertEquals(String.valueOf(FILE_SIZE), response.header("content-length"));
            assertArrayEquals(contents, response.body);
            assertEquals(0, server.recorder().regions.get());
        }
    }

    @Test
    void tlsConnectionStreamsFile() throws Exception {
        try (Server server = Server.start(Map.of(
            "micronaut.server.ssl.enabled", true,
            "micronaut.server.ssl.build-self-signed", true,
            "micronaut.server.ssl.port", -1
        ))) {
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, InsecureTrustManagerFactory.INSTANCE.getTrustManagers(), null);
            try (Socket socket = ssl.getSocketFactory().createSocket(server.server.getHost(), server.server.getPort())) {
                socket.setSoTimeout(10_000);
                socket.getOutputStream().write("GET /zero-copy/file HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                Response response = Response.read(socket.getInputStream());
                assertEquals(200, response.status);
                assertArrayEquals(contents, response.body);
            }
            assertEquals(0, server.recorder().regions.get());
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void epollSendsRegion() throws IOException {
        try (Server server = Server.start(Map.of("micronaut.server.netty.use-native-transport", true))) {
            Response response = server.exchange("GET /zero-copy/file HTTP/1.1\r\nHost: localhost\r\nRange: bytes=7-\r\nConnection: close\r\n\r\n");
            assertEquals(206, response.status);
            assertArrayEquals(Arrays.copyOfRange(contents, 7, FILE_SIZE), response.body);
            assertEquals(1, server.recorder().regions.get());
        }
    }

    @Test
    void applicationOutboundHandlerStreamsFile() throws IOException {
        try (Server server = Server.start(Map.of("spec.outbound-handler", true))) {
            Response response = server.exchange("GET /zero-copy/file HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
            assertEquals(200, response.status);
            assertArrayEquals(contents, response.body);
            assertEquals(0, server.recorder().regions.get());
        }
    }

    @Test
    void accessLogCountsTheBytesOfTheRegion() throws IOException {
        String loggerName = "ZeroCopyFileResponseTest.access";
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(loggerName);
        java.util.List<String> lines = new java.util.concurrent.CopyOnWriteArrayList<>();
        ch.qos.logback.core.AppenderBase<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ch.qos.logback.core.AppenderBase<>() {
            @Override
            protected void append(ch.qos.logback.classic.spi.ILoggingEvent event) {
                lines.add(event.getFormattedMessage());
            }
        };
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
        try (Server server = Server.start(Map.of(
            "micronaut.server.netty.access-logger.enabled", true,
            "micronaut.server.netty.access-logger.logger-name", loggerName,
            "micronaut.server.netty.access-logger.log-format", "%s %b"
        ))) {
            Response response = server.exchange("GET /zero-copy/file HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
            assertEquals(200, response.status);
            assertArrayEquals(contents, response.body);
            assertEquals(1, server.recorder().regions.get());
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (lines.isEmpty() && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertEquals(List.of("200 " + FILE_SIZE), lines);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void slowDownloadOutlivesIdleTimeout() throws Exception {
        try (Server server = Server.start(Map.of("micronaut.server.idle-timeout", "500ms"))) {
            try (Socket socket = server.socket()) {
                socket.setReceiveBufferSize(64 * 1024);
                socket.getOutputStream().write("GET /zero-copy/large-file HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                // about 2.5 seconds, five times the idle timeout
                Response response = Response.read(new SlowInputStream(socket.getInputStream(), LARGE_FILE_SIZE / 25, 100));
                assertEquals(200, response.status);
                assertArrayEquals(largeContents, response.body);
            }
            assertEquals(1, server.recorder().regions.get());
        }
    }

    @Test
    void gracefulShutdownWaitsForTransfer() throws Exception {
        try (Server server = Server.start(Map.of())) {
            try (Socket socket = server.socket()) {
                socket.setReceiveBufferSize(64 * 1024);
                socket.getOutputStream().write("GET /zero-copy/large-file HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                InputStream in = new SlowInputStream(socket.getInputStream(), LARGE_FILE_SIZE / 10, 100);
                // the response has started when the first byte arrives
                in = new java.io.PushbackInputStream(in);
                int first = in.read();
                ((java.io.PushbackInputStream) in).unread(first);
                java.util.concurrent.CompletionStage<?> shutdown = ((NettyHttpServer) server.server).shutdownGracefully();
                Response response = Response.read(in);
                assertEquals(200, response.status);
                assertArrayEquals(largeContents, response.body);
                // the connection closes after the response
                assertEquals(-1, socket.getInputStream().read());
                shutdown.toCompletableFuture().get(10, java.util.concurrent.TimeUnit.SECONDS);
            }
            assertEquals(1, server.recorder().regions.get());
        }
    }

    @Test
    void clientDisconnectDuringDownloadIsNotAWarning() throws Exception {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("io.micronaut.http.server.netty.handler.PipeliningServerHandler");
        java.util.List<String> warnings = new java.util.concurrent.CopyOnWriteArrayList<>();
        ch.qos.logback.core.AppenderBase<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ch.qos.logback.core.AppenderBase<>() {
            @Override
            protected void append(ch.qos.logback.classic.spi.ILoggingEvent event) {
                if (event.getLevel().isGreaterOrEqual(ch.qos.logback.classic.Level.WARN)) {
                    warnings.add(event.getFormattedMessage());
                }
            }
        };
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
        try (Server server = Server.start(Map.of())) {
            try (Socket socket = server.socket()) {
                socket.setReceiveBufferSize(64 * 1024);
                // the second response waits behind the first one, and is discarded with the connection
                socket.getOutputStream().write((
                    "GET /zero-copy/large-file HTTP/1.1\r\nHost: localhost\r\n\r\n" +
                        "GET /zero-copy/large-file HTTP/1.1\r\nHost: localhost\r\n\r\n"
                ).getBytes(StandardCharsets.US_ASCII));
                socket.getInputStream().readNBytes(1024 * 1024);
                // reset the connection in the middle of the download
                socket.setSoLinger(true, 0);
            }
            // the next request on a new connection is answered once the server handled the reset
            Response response = server.exchange("GET /zero-copy/text HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
            assertEquals(200, response.status);
            Thread.sleep(500);
            assertEquals(1, server.recorder().regions.get());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
        assertEquals(java.util.List.of(), warnings);
    }

    /**
     * Reads a stream slowly: after each block of bytes, it waits.
     */
    private static final class SlowInputStream extends java.io.FilterInputStream {
        private final int blockSize;
        private final long pauseMillis;
        private int inBlock;

        SlowInputStream(InputStream in, int blockSize, long pauseMillis) {
            super(in);
            this.blockSize = blockSize;
            this.pauseMillis = pauseMillis;
        }

        private void count(int n) throws IOException {
            inBlock += n;
            if (inBlock >= blockSize) {
                inBlock = 0;
                try {
                    Thread.sleep(pauseMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new java.io.InterruptedIOException();
                }
            }
        }

        @Override
        public int read() throws IOException {
            int c = super.read();
            if (c >= 0) {
                count(1);
            }
            return c;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, Math.min(len, 64 * 1024));
            if (n > 0) {
                count(n);
            }
            return n;
        }
    }

    private record Server(ApplicationContext ctx, EmbeddedServer server) implements AutoCloseable {
        static Server start(Map<String, Object> properties) {
            Map<String, Object> config = new HashMap<>(properties);
            config.put("spec.name", SPEC_NAME);
            config.put("micronaut.server.port", -1);
            ApplicationContext ctx = ApplicationContext.run(config);
            return new Server(ctx, ctx.getBean(EmbeddedServer.class).start());
        }

        Recorder recorder() {
            return ctx.getBean(Recorder.class);
        }

        Socket socket() throws IOException {
            Socket socket = new Socket("127.0.0.1", server.getPort());
            socket.setSoTimeout(10_000);
            return socket;
        }

        Response exchange(String request) throws IOException {
            try (Socket socket = socket()) {
                socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
                return Response.read(socket.getInputStream());
            }
        }

        @Override
        public void close() {
            ctx.close();
        }
    }

    private record Response(int status, Map<String, String> headers, byte[] body) {
        String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }

        static Response read(InputStream in) throws IOException {
            String statusLine = line(in);
            int status = Integer.parseInt(statusLine.split(" ")[1]);
            Map<String, String> headers = new HashMap<>();
            for (String line = line(in); !line.isEmpty(); line = line(in)) {
                int colon = line.indexOf(':');
                headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT), line.substring(colon + 1).trim());
            }
            byte[] body;
            if ("chunked".equalsIgnoreCase(headers.get("transfer-encoding"))) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                while (true) {
                    int size = Integer.parseInt(line(in).trim(), 16);
                    if (size == 0) {
                        while (!line(in).isEmpty()) {
                            // trailers
                        }
                        break;
                    }
                    out.write(in.readNBytes(size));
                    line(in);
                }
                body = out.toByteArray();
            } else if (headers.containsKey("content-length")) {
                int length = Integer.parseInt(headers.get("content-length"));
                body = in.readNBytes(length);
                assertEquals(length, body.length, "body ends early");
            } else {
                body = in.readAllBytes();
            }
            return new Response(status, headers, body);
        }

        private static String line(InputStream in) throws IOException {
            StringBuilder sb = new StringBuilder();
            while (true) {
                int c = in.read();
                if (c < 0) {
                    throw new IOException("Connection closed in the middle of a response, after: " + sb);
                }
                if (c == '\n') {
                    int len = sb.length();
                    if (len > 0 && sb.charAt(len - 1) == '\r') {
                        sb.setLength(len - 1);
                    }
                    return sb.toString();
                }
                sb.append((char) c);
            }
        }
    }

    @Controller("/zero-copy")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes {
        @Get("/file")
        File file() {
            return file;
        }

        @Get("/large-file")
        File largeFile() {
            return largeFile;
        }

        @Get("/system-file")
        SystemFile systemFile() {
            return new SystemFile(file);
        }

        @Get("/text-file")
        SystemFile textFile() {
            return new SystemFile(file, io.micronaut.http.MediaType.TEXT_PLAIN_TYPE);
        }

        @Get(value = "/text", produces = "text/plain")
        String text() {
            return "text";
        }
    }

    /**
     * Counts the file regions written to the connections of the server, right behind the HTTP
     * codec, from the log of a {@link LoggingHandler}, which the server knows to pass file
     * regions on. With {@code spec.outbound-handler}, a plain outbound handler of the
     * application follows it, which the server does not know.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Recorder implements BeanCreatedEventListener<NettyServerCustomizer.Registry> {
        final AtomicLong regions = new AtomicLong();
        private final boolean outboundHandler;
        private final ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(RECORDER_LOGGER);
        private final ch.qos.logback.core.AppenderBase<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ch.qos.logback.core.AppenderBase<>() {
            @Override
            protected void append(ch.qos.logback.classic.spi.ILoggingEvent event) {
                String message = event.getFormattedMessage();
                if (message.contains(" WRITE: ") && message.contains("FileRegion")) {
                    regions.incrementAndGet();
                }
            }
        };

        Recorder(@io.micronaut.context.annotation.Value("${spec.outbound-handler:false}") boolean outboundHandler) {
            this.outboundHandler = outboundHandler;
            appender.setContext(logger.getLoggerContext());
            appender.start();
            logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
            logger.setAdditive(false);
            logger.addAppender(appender);
        }

        @jakarta.annotation.PreDestroy
        void stop() {
            logger.detachAppender(appender);
            appender.stop();
        }

        @Override
        public NettyServerCustomizer.Registry onCreated(BeanCreatedEvent<NettyServerCustomizer.Registry> event) {
            event.getBean().register(new NettyServerCustomizer() {
                @Override
                public NettyServerCustomizer specializeForChannel(Channel channel, ChannelRole role) {
                    if (role != ChannelRole.CONNECTION) {
                        return this;
                    }
                    return new NettyServerCustomizer() {
                        @Override
                        public NettyServerCustomizer specializeForChannel(Channel c, ChannelRole r) {
                            return this;
                        }

                        @Override
                        public void onInitialPipelineBuilt() {
                            if (channel.pipeline().get(ChannelPipelineCustomizer.HANDLER_HTTP_SERVER_CODEC) == null) {
                                return;
                            }
                            channel.pipeline().addAfter(ChannelPipelineCustomizer.HANDLER_HTTP_SERVER_CODEC, "zero-copy-recorder",
                                new LoggingHandler(RECORDER_LOGGER, LogLevel.DEBUG, ByteBufFormat.SIMPLE));
                            if (outboundHandler) {
                                channel.pipeline().addAfter("zero-copy-recorder", "application-outbound", new ChannelOutboundHandlerAdapter());
                            }
                        }
                    };
                }
            });
            return event.getBean();
        }
    }
}
