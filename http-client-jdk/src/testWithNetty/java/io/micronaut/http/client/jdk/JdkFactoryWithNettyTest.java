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
package io.micronaut.http.client.jdk;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.netty.NettyClientHttpRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class JdkFactoryWithNettyTest {
    @Test
    void bothFactoryConstructorsDecodeNettyRequests() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] cookie = exchange.getRequestHeaders().getFirst(HttpHeaders.COOKIE).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, cookie.length);
            try (var output = exchange.getResponseBody()) {
                output.write(cookie);
            }
        });
        server.start();
        URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        try (var client = new DefaultJdkHttpClient(uri, ConversionService.SHARED);
             var configured = new DefaultJdkHttpClient(uri, new DefaultHttpClientConfiguration(), null,
                 JdkHttpClientFactory.createDefaultMessageBodyHandlerRegistry(), ConversionService.SHARED)) {
            for (var factoryClient : new DefaultJdkHttpClient[]{client, configured}) {
                var request = HttpRequest.GET("/").header(HttpHeaders.COOKIE, "session=abc");
                assertInstanceOf(NettyClientHttpRequest.class, request);
                String received = factoryClient.toBlocking().retrieve(request, String.class);
                assertTrue(received.contains("session=abc") || received.contains("session=\"abc\""), received);
            }
        } finally {
            server.stop(0);
        }
    }
}
