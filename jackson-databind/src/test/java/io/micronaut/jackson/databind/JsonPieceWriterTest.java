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
package io.micronaut.jackson.databind;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.PieceWriter;
import io.micronaut.http.codec.CodecException;
import io.micronaut.json.body.JsonMessageHandler;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The piece writer of the JSON handler writes the elements of a streamed response with one
 * generator, each element with the separator in front of it as one body.
 */
class JsonPieceWriterTest {

    private final ByteBodyFactory bodyFactory = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
    private final ReadBuffer beforeFirst = bodyFactory.readBufferFactory().copyOf("[", StandardCharsets.UTF_8);
    private final ReadBuffer between = bodyFactory.readBufferFactory().copyOf(",", StandardCharsets.UTF_8);
    private final HttpRequest<?> request = HttpRequest.GET("/");
    private final HttpResponse<?> response = HttpResponse.ok();

    private <T> PieceWriter<T> open(Argument<T> type) {
        JsonMessageHandler<T> handler = new JsonMessageHandler<>(new JacksonDatabindMapper());
        return handler.openPieceWriter(bodyFactory, request, response, type, MediaType.APPLICATION_JSON_TYPE);
    }

    private static String text(CloseableByteBody body) {
        try (body) {
            return new String(body.toInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void piecesCarryTheSeparatorInFront() {
        Argument<Map<String, Integer>> type = Argument.mapOf(String.class, Integer.class);
        try (PieceWriter<Map<String, Integer>> writer = open(type)) {
            assertEquals("[{\"i\":1}", text(writer.writePiece(beforeFirst, Map.of("i", 1))));
            assertEquals(",{\"i\":2}", text(writer.writePiece(between, Map.of("i", 2))));
            assertEquals(",{\"i\":3}", text(writer.writePiece(between, Map.of("i", 3))));
        }
    }

    @Test
    void piecesWithoutSeparators() {
        try (PieceWriter<Integer> writer = open(Argument.INT)) {
            assertEquals("1", text(writer.writePiece(null, 1)));
            assertEquals("2", text(writer.writePiece(null, 2)));
        }
    }

    @Test
    void separatorIsNotConsumed() {
        try (ReadBuffer separator = bodyFactory.readBufferFactory().copyOf(",", StandardCharsets.UTF_8);
             PieceWriter<Integer> writer = open(Argument.INT)) {
            assertEquals(",1", text(writer.writePiece(separator, 1)));
            assertEquals(",2", text(writer.writePiece(separator, 2)));
            assertEquals(1, separator.readable());
        }
    }

    @Test
    void undeclaredTypePassesSerializedDocumentsThrough() {
        try (PieceWriter<Object> writer = open(Argument.OBJECT_ARGUMENT)) {
            assertEquals("[{\"a\":1}", text(writer.writePiece(beforeFirst, "{\"a\":1}")));
            assertEquals(",[1,2]", text(writer.writePiece(between, List.of(1, 2))));
        }
    }

    @Test
    void failingPieceIsReportedAsCodecException() {
        try (PieceWriter<JsonMessageHandlerWriteFailureTest.Failing> writer = open(Argument.of(JsonMessageHandlerWriteFailureTest.Failing.class))) {
            CodecException e = assertThrows(CodecException.class, () -> writer.writePiece(beforeFirst, new JsonMessageHandlerWriteFailureTest.Failing()));
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            assertEquals("foo", root.getMessage());
        }
    }
}
