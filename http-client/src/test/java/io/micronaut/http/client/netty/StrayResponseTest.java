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
package io.micronaut.http.client.netty;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * A server that sends more responses than it was asked for must not answer the next request with
 * them: the response that arrived in the same read as the response of the previous request is not
 * the response of the request that waited for the connection.
 */
class StrayResponseTest {

    @Test
    void aStrayResponseIsNotDeliveredToTheWaitingRequest() throws Exception {
        DefaultHttpClientConfiguration configuration = new DefaultHttpClientConfiguration();
        configuration.getConnectionPoolConfiguration().setMaxConcurrentHttp1Connections(1);
        List<Socket> sockets = new CopyOnWriteArrayList<>();
        try (ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
             DefaultHttpClient client = DefaultHttpClient.builder()
                 .uri(URI.create("http://127.0.0.1:" + server.getLocalPort()))
                 .configuration(configuration)
                 .build()) {
            CompletableFuture<HttpResponse<String>> first = Mono.from(client.exchange(HttpRequest.GET("/first"), String.class)).toFuture();
            Socket socket = server.accept();
            sockets.add(socket);
            Assertions.assertEquals("GET /first", readHead(socket.getInputStream()));

            CompletableFuture<HttpResponse<String>> second = Mono.from(client.exchange(HttpRequest.GET("/second"), String.class)).toFuture();
            // let the second request queue for the only connection
            Thread.sleep(500);
            Assertions.assertFalse(second.isDone());

            // the answer to the first request and a response nobody asked for, in one write
            write(socket, response("first") + response("stray"));
            Assertions.assertEquals("first", first.get(10, TimeUnit.SECONDS).body());

            // answer the second request on whichever connection it arrives
            Thread answer = new Thread(() -> {
                try {
                    Socket s = socket;
                    while (true) {
                        String head;
                        try {
                            head = readHead(s.getInputStream());
                        } catch (IOException e) {
                            s = server.accept();
                            sockets.add(s);
                            continue;
                        }
                        if (head.equals("GET /second")) {
                            write(s, response("second"));
                            return;
                        }
                    }
                } catch (IOException ignored) {
                }
            });
            answer.setDaemon(true);
            answer.start();

            try {
                Assertions.assertEquals("second", second.get(10, TimeUnit.SECONDS).body());
            } catch (ExecutionException e) {
                // failing the second request cleanly is acceptable, answering it with the stray response is not
            }
        } finally {
            for (Socket s : sockets) {
                s.close();
            }
        }
    }

    private static String response(String body) {
        return "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: " + body.length() + "\r\n\r\n" + body;
    }

    private static void write(Socket socket, String data) throws IOException {
        OutputStream out = socket.getOutputStream();
        out.write(data.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }

    private static String readHead(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        while (!sb.toString().endsWith("\r\n\r\n")) {
            int b = in.read();
            if (b < 0) {
                throw new SocketException("closed");
            }
            sb.append((char) b);
        }
        String line = sb.substring(0, sb.indexOf("\r\n"));
        return line.substring(0, line.lastIndexOf(' '));
    }
}
