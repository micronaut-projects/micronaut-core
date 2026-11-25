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
package io.micronaut.http.server.netty.util;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.server.netty.NettyEmbeddedServer;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The HTTP to HTTPS redirect uses a host resolver that the Netty server builds itself; it follows
 * {@code micronaut.server.host-resolution.allow-forwarded} and {@code allow-xforwarded} too.
 */
class HttpToHttpsRedirectForwardedHeadersTest {

    private static final String FORWARDED_HEADERS = "X-Forwarded-Host: other.example.com\r\nForwarded: host=other.example.com\r\n";

    @Test
    void theRedirectUsesTheHostHeaderWhenTheForwardedHeadersAreIgnored() throws IOException {
        try (ApplicationContext ctx = start(Map.of(
            "micronaut.server.host-resolution.allow-forwarded", false,
            "micronaut.server.host-resolution.allow-xforwarded", false))) {
            URI location = URI.create(redirectLocation(ctx, FORWARDED_HEADERS));
            assertEquals("https", location.getScheme());
            assertEquals("origin.example.com", location.getHost());
            assertEquals("/hello", location.getPath());
        }
    }

    private static ApplicationContext start(Map<String, Object> properties) {
        Map<String, Object> config = new HashMap<>(properties);
        config.put("micronaut.server.port", -1);
        config.put("micronaut.server.dual-protocol", true);
        config.put("micronaut.server.http-to-https-redirect", true);
        config.put("micronaut.ssl.enabled", true);
        config.put("micronaut.server.ssl.port", -1);
        config.put("micronaut.server.ssl.build-self-signed", true);
        ApplicationContext ctx = ApplicationContext.run(config);
        ctx.getBean(EmbeddedServer.class).start();
        return ctx;
    }

    private static String redirectLocation(ApplicationContext ctx, String headers) throws IOException {
        NettyEmbeddedServer server = ctx.getBean(NettyEmbeddedServer.class);
        int httpPort = server.getBoundPorts().stream().filter(port -> port != server.getPort()).findFirst().orElseThrow();
        try (Socket socket = new Socket("127.0.0.1", httpPort)) {
            socket.setSoTimeout(10_000);
            String request = "GET /hello HTTP/1.1\r\nHost: origin.example.com\r\n" + headers + "Connection: close\r\n\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
            assertTrue(response.startsWith("HTTP/1.1 308 "), response);
            return response.lines()
                .filter(line -> line.toLowerCase(Locale.ROOT).startsWith("location:"))
                .findFirst()
                .orElseThrow()
                .substring("location:".length())
                .trim();
        }
    }
}
