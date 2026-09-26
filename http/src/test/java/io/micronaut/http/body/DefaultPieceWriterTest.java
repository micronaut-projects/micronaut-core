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
package io.micronaut.http.body;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A writer that does not open a {@link PieceWriter} of its own gets one that writes every piece
 * with {@link ResponseBodyWriter#writePiece} and puts the separator in front of it, whether the
 * piece is available or streamed.
 */
class DefaultPieceWriterTest {

    private final ByteBodyFactory bodyFactory = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
    private final ConcatenatingSubscriber.Separators separators = ConcatenatingSubscriber.Separators.JDK_JSON;

    private static String text(CloseableByteBody body) throws IOException {
        try (body) {
            return new String(body.toInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void availablePieces() throws IOException {
        MessageBodyWriter<String> plain = (type, mediaType, object, outgoingHeaders, outputStream) -> {
            try {
                outputStream.write(object.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
        try (PieceWriter<String> writer = ResponseBodyWriter.wrap(plain).openPieceWriter(bodyFactory, HttpRequest.GET("/"), HttpResponse.ok(), Argument.STRING, MediaType.APPLICATION_JSON_TYPE)) {
            assertEquals("[1", text(writer.writePiece(separators.beforeFirst(), "1")));
            assertEquals(",2", text(writer.writePiece(separators.between(), "2")));
            assertEquals("3", text(writer.writePiece(null, "3")));
        }
    }

    @Test
    void streamedPieces() throws IOException {
        ResponseBodyWriter<String> streaming = new ResponseBodyWriter<>() {
            @Override
            public CloseableByteBody writePiece(ByteBodyFactory bodyFactory, HttpRequest<?> request, HttpResponse<?> response, Argument<String> type, MediaType mediaType, String object) throws CodecException {
                return bodyFactory.adapt(Flux.just(bodyFactory.readBufferFactory().copyOf(object, StandardCharsets.UTF_8)));
            }

            @Override
            public void writeTo(Argument<String> type, MediaType mediaType, String object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
                throw new UnsupportedOperationException();
            }
        };
        try (PieceWriter<String> writer = streaming.openPieceWriter(bodyFactory, HttpRequest.GET("/"), HttpResponse.ok(), Argument.STRING, MediaType.APPLICATION_JSON_TYPE)) {
            assertEquals("[1", text(writer.writePiece(separators.beforeFirst(), "1")));
            assertEquals(",2", text(writer.writePiece(separators.between(), "2")));
            assertEquals("3", text(writer.writePiece(null, "3")));
        }
    }

    @Test
    void separatorIsNotConsumed() throws IOException {
        MessageBodyWriter<String> plain = (type, mediaType, object, outgoingHeaders, outputStream) -> {
            try {
                outputStream.write(object.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
        try (ReadBuffer separator = bodyFactory.readBufferFactory().copyOf(",", StandardCharsets.UTF_8);
             PieceWriter<String> writer = ResponseBodyWriter.wrap(plain).openPieceWriter(bodyFactory, HttpRequest.GET("/"), HttpResponse.ok(), Argument.STRING, MediaType.APPLICATION_JSON_TYPE)) {
            assertEquals(",1", text(writer.writePiece(separator, "1")));
            assertEquals(",2", text(writer.writePiece(separator, "2")));
            assertEquals(1, separator.readable());
        }
    }
}
