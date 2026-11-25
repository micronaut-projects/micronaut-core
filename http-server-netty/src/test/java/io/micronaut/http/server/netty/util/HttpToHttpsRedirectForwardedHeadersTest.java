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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    private static final String FORWARDED = "Forwarded: host=other.example.com\r\n";
    private static final String X_FORWARDED = "X-Forwarded-Host: other.example.com\r\n";

    // each row sends only the header families whose flag is false, so a flag the redirect ignores fails its row
    @ParameterizedTest(name = "allow-forwarded={0}, allow-xforwarded={1}")
    @CsvSource({
        "false, true",
        "true,  false",
        "false, false",
    })
    void theRedirectUsesTheHostHeaderWhenTheForwardedHeadersAreIgnored(boolean allowForwarded, boolean allowXforwarded) throws IOException {
        String headers = (allowForwarded ? "" : FORWARDED) + (allowXforwarded ? "" : X_FORWARDED);
        try (ApplicationContext ctx = start(Map.of(
            "micronaut.server.host-resolution.allow-forwarded", allowForwarded,
            "micronaut.server.host-resolution.allow-xforwarded", allowXforwarded))) {
            URI location = URI.create(redirectLocation(ctx, headers));
            assertEquals("https", location.getScheme());
            assertEquals("origin.example.com", location.getHost());
            assertEquals("/hello", location.getPath());
        }
    }

    // an empty flag is left unset, so the first rows use the defaults; an empty header is not sent
    @ParameterizedTest(name = "allow-forwarded={0}, allow-xforwarded={1}, Forwarded host={2}, X-Forwarded-Host={3}")
    @CsvSource({
        ",      ,      fwd.example.com, ,                 fwd.example.com",
        ",      ,      ,                xfwd.example.com, xfwd.example.com",
        "false, true,  fwd.example.com, xfwd.example.com, xfwd.example.com",
        "true,  false, fwd.example.com, xfwd.example.com, fwd.example.com",
    })
    void theRedirectUsesTheForwardedHeadersThatAreAllowed(@Nullable Boolean allowForwarded, @Nullable Boolean allowXforwarded,
                                                          @Nullable String forwardedHost, @Nullable String xForwardedHost,
                                                          String expectedHost) throws IOException {
        Map<String, Object> properties = new HashMap<>();
        if (allowForwarded != null) {
            properties.put("micronaut.server.host-resolution.allow-forwarded", allowForwarded);
        }
        if (allowXforwarded != null) {
            properties.put("micronaut.server.host-resolution.allow-xforwarded", allowXforwarded);
        }
        String headers = (forwardedHost == null ? "" : "Forwarded: host=" + forwardedHost + "\r\n")
            + (xForwardedHost == null ? "" : "X-Forwarded-Host: " + xForwardedHost + "\r\n");
        try (ApplicationContext ctx = start(properties)) {
            URI location = URI.create(redirectLocation(ctx, headers));
            assertEquals("https", location.getScheme());
            assertEquals(expectedHost, location.getHost());
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
