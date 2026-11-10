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
package io.micronaut.dev.livereload.netty;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A page of a mounted directory that cannot be read as UTF-8 is still answered.
 */
class LiveReloadUnreadablePageTest {

    @Test
    void aPageThatIsNotUtf8IsServedAsItIs(@TempDir Path directory) throws Exception {
        // ISO-8859-1 "café": the é is not valid UTF-8 on its own
        byte[] page = new byte[]{'<', 'p', '>', 'c', 'a', 'f', (byte) 0xE9, '<', '/', 'p', '>'};
        Files.write(directory.resolve("latin.html"), page);
        try (NettyLiveReloadServer server = NettyLiveReloadServer.start(0)) {
            server.serve("/site", directory);
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/site/latin.html"))
                .timeout(Duration.ofSeconds(10))
                .build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, response.statusCode());
            assertArrayEquals(page, response.body());
        }
    }

    @Test
    void aPageThatCannotBeReadIsNotFound(@TempDir Path directory) throws Exception {
        Path page = directory.resolve("locked.html");
        Files.writeString(page, "<p>locked</p>");
        assumeTrue(Files.getFileStore(page).supportsFileAttributeView("posix"));
        Files.setPosixFilePermissions(page, PosixFilePermissions.fromString("---------"));
        // a user that reads a file whatever its permissions has no page it cannot read
        assumeFalse(Files.isReadable(page));
        try (NettyLiveReloadServer server = NettyLiveReloadServer.start(0)) {
            server.serve("/site", directory);
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/site/locked.html"))
                .timeout(Duration.ofSeconds(10))
                .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(404, response.statusCode());
        }
    }

    @Test
    void aRequestThatFailsClosesTheConnection() throws Exception {
        try (NettyLiveReloadServer server = NettyLiveReloadServer.start(0);
             Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socket.setSoTimeout(10_000);
            OutputStream output = socket.getOutputStream();
            // an escape that is no hexadecimal number: the path cannot be decoded
            output.write("GET /%zz HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            output.flush();
            InputStream input = socket.getInputStream();
            // the connection is closed with no answer, where it was left open before
            assertEquals(-1, input.read());
        }
    }
}
