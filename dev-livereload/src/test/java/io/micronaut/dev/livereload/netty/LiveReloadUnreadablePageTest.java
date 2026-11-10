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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
