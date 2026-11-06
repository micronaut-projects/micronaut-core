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
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.functional.ThrowingConsumer;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.json.body.JsonMessageHandler;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The 8 KiB starting capacity is a hint for a whole response body. The elements of a streamed
 * response are written to the channel uncopied, so a hint that size would hold 8 KiB of capacity
 * for every element the client has not read yet; they must use the factory's default sizing.
 */
class JsonMessageHandlerBufferSizeTest {

    @Test
    void wholeBodyUsesTheSizeHintAndPiecesDoNot() throws Exception {
        // the size hint of each buffer() call made by the handler, -1 for the variant without one
        List<Integer> hints = new ArrayList<>();
        ReadBufferFactory recording = new ReadBufferFactory() {
            private boolean nested;

            @Override
            public <T extends Throwable> ReadBuffer buffer(ThrowingConsumer<? super OutputStream, T> writer) throws T {
                hints.add(-1);
                // the default sizing is implemented through the sized variant
                nested = true;
                try {
                    return super.buffer(writer);
                } finally {
                    nested = false;
                }
            }

            @Override
            public <T extends Throwable> ReadBuffer buffer(int expectedSize, ThrowingConsumer<? super OutputStream, T> writer) throws T {
                if (!nested) {
                    hints.add(expectedSize);
                }
                return super.buffer(expectedSize, writer);
            }
        };
        ByteBodyFactory bodyFactory = new ByteBodyFactory(ByteArrayBufferFactory.INSTANCE, recording) { };
        JsonMessageHandler<Map<String, Integer>> handler = new JsonMessageHandler<>(new JacksonDatabindMapper());
        Argument<Map<String, Integer>> type = Argument.mapOf(String.class, Integer.class);
        HttpRequest<?> request = HttpRequest.GET("/");
        MutableHttpResponse<Map<String, Integer>> response = HttpResponse.ok();

        try (ByteBodyHttpResponse<?> whole = handler.write(bodyFactory, request, response, type, MediaType.APPLICATION_JSON_TYPE, Map.of("i", 1))) {
            assertEquals("{\"i\":1}", new String(whole.byteBody().buffer().get().toByteArray(), StandardCharsets.UTF_8));
        }
        try (CloseableByteBody piece = handler.writePiece(bodyFactory, request, response, type, MediaType.APPLICATION_JSON_TYPE, Map.of("i", 2))) {
            assertEquals("{\"i\":2}", new String(piece.buffer().get().toByteArray(), StandardCharsets.UTF_8));
        }

        assertEquals(List.of(8192, -1), hints);
    }
}
