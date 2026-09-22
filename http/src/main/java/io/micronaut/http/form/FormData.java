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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.convert.exceptions.ConversionErrorException;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * The submitted form of a request, {@code application/x-www-form-urlencoded} or
 * {@code multipart/form-data}, given to form handler functions:
 *
 * <pre>{@code
 * routes.POST("/profile", (request, pathVariables, form) -> {
 *     String name = form.getString("name");
 *     int age = form.getInt("age");
 *     Optional<FileUpload> avatar = form.findFile("avatar");
 *     ...
 * });
 * }</pre>
 *
 * <p>The whole form is read before the handler runs. Text fields convert like the path
 * variables, with the same accessors, including default values for missing fields, e.g.
 * {@code getInt("quantity", 1)}, with the conversion service of the route: a missing required field or a value that
 * does not convert is answered with 400. Uploaded files are {@link FileUpload}s, stored in
 * memory or on disk depending on the {@code micronaut.server.multipart} configuration, with the
 * limits of that configuration. The files are owned by the request: what was not consumed is
 * released when the request completes, and an operation on a file must be part of the stage the
 * handler returns:</p>
 * <pre>{@code
 * routes.handleFormAsync(HttpMethod.POST, "/profile", (request, pathVariables, form) ->
 *     form.getFile("avatar").transferTo(destination)
 *         .thenApply(done -> HttpResponse.ok(form.getString("displayName"))));
 * }</pre>
 *
 * <p>{@link #close()} releases the files early. The text fields stay readable after closing.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface FormData extends AutoCloseable {

    /**
     * @return The names of the text fields that have a value
     */
    Set<String> names();

    /**
     * @param name The name of the field
     * @return Whether a text field of that name has a value
     */
    boolean contains(String name);

    /**
     * All the values of a text field, e.g. of checkboxes or a multiple select.
     *
     * @param name The name of the field
     * @return The values, in the order they were submitted, or an empty list
     */
    List<String> getValues(String name);

    /**
     * A required text field converted to a type. If the field was submitted several times, the
     * first value is used.
     *
     * @param name The name of the field
     * @param type The type
     * @param <T>  The type
     * @return The value
     * @throws FormFieldException if the field has no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     */
    <T> T get(String name, Class<T> type);

    /**
     * An optional text field converted to a type.
     *
     * @param name The name of the field
     * @param type The type
     * @param <T>  The type
     * @return The value, if present
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    <T> Optional<T> find(String name, Class<T> type);

    /**
     * All the files uploaded in a field. Every call returns the same handles.
     *
     * @param name The name of the field
     * @return The files, in the order they were submitted, or an empty list; immutable
     */
    List<FileUpload> getFiles(String name);

    /**
     * An optional file.
     *
     * @param name The name of the field
     * @return The first file uploaded in the field, if any
     */
    default Optional<FileUpload> findFile(String name) {
        List<FileUpload> files = getFiles(name);
        return files.isEmpty() ? Optional.empty() : Optional.of(files.get(0));
    }

    /**
     * A required file.
     *
     * @param name The name of the field
     * @return The first file uploaded in the field
     * @throws FormFieldException if no file was uploaded in the field, answered with 400
     */
    default FileUpload getFile(String name) {
        return findFile(name).orElseThrow(() -> FormFieldException.missingFile(name));
    }

    /**
     * Release the files that were not consumed yet, and abort the operations on them that are
     * still running. Closing is idempotent, and the same stage is returned on every call. The text
     * fields stay readable.
     *
     * @return Completes when the resources of the files were released, or exceptionally when
     * releasing them failed
     */
    CompletionStage<Void> closeAsync();

    /**
     * Start releasing the files like {@link #closeAsync()}, without waiting for it.
     */
    @Override
    void close();

    /**
     * A required field as a string.
     *
     * @param name The name of the field
     * @return The value
     * @throws FormFieldException if the field has no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     */
    default String getString(String name) {
        return get(name, String.class);
    }

    /**
     * A required field as an {@code int}.
     *
     * @param name The name of the field
     * @return The value
     * @throws FormFieldException if the field has no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     */
    default int getInt(String name) {
        return get(name, Integer.class);
    }

    /**
     * A required field as a {@code long}.
     *
     * @param name The name of the field
     * @return The value
     * @throws FormFieldException if the field has no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     */
    default long getLong(String name) {
        return get(name, Long.class);
    }

    /**
     * A required field as a {@code double}.
     *
     * @param name The name of the field
     * @return The value
     * @throws FormFieldException if the field has no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     */
    default double getDouble(String name) {
        return get(name, Double.class);
    }

    /**
     * A required field as a {@code float}.
     *
     * @param name The name of the field
     * @return The value
     * @throws FormFieldException if the field has no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     */
    default float getFloat(String name) {
        return get(name, Float.class);
    }

    /**
     * A required field as a {@code short}.
     *
     * @param name The name of the field
     * @return The value
     * @throws FormFieldException if the field has no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     */
    default short getShort(String name) {
        return get(name, Short.class);
    }

    /**
     * A required field as a {@code byte}.
     *
     * @param name The name of the field
     * @return The value
     * @throws FormFieldException if the field has no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     */
    default byte getByte(String name) {
        return get(name, Byte.class);
    }

    /**
     * A required field as a {@code char}.
     *
     * @param name The name of the field
     * @return The value
     * @throws FormFieldException if the field has no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     */
    default char getChar(String name) {
        return get(name, Character.class);
    }

    /**
     * A required field as a {@code boolean}.
     *
     * @param name The name of the field
     * @return The value
     * @throws FormFieldException if the field has no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     */
    default boolean getBoolean(String name) {
        return get(name, Boolean.class);
    }

    /**
     * A field converted to a type, or a default value if it has none.
     *
     * @param name         The name of the field
     * @param type         The type
     * @param defaultValue The value to return if the field has no value
     * @param <T>          The type
     * @return The value, or the default value
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default <T> T get(String name, Class<T> type, T defaultValue) {
        return find(name, type).orElse(defaultValue);
    }

    /**
     * A field as a string, or a default value if it has none.
     *
     * @param name         The name of the field
     * @param defaultValue The value to return if the field has no value
     * @return The value, or the default value
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default String getString(String name, String defaultValue) {
        return find(name, String.class).orElse(defaultValue);
    }

    /**
     * A field as an {@code int}, or a default value if it has none.
     *
     * @param name         The name of the field
     * @param defaultValue The value to return if the field has no value
     * @return The value, or the default value
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default int getInt(String name, int defaultValue) {
        return find(name, Integer.class).orElse(defaultValue);
    }

    /**
     * A field as a {@code long}, or a default value if it has none.
     *
     * @param name         The name of the field
     * @param defaultValue The value to return if the field has no value
     * @return The value, or the default value
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default long getLong(String name, long defaultValue) {
        return find(name, Long.class).orElse(defaultValue);
    }

    /**
     * A field as a {@code double}, or a default value if it has none.
     *
     * @param name         The name of the field
     * @param defaultValue The value to return if the field has no value
     * @return The value, or the default value
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default double getDouble(String name, double defaultValue) {
        return find(name, Double.class).orElse(defaultValue);
    }

    /**
     * A field as a {@code float}, or a default value if it has none.
     *
     * @param name         The name of the field
     * @param defaultValue The value to return if the field has no value
     * @return The value, or the default value
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default float getFloat(String name, float defaultValue) {
        return find(name, Float.class).orElse(defaultValue);
    }

    /**
     * A field as a {@code short}, or a default value if it has none.
     *
     * @param name         The name of the field
     * @param defaultValue The value to return if the field has no value
     * @return The value, or the default value
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default short getShort(String name, short defaultValue) {
        return find(name, Short.class).orElse(defaultValue);
    }

    /**
     * A field as a {@code byte}, or a default value if it has none.
     *
     * @param name         The name of the field
     * @param defaultValue The value to return if the field has no value
     * @return The value, or the default value
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default byte getByte(String name, byte defaultValue) {
        return find(name, Byte.class).orElse(defaultValue);
    }

    /**
     * A field as a {@code char}, or a default value if it has none.
     *
     * @param name         The name of the field
     * @param defaultValue The value to return if the field has no value
     * @return The value, or the default value
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default char getChar(String name, char defaultValue) {
        return find(name, Character.class).orElse(defaultValue);
    }

    /**
     * A field as a {@code boolean}, or a default value if it has none.
     *
     * @param name         The name of the field
     * @param defaultValue The value to return if the field has no value
     * @return The value, or the default value
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default boolean getBoolean(String name, boolean defaultValue) {
        return find(name, Boolean.class).orElse(defaultValue);
    }

    /**
     * An optional field as a string.
     *
     * @param name The name of the field
     * @return The value, if present
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default Optional<String> findString(String name) {
        return find(name, String.class);
    }

    /**
     * An optional field as an {@code int}.
     *
     * @param name The name of the field
     * @return The value, if present
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default OptionalInt findInt(String name) {
        Optional<Integer> value = find(name, Integer.class);
        return value.isPresent() ? OptionalInt.of(value.get().intValue()) : OptionalInt.empty();
    }

    /**
     * An optional field as a {@code long}.
     *
     * @param name The name of the field
     * @return The value, if present
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default OptionalLong findLong(String name) {
        Optional<Long> value = find(name, Long.class);
        return value.isPresent() ? OptionalLong.of(value.get().longValue()) : OptionalLong.empty();
    }

    /**
     * An optional field as a {@code double}.
     *
     * @param name The name of the field
     * @return The value, if present
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default OptionalDouble findDouble(String name) {
        Optional<Double> value = find(name, Double.class);
        return value.isPresent() ? OptionalDouble.of(value.get().doubleValue()) : OptionalDouble.empty();
    }

    /**
     * An optional field as a {@code boolean}.
     *
     * @param name The name of the field
     * @return The value, if present
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default Optional<Boolean> findBoolean(String name) {
        return find(name, Boolean.class);
    }
}
