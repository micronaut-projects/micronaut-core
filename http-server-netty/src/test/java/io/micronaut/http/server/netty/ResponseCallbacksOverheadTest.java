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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a request costs a keep-alive connection when it registers no callback: the listeners of
 * the close future of the connection stay the same, and the time per request is printed for a
 * comparison between branches.
 */
class ResponseCallbacksOverheadTest {
    static final String SPEC_NAME = "ResponseCallbacksOverheadTest";
    private static final int REQUESTS = 5_000;

    @Test
    void keepAliveRequestsWithoutCallbacksAddNoListeners() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            ChannelHolder holder = ctx.getBean(ChannelHolder.class);
            try (Socket socket = new Socket(server.getHost(), server.getPort())) {
                socket.setTcpNoDelay(true);
                exchange(socket, "/overhead/plain");
                int listeners = closeListeners(holder.channel.closeFuture());
                // warm up, then measure
                for (int i = 0; i < REQUESTS; i++) {
                    exchange(socket, "/overhead/plain");
                }
                long start = System.nanoTime();
                for (int i = 0; i < REQUESTS; i++) {
                    exchange(socket, "/overhead/plain");
                }
                long perRequest = (System.nanoTime() - start) / REQUESTS;
                System.out.println("OVERHEAD plain keep-alive request: " + perRequest + " ns");
                assertEquals(listeners, closeListeners(holder.channel.closeFuture()), "no listener accumulates on the connection");
            }
        }
    }

    /**
     * Send a GET on a keep-alive connection and read its whole {@code content-length} response.
     */
    static void exchange(Socket socket, String path) throws IOException {
        OutputStream out = socket.getOutputStream();
        out.write(("GET " + path + " HTTP/1.1\r\nHost: localhost\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
        InputStream in = socket.getInputStream();
        StringBuilder head = new StringBuilder();
        while (!head.toString().endsWith("\r\n\r\n")) {
            int b = in.read();
            assertTrue(b >= 0, "connection closed");
            head.append((char) b);
        }
        String lower = head.toString().toLowerCase();
        int at = lower.indexOf("content-length:");
        int length = Integer.parseInt(lower.substring(at + 15, lower.indexOf("\r\n", at)).trim());
        assertEquals(length, in.readNBytes(length).length);
    }

    /**
     * @param future A future
     * @return The number of listeners of the future
     */
    static int closeListeners(Future<?> future) throws ReflectiveOperationException {
        Class<?> type = future.getClass();
        while (!type.getName().equals("io.netty.util.concurrent.DefaultPromise")) {
            type = type.getSuperclass();
        }
        Field listener = type.getDeclaredField("listener");
        Field listeners = type.getDeclaredField("listeners");
        listener.setAccessible(true);
        listeners.setAccessible(true);
        synchronized (future) {
            Object many = listeners.get(future);
            if (many != null) {
                Method size = many.getClass().getDeclaredMethod("size");
                size.setAccessible(true);
                return (int) size.invoke(many);
            }
            return listener.get(future) == null ? 0 : 1;
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ChannelHolder {
        volatile Channel channel;

        void capture(HttpRequest<?> request) {
            channel = ((NettyHttpRequest<?>) request).getChannelHandlerContext().channel();
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class OverheadRoutes implements HttpRoutes {
        private final ChannelHolder holder;

        OverheadRoutes(ChannelHolder holder) {
            this.holder = holder;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/overhead/plain").handle((request, pathVariables) -> {
                holder.capture(request);
                return HttpResponse.ok("ok").contentType(MediaType.TEXT_PLAIN_TYPE);
            });
        }
    }
}
