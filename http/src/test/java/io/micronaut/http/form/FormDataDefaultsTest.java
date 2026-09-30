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
package io.micronaut.http.form;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The default methods of {@link FormData}, for an implementation that only has the text fields:
 * the typed accessors, the files, and the failures.
 */
class FormDataDefaultsTest {

    private static final FormData FORM = new TextForm(Map.of(
        "name", List.of("Fred"),
        "age", List.of("42", "43")));

    @Test
    void typedFields() {
        assertEquals(List.of(42, 43), FORM.get("age", Argument.listOf(Integer.class)));
        assertEquals(List.of(42, 43), FORM.get("age", Argument.of(List.class, "other", Argument.of(Integer.class))));
        assertArrayEquals(new Integer[] {42, 43}, FORM.get("age", Argument.of(Integer[].class)));
        assertEquals(42, FORM.get("age", Argument.of(Integer.class)));
        assertEquals(Optional.empty(), FORM.find("city", Argument.listOf(Integer.class)));
        assertEquals(1.5, FORM.getDouble("city", 1.5));
        assertEquals(42, FORM.getInt("age"));
    }

    @Test
    void anOptionalOfACollectionGetsEveryValue() {
        assertEquals(Optional.of(List.of(42, 43)), FORM.get("age", Argument.of(Optional.class, Argument.listOf(Integer.class))));
        assertArrayEquals(new Integer[] {42, 43}, (Integer[]) FORM.get("age", Argument.of(Optional.class, Argument.of(Integer[].class))).orElseThrow());
        assertEquals(Optional.of(42), FORM.get("age", Argument.of(Optional.class, Argument.of(Integer.class))));
    }

    @Test
    void failures() {
        FormFieldException missing = assertThrows(FormFieldException.class, () -> FORM.get("city", Argument.listOf(Integer.class)));
        assertEquals("city", missing.getFieldName());
        assertTrue(missing.getMessage().contains("[city]"), missing.getMessage());
        FormFieldException unconvertible = assertThrows(FormFieldException.class,
            () -> FORM.get("name", Argument.of(Map.class, "name", Argument.of(FormDataDefaultsTest.class), Argument.of(FormDataDefaultsTest.class))));
        assertTrue(unconvertible.getMessage().contains("cannot be converted to"), unconvertible.getMessage());
    }

    @Test
    void files() {
        assertEquals(Optional.empty(), FORM.findFile("avatar"));
        FormFieldException missing = assertThrows(FormFieldException.class, () -> FORM.getFile("avatar"));
        assertEquals("avatar", missing.getFieldName());
        assertTrue(missing.getMessage().contains("not uploaded"), missing.getMessage());
        assertTrue(FormFieldException.notAFile("name").getMessage().contains("expected to be a file upload"));
    }

    /**
     * A form of text fields, converted with the shared conversion service.
     *
     * @param fields The fields
     */
    private record TextForm(Map<String, List<String>> fields) implements FormData {

        @Override
        public Set<String> names() {
            return fields.keySet();
        }

        @Override
        public boolean contains(String name) {
            return fields.containsKey(name);
        }

        @Override
        public List<String> getValues(String name) {
            return fields.getOrDefault(name, List.of());
        }

        @Override
        public <T> T get(String name, Class<T> type) {
            return find(name, type).orElseThrow(() -> FormFieldException.missingField(name));
        }

        @Override
        public <T> Optional<T> find(String name, Class<T> type) {
            List<String> values = getValues(name);
            return values.isEmpty() ? Optional.empty() : ConversionService.SHARED.convert(values.get(0), type);
        }

        @Override
        public List<FileUpload> getFiles(String name) {
            return List.of();
        }

        @Override
        public CompletionStage<Void> closeAsync() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void close() {
            // no files to release
        }
    }
}
