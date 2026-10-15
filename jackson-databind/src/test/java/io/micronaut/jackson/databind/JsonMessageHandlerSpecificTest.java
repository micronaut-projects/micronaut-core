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

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.json.body.JsonMessageHandler;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A handler specialized for a type, e.g. the reader or writer of a route, reads and writes that
 * type with its specialized mapper and any other type with the general one.
 */
class JsonMessageHandlerSpecificTest {

    private static final Argument<Object> LIST = (Argument) Argument.listOf(String.class);
    private static final Argument<Object> MAP = (Argument) Argument.mapOf(String.class, String.class);

    private final JsonMessageHandler<Object> handler = new JsonMessageHandler<>(new JacksonDatabindMapper());

    @Test
    void readsTheSpecificType() {
        MessageBodyReader<Object> reader = handler.createSpecificReader(LIST);

        assertEquals(List.of("a"), read(reader, LIST, "[\"a\"]"));
    }

    @Test
    void readsAnotherTypeWithTheGeneralMapper() {
        MessageBodyReader<Object> reader = handler.createSpecificReader(LIST);

        assertEquals(Map.of("a", "b"), read(reader, MAP, "{\"a\":\"b\"}"));
    }

    @Test
    void writesAnotherTypeWithTheGeneralMapper() {
        JsonMessageHandler<Object> writer = handler.createSpecific(LIST);

        assertEquals("[\"a\"]", write(writer, LIST, List.of("a")));
        assertEquals("{\"a\":\"b\"}", write(writer, MAP, Map.of("a", "b")));
    }

    @Test
    void specializingAgainUsesTheGeneralMapper() {
        JsonMessageHandler<Object> again = handler.createSpecific(LIST).createSpecific(MAP);

        assertEquals(Map.of("a", "b"), read(again, MAP, "{\"a\":\"b\"}"));
        assertEquals(List.of("a"), read(again, LIST, "[\"a\"]"));
    }

    private static Object read(MessageBodyReader<Object> reader, Argument<Object> type, String json) {
        return reader.read(type, MediaType.APPLICATION_JSON_TYPE, HttpResponse.ok().getHeaders(), new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    private static String write(JsonMessageHandler<Object> writer, Argument<Object> type, Object value) {
        MutableHttpHeaders headers = HttpResponse.ok().getHeaders();
        var out = new ByteArrayOutputStream();
        writer.writeTo(type, MediaType.APPLICATION_JSON_TYPE, value, headers, out);
        return out.toString(StandardCharsets.UTF_8);
    }
}
