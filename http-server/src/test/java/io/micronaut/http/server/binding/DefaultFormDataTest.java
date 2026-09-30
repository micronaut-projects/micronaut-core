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
package io.micronaut.http.server.binding;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.form.FileUpload;
import io.micronaut.http.form.FormFieldException;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The accessors of the text fields of a {@link DefaultFormData}, the conversion failures, and the
 * release of its files.
 */
class DefaultFormDataTest {

    private static DefaultFormData form(Map<String, List<FileUpload>> files) {
        Map<String, List<String>> fields = new LinkedHashMap<>();
        fields.put("name", List.of("Fred"));
        fields.put("age", List.of("42", "43"));
        fields.put("ratio", List.of("1.5"));
        fields.put("flag", List.of("true"));
        fields.put("letter", List.of("x"));
        fields.put("empty", List.of());
        return new DefaultFormData(fields, files, ConversionService.SHARED);
    }

    @Test
    void textFields() {
        DefaultFormData form = form(Map.of());
        assertEquals(Set.of("name", "age", "ratio", "flag", "letter", "empty"), form.names());
        assertTrue(form.contains("name"));
        assertFalse(form.contains("city"));
        assertEquals(List.of("42", "43"), form.getValues("age"));
        assertEquals(List.of(), form.getValues("city"));
        assertEquals("Fred", form.getString("name"));
        assertEquals(42, form.getInt("age"));
        assertEquals(42L, form.getLong("age"));
        assertEquals(1.5, form.getDouble("ratio"));
        assertEquals(1.5f, form.getFloat("ratio"));
        assertEquals((short) 42, form.getShort("age"));
        assertEquals((byte) 42, form.getByte("age"));
        assertEquals('x', form.getChar("letter"));
        assertTrue(form.getBoolean("flag"));
        assertEquals(Optional.of("Fred"), form.findString("name"));
        assertEquals(OptionalInt.of(42), form.findInt("age"));
        assertEquals(OptionalLong.of(42), form.findLong("age"));
        assertEquals(OptionalDouble.of(1.5), form.findDouble("ratio"));
        assertEquals(Optional.of(true), form.findBoolean("flag"));
        assertEquals(OptionalInt.empty(), form.findInt("city"));
        assertEquals(OptionalLong.empty(), form.findLong("city"));
        assertEquals(OptionalDouble.empty(), form.findDouble("city"));
        assertEquals(Optional.empty(), form.find("empty", String.class));
    }

    @Test
    void defaultValuesOfMissingFields() {
        DefaultFormData form = form(Map.of());
        assertEquals("none", form.getString("city", "none"));
        assertEquals("Fred", form.getString("name", "none"));
        assertEquals(1, form.getInt("city", 1));
        assertEquals(2L, form.getLong("city", 2L));
        assertEquals(3.0, form.getDouble("city", 3.0));
        assertEquals(4.0f, form.getFloat("city", 4.0f));
        assertEquals((short) 5, form.getShort("city", (short) 5));
        assertEquals((byte) 6, form.getByte("city", (byte) 6));
        assertEquals('y', form.getChar("city", 'y'));
        assertTrue(form.getBoolean("city", true));
        assertEquals(7, form.get("city", Integer.class, 7));
    }

    @Test
    void typedFields() {
        DefaultFormData form = form(Map.of());
        assertEquals(List.of(42, 43), form.get("age", Argument.listOf(Integer.class)));
        assertArrayEquals(new Integer[] {42, 43}, form.get("age", Argument.of(Integer[].class)));
        assertEquals(42, form.get("age", Argument.of(Integer.class, "age")));
        assertEquals(Optional.of(42), form.find("age", Argument.of(Integer.class)));
        assertEquals(Optional.empty(), form.find("city", Argument.listOf(Integer.class)));
        assertEquals(Optional.empty(), form.find("empty", Argument.listOf(Integer.class)));
    }

