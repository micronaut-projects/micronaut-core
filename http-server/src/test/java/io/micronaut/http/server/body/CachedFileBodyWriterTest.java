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
package io.micronaut.http.server.body;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.body.AvailableByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.types.files.CachedFile;
import io.micronaut.http.server.util.HttpDateHeader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CachedFileBodyWriterTest {

    private static final ByteBodyFactory BODIES = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
    private static final byte[] CONTENT = "cached".getBytes(StandardCharsets.UTF_8);
    private static final long LAST_MODIFIED = 1_700_000_000_000L;

    private final CachedFileBodyWriter writer = new CachedFileBodyWriter(new HttpServerConfiguration.FileTypeHandlerConfiguration());

    @Test
    void theContentIsWrittenWithTheHeadersOfAFile() throws Exception {
        CachedFile file = new CachedFile(CONTENT, MediaType.TEXT_PLAIN_TYPE, LAST_MODIFIED);
        MutableHttpResponse<CachedFile> response = HttpResponse.ok(file);
        try (ByteBodyHttpResponse<?> written = writer.write(BODIES, HttpRequest.GET("/a.txt"), response, Argument.of(CachedFile.class), MediaType.TEXT_PLAIN_TYPE, file)) {
            assertEquals(HttpStatus.OK, written.getStatus());
            assertArrayEquals(CONTENT, assertInstanceOf(AvailableByteBody.class, written.byteBody()).toByteArray());
            assertEquals(MediaType.TEXT_PLAIN, written.getHeaders().get(HttpHeaders.CONTENT_TYPE));
            assertNotNull(written.getHeaders().get(HttpHeaders.LAST_MODIFIED));
            assertNotNull(written.getHeaders().get(HttpHeaders.CACHE_CONTROL));
        }
    }

    @Test
    void anUnmodifiedFileIsNotModified() throws Exception {
        CachedFile file = new CachedFile(CONTENT, MediaType.TEXT_PLAIN_TYPE, LAST_MODIFIED);
        try (ByteBodyHttpResponse<?> written = writer.write(BODIES,
            HttpRequest.GET("/a.txt").header(HttpHeaders.IF_MODIFIED_SINCE, HttpDateHeader.get(LAST_MODIFIED)),
            HttpResponse.ok(file), Argument.of(CachedFile.class), MediaType.TEXT_PLAIN_TYPE, file)) {
            assertEquals(HttpStatus.NOT_MODIFIED, written.getStatus());
        }
    }

    @Test
    void aPieceAndAStreamHaveTheContent() throws Exception {
        CachedFile file = new CachedFile(CONTENT, MediaType.TEXT_PLAIN_TYPE, LAST_MODIFIED);
        try (CloseableByteBody piece = writer.writePiece(BODIES, HttpRequest.GET("/a.txt"), HttpResponse.ok(), Argument.of(CachedFile.class), MediaType.TEXT_PLAIN_TYPE, file)) {
            assertArrayEquals(CONTENT, assertInstanceOf(AvailableByteBody.class, piece).toByteArray());
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.writeTo(Argument.of(CachedFile.class), MediaType.TEXT_PLAIN_TYPE, file, null, out);
        assertArrayEquals(CONTENT, out.toByteArray());
    }
}
