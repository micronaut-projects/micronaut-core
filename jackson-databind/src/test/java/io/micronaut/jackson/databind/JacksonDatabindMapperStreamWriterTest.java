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
import io.micronaut.json.JsonMapper;
import io.micronaut.json.JsonStreamWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stream writer keeps one generator open for a sequence of values. It must produce exactly
 * the bytes that one {@link JsonMapper#writeValue} call per value would, with nothing in between,
 * and each value must be on the stream once its write returns.
 */
class JacksonDatabindMapperStreamWriterTest {

    private static final Argument<Item> TYPE = Argument.of(Item.class);
    private static final List<Item> ITEMS = List.of(
        new Item(1, "one", List.of(1, 2)),
        new Item(2, "two \"quoted\" \u00fc", List.of()),
        new Item(3, null, List.of(3))
    );

    @Test
    void valuesAreWrittenWithoutSeparatorsAndAreByteIdenticalToWriteValue() throws IOException {
        JacksonDatabindMapper mapper = new JacksonDatabindMapper();
        assertSequence(mapper, mapper);
    }

    @Test
    void specializedMapper() throws IOException {
        JacksonDatabindMapper mapper = new JacksonDatabindMapper();
        assertSequence(mapper.createSpecific(TYPE), mapper);
    }

    private static void assertSequence(JsonMapper mapper, JsonMapper reference) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        try (JsonStreamWriter<Item> writer = mapper.createStreamWriter(out, TYPE)) {
            for (Item item : ITEMS) {
                writer.write(item);
                expected.write(reference.writeValueAsBytes(TYPE, item));
                // the value is on the stream when write returns
                assertArrayEquals(expected.toByteArray(), out.toByteArray());
            }
        }
        assertArrayEquals(expected.toByteArray(), out.toByteArray());
        String json = out.toString(StandardCharsets.UTF_8);
        assertTrue(json.startsWith("{\"id\":1,"), json);
        assertEquals(3, json.split("\\{\"id\":").length - 1, json);
        assertEquals("}{", json.substring(json.indexOf("}{"), json.indexOf("}{") + 2), json);
    }

    @Test
    void nullValue() throws IOException {
        JacksonDatabindMapper mapper = new JacksonDatabindMapper();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JsonStreamWriter<Item> writer = mapper.createStreamWriter(out, TYPE)) {
            writer.write(null);
            writer.write(ITEMS.get(0));
        }
        assertEquals("null" + mapper.writeValueAsString(TYPE, ITEMS.get(0)), out.toString(StandardCharsets.UTF_8));
    }

    @Test
    void genericType() throws IOException {
        JacksonDatabindMapper mapper = new JacksonDatabindMapper();
        Argument<Map<String, Integer>> type = Argument.mapOf(String.class, Integer.class);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JsonStreamWriter<Map<String, Integer>> writer = mapper.createStreamWriter(out, type)) {
            writer.write(Map.of("a", 1));
            writer.write(Map.of("b", 2));
        }
        assertEquals("{\"a\":1}{\"b\":2}", out.toString(StandardCharsets.UTF_8));
    }

    @Test
    void closeClosesTheStream() throws IOException {
        JacksonDatabindMapper mapper = new JacksonDatabindMapper();
        boolean[] closed = new boolean[1];
        ByteArrayOutputStream out = new ByteArrayOutputStream() {
            @Override
            public void close() {
                closed[0] = true;
            }
        };
        JsonStreamWriter<Item> writer = mapper.createStreamWriter(out, TYPE);
        writer.write(ITEMS.get(0));
        writer.close();
        assertTrue(closed[0]);
    }

    @Test
    void failingValue() throws IOException {
        JacksonDatabindMapper mapper = new JacksonDatabindMapper();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JsonStreamWriter<Failing> writer = mapper.createStreamWriter(out, Argument.of(Failing.class))) {
            Exception e = assertThrows(Exception.class, () -> writer.write(new Failing()));
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            assertEquals("foo", root.getMessage());
        }
    }

    public record Item(int id, String name, List<Integer> values) {
    }

    public static final class Failing {
        public String getName() {
            throw new IllegalStateException("foo");
        }
    }
}