    @Test
    void missingAndUnconvertibleFields() {
        DefaultFormData form = form(Map.of());
        FormFieldException missing = assertThrows(FormFieldException.class, () -> form.getInt("city"));
        assertEquals("city", missing.getFieldName());
        assertThrows(FormFieldException.class, () -> form.getString("empty"));
        assertThrows(FormFieldException.class, () -> form.get("city", Argument.listOf(Integer.class)));
        assertThrows(ConversionErrorException.class, () -> form.getInt("name"));
        FormFieldException unconvertible = assertThrows(FormFieldException.class, () -> form.get("name", DefaultFormDataTest.class));
        assertTrue(unconvertible.getMessage().contains("cannot be converted to"), unconvertible.getMessage());
    }

    @Test
    void files() {
        StubUpload avatar = new StubUpload("avatar", CompletableFuture.completedFuture(null));
        DefaultFormData form = form(Map.of("avatar", List.of(avatar)));
        assertSame(avatar, form.getFile("avatar"));
        assertEquals(Optional.of(avatar), form.findFile("avatar"));
        assertEquals(List.of(avatar), form.getFiles("avatar"));
        assertEquals(Optional.empty(), form.findFile("cover"));
        assertEquals(List.of(), form.getFiles("cover"));
        FormFieldException missing = assertThrows(FormFieldException.class, () -> form.getFile("cover"));
        assertEquals("cover", missing.getFieldName());
        assertTrue(form.toString().contains("avatar"), form.toString());
    }

    @Test
    void closeReleasesTheFilesOnce() throws Exception {
        StubUpload avatar = new StubUpload("avatar", CompletableFuture.completedFuture(null));
        DefaultFormData form = form(Map.of("avatar", List.of(avatar)));
        CompletionStage<Void> closed = form.closeAsync();
        assertSame(closed, form.closeAsync());
        form.close();
        closed.toCompletableFuture().get();
        assertEquals(1, avatar.closes);
        // the text fields stay readable
        assertEquals("Fred", form.getString("name"));
    }

    @Test
    void closeFailsWithTheFirstFailureAndSuppressesTheOthers() {
        IllegalStateException first = new IllegalStateException("first");
        IllegalStateException second = new IllegalStateException("second");
        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        cancelled.cancel(false);
        DefaultFormData form = form(Map.of("docs", List.of(
            new StubUpload("ok", CompletableFuture.completedFuture(null)),
            new StubUpload("first", CompletableFuture.failedFuture(first)),
            new StubUpload("again", CompletableFuture.failedFuture(first)),
            new StubUpload("second", CompletableFuture.failedFuture(second)),
            new StubUpload("cancelled", cancelled))));
        ExecutionException e = assertThrows(ExecutionException.class, () -> form.closeAsync().toCompletableFuture().get());
        assertSame(first, e.getCause());
        assertEquals(2, first.getSuppressed().length);
        assertSame(second, first.getSuppressed()[0]);
        assertInstanceOf(CancellationException.class, first.getSuppressed()[1]);
    }

    /**
     * A file whose release completes with a stage of the test.
     */
    private static final class StubUpload implements FileUpload {
        private final String name;
        private final CompletableFuture<Void> released;
        private int closes;

        StubUpload(String name, CompletableFuture<Void> released) {
            this.name = name;
            this.released = released;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String fileName() {
            return name + ".txt";
        }

        @Override
        public Optional<MediaType> contentType() {
            return Optional.empty();
        }

        @Override
        public OptionalLong size() {
            return OptionalLong.empty();
        }

        @Override
        public OptionalLong expectedSize() {
            return OptionalLong.empty();
        }

        @Override
        public CompletionStage<byte[]> bytes(int maximumBytes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<String> text(int maximumBytes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<String> text(int maximumBytes, Charset charset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<Void> transferTo(Path destination) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<Void> transferTo(OutputStream out) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CloseableByteBody takeBody() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<Void> closeAsync() {
            closes++;
            return released;
        }

        @Override
        public void close() {
            closeAsync();
        }
    }
}
