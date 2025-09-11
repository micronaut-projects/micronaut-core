/*
 * Copyright 2017-2025 original authors
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

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

class DecompressionConfigTest {
    static final String SPEC_NAME = "DecompressionConfigTest";

    private static final byte[] UNCOMPRESSED = "Hello, gzip!".getBytes(StandardCharsets.UTF_8);
    private static final byte[] GZIPPED = gzip(UNCOMPRESSED);

    @Test
    void gzipPreservedWhenDecompressionDisabled() throws Exception {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider()
                 .getServer(SPEC_NAME, Map.of("micronaut.http.client.decompression-enabled", "false"));
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class);
             ByteBodyHttpResponse<?> response = Mono.from(
                     client.exchange(HttpRequest.GET(server.getURL().get() + "/decompression/gzip"), null, null))
                 .cast(ByteBodyHttpResponse.class)
                 .block()) {

            // Body should still be compressed
            byte[] body = response.byteBody().buffer().get().toByteArray();
            Assertions.assertArrayEquals(GZIPPED, body);

            // Header should be preserved
            Assertions.assertEquals("gzip", response.getHeaders().get(HttpHeaders.CONTENT_ENCODING));
        }
    }

    @Test
    void gzipIsDecompressedByDefault() throws Exception {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class);
             ByteBodyHttpResponse<?> response = Mono.from(
                     client.exchange(HttpRequest.GET(server.getURL().get() + "/decompression/gzip"), null, null))
                 .cast(ByteBodyHttpResponse.class)
                 .block()) {

            // Body should be decompressed to the original content
            byte[] body = response.byteBody().buffer().get().toByteArray();
            Assertions.assertArrayEquals(UNCOMPRESSED, body);

            // Content-Encoding header should be removed by the decompressor
            Assertions.assertFalse(response.getHeaders().contains(HttpHeaders.CONTENT_ENCODING));
        }
    }

    @Controller("/decompression")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class DecompressionController {
        @Get("/gzip")
        HttpResponse<byte[]> gzip() {
            return HttpResponse.ok(GZIPPED)
                .header(HttpHeaders.CONTENT_ENCODING, "gzip")
                .contentLength(GZIPPED.length);
        }
    }

    private static byte[] gzip(byte[] data) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
                gzip.write(data);
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
