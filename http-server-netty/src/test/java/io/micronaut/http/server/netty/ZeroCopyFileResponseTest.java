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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.FileRegion;
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
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A file response over a plain HTTP/1.x connection is sent from the file directly to the
 * connection, as a {@link FileRegion}. A compressed response, a TLS connection, or a server that
 * disables it reads the file as a stream.
 */
class ZeroCopyFileResponseTest {
    private static final String SPEC_NAME = "ZeroCopyFileResponseTest";
    private static final int FILE_SIZE = 1024 * 1024 + 17;

    private static File file;
    private static byte[] contents;

    @BeforeAll
    static void createFile() throws IOException {
        contents = new byte[FILE_SIZE];
        new Random(42).nextBytes(contents);
        file = File.createTempFile("zero-copy", ".bin");
        file.deleteOnExit();
        Files.write(file.toPath(), contents);
    }

    @AfterAll
    static void deleteFile() {
        if (file != null) {
            file.delete();
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
            assertEquals(FILE_SIZE, server.recorder().regionBytes.get());
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
            assertEquals(200000, server.recorder().regionBytes.get());
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
     * codec.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Recorder implements BeanCreatedEventListener<NettyServerCustomizer.Registry> {
        final AtomicLong regions = new AtomicLong();
        final AtomicLong regionBytes = new AtomicLong();

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
                            channel.pipeline().addAfter(ChannelPipelineCustomizer.HANDLER_HTTP_SERVER_CODEC, "zero-copy-recorder", new ChannelOutboundHandlerAdapter() {
                                @Override
                                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                                    if (msg instanceof FileRegion region) {
                                        regions.incrementAndGet();
                                        regionBytes.addAndGet(region.count());
                                    }
                                    super.write(ctx, msg, promise);
                                }
                            });
                        }
                    };
                }
            });
            return event.getBean();
        }
    }
}
