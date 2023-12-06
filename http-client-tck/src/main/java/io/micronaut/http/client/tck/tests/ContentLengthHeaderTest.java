/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.client.tck.tests;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;

import static io.micronaut.http.HttpHeaders.CONTENT_LENGTH;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ContentLengthHeaderTest {

    private static final String PATH = "/content-length";

    private HttpServer server;

    private ApplicationContext applicationContext;
    private HttpClient httpClient;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(SocketUtils.findAvailableTcpPort()), 0);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.createContext(PATH, new MyHandler());
        server.start();

        applicationContext = ApplicationContext.run();
        httpClient = applicationContext.createBean(HttpClient.class, new URL("http://localhost:" + server.getAddress().getPort()));
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        applicationContext.stop();
    }

    @ParameterizedTest(name = "blocking={0}")
    @ValueSource(booleans = {true, false})
    void postContainsHeader(boolean blocking) {
        MutableHttpRequest<String> post = HttpRequest.POST(PATH, "tim");
        String retrieve = blocking
            ? httpClient.toBlocking().retrieve(post)
            : Flux.from(httpClient.retrieve(post)).blockFirst();
        assertEquals("POST:3", retrieve);
    }

    @ParameterizedTest(name = "blocking={0}")
    @ValueSource(booleans = {true, false})
    @ClientDisabledCondition.ClientDisabled(httpClient = ClientDisabledCondition.JDK, jdk = "17") // The bug is fixed in the JDK HttpClient 21, so we disable the test for prior versions
    void getContainsHeader(boolean blocking) {
        MutableHttpRequest<String> get = HttpRequest.GET(PATH);
        String retrieve = blocking
            ? httpClient.toBlocking().retrieve(get)
            : Flux.from(httpClient.retrieve(get)).blockFirst();
        assertEquals("GET:", retrieve);
    }

    static class MyHandler implements HttpHandler {

        private String header(Headers requestHeaders, String name) {
            if (requestHeaders.containsKey(name)) {
                return String.join(", ", requestHeaders.get(name));
            } else {
                return "";
            }
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String contentLength = header(exchange.getRequestHeaders(), CONTENT_LENGTH);
            exchange.sendResponseHeaders(200, 0);
            try (var os = exchange.getResponseBody()) {
                os.write("%s:%s".formatted(method, contentLength).getBytes());
            }
            exchange.close();
        }
    }
}
