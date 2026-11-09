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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.util.zip.DeflaterOutputStream;

class DecompressionConfigTest {
    static final String SPEC_NAME = "DecompressionConfigTest";

    private static final byte[] UNCOMPRESSED = "Hello, gzip!".getBytes(StandardCharsets.UTF_8);
    private static final byte[] GZIPPED = gzip(UNCOMPRESSED);
    private static final byte[] DEFLATED = deflate(UNCOMPRESSED);

    @ParameterizedTest
    @ValueSource(strings = {"gzip", "deflate"})
    void encodedBodyPreservedWhenDecompressionDisabled(String encoding) throws Exception {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider()
                 .getServer(SPEC_NAME, Map.of("micronaut.http.client.decompression-enabled", "false"));
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class);
             ByteBodyHttpResponse<?> response = Mono.from(
                     client.exchange(HttpRequest.GET(server.getURL().get() + "/decompression/" + encoding), null, null))
                 .cast(ByteBodyHttpResponse.class)
                 .block()) {

            // Body should still be compressed
            byte[] body = response.byteBody().buffer().get().toByteArray();
            Assertions.assertArrayEquals(encoding.equals("gzip") ? GZIPPED : DEFLATED, body);

            // Header should be preserved
            Assertions.assertEquals(encoding, response.getHeaders().get(HttpHeaders.CONTENT_ENCODING));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"gzip", "deflate"})
    void encodedBodyIsDecompressedByDefault(String encoding) throws Exception {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class);
             ByteBodyHttpResponse<?> response = Mono.from(
                     client.exchange(HttpRequest.GET(server.getURL().get() + "/decompression/" + encoding), null, null))
                 .cast(ByteBodyHttpResponse.class)
                 .block()) {

            // Body should be decompressed to the original content
            byte[] body = response.byteBody().buffer().get().toByteArray();
            Assertions.assertArrayEquals(UNCOMPRESSED, body);

            // Content-Encoding header should be removed by the decompressor
            Assertions.assertFalse(response.getHeaders().contains(HttpHeaders.CONTENT_ENCODING));
        }
    }

    private static byte[] deflate(byte[] data) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (DeflaterOutputStream deflater = new DeflaterOutputStream(baos)) {
                deflater.write(data);
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
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

    @Controller("/decompression")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class DecompressionController {
        @Get("/deflate")
        HttpResponse<byte[]> deflate() {
            return HttpResponse.ok(DEFLATED)
                .header(HttpHeaders.CONTENT_ENCODING, "deflate")
                .contentLength(DEFLATED.length);
        }

        @Get("/gzip")
        HttpResponse<byte[]> gzip() {
            return HttpResponse.ok(GZIPPED)
                .header(HttpHeaders.CONTENT_ENCODING, "gzip")
                .contentLength(GZIPPED.length);
        }
    }

}
