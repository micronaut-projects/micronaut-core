/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.json;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import org.reactivestreams.Processor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Common abstraction for mapping json to data structures.
 *
 * @author Jonas Konrad
 * @since 3.1
 */
@Experimental
public interface JsonMapper {
    /**
     * Specialize this mapper for the given type. Read and write operations on the returned mapper
     * may only be called with that type.
     *
     * @param type The type to read or write
     * @return The specialized {@link JsonMapper}.
     */
    @NonNull
    default JsonMapper createSpecific(@NonNull Argument<?> type) {
        return this;
    }

    /**
     * Transform a {@link JsonNode} to a value of the given type.
     *
     * @param tree The input json data.
     * @param type The type to deserialize.
     * @param <T> Type variable of the return type.
     * @return The deserialized value.
     * @throws IOException IOException
     */
    <T> T readValueFromTree(@NonNull JsonNode tree, @NonNull Argument<T> type) throws IOException;

    /**
     * Transform a {@link JsonNode} to a value of the given type.
     *
     * @param tree The input json data.
     * @param type The type to deserialize.
     * @param <T> Type variable of the return type.
     * @return The deserialized value.
     * @throws IOException IOException
     */
    default <T> T readValueFromTree(@NonNull JsonNode tree, @NonNull Class<T> type) throws IOException {
        return readValueFromTree(tree, Argument.of(type));
    }

    /**
     * Parse and map json from the given stream.
     *
     * @param inputStream The input data.
     * @param type The type to deserialize to.
     * @param <T> Type variable of the return type.
     * @return The deserialized object.
     * @throws IOException IOException
     */
    <T> T readValue(@NonNull InputStream inputStream, @NonNull Argument<T> type) throws IOException;

    /**
     * Read a value from the given input stream for the given type.
     *
     * @param inputStream The input stream
     * @param type The type
     * @param <T> The generic type
     * @return The value or {@code null} if it decodes to null
     * @throws IOException If an unrecoverable error occurs
     */
    default @Nullable <T> T readValue(@NonNull InputStream inputStream, @NonNull Class<T> type) throws IOException {
        Objects.requireNonNull(type, "Type cannot be null");
        return readValue(inputStream, Argument.of(type));
    }

    /**
     * Parse and map json from the given byte array.
     *
     * @param byteArray The input data.
     * @param type The type to deserialize to.
     * @param <T> Type variable of the return type.
     * @return The deserialized object.
     * @throws IOException IOException
     */
    <T> T readValue(byte @NonNull [] byteArray, @NonNull Argument<T> type) throws IOException;

    /**
     * Parse and map json from the given byte buffer.
     *
     * @param byteBuffer The input data.
     * @param type The type to deserialize to.
     * @param <T> Type variable of the return type.
     * @return The deserialized object.
     * @throws IOException IOException
     */
    default <T> T readValue(@NonNull ByteBuffer<?> byteBuffer, @NonNull Argument<T> type) throws IOException {
        return readValue(byteBuffer.toByteArray(), type);
    }

    /**
     * Parse and map json from the given string.
     *
     * @param string The input data.
     * @param type The type to deserialize to.
     * @param <T> Type variable of the return type.
     * @return The deserialized object.
     * @throws IOException IOException
     */
    default <T> T readValue(@NonNull String string, @NonNull Argument<T> type) throws IOException {
        return readValue(string.getBytes(StandardCharsets.UTF_8), type);
    }

    /**
     * Read a value from the byte array for the given type.
     *
     * @param byteArray The byte array
     * @param type The type
     * @param <T> The generic type
     * @return The value or {@code null} if it decodes to null
     * @throws IOException If an unrecoverable error occurs
     * @since 4.0.0
     */
    default @Nullable <T> T readValue(byte @NonNull [] byteArray, @NonNull Class<T> type) throws IOException {
        Objects.requireNonNull(type, "Type cannot be null");
        return readValue(byteArray, Argument.of(type));
    }

    /**
     * Read a value from the given string for the given type.
     *
     * @param string The string
     * @param type The type
     * @param <T> The generic type
     * @return The value or {@code null} if it decodes to null
     * @throws IOException If an unrecoverable error occurs
     */
    default @Nullable <T> T readValue(@NonNull String string, @NonNull Class<T> type) throws IOException {
        Objects.requireNonNull(type, "Type cannot be null");
        return readValue(string, Argument.of(type));
    }

    /**
     * Create a reactive {@link Processor} that accepts json bytes and parses them as {@link JsonNode}s.
     *
     * @param onSubscribe An additional function to invoke with this processor when the returned processor is subscribed to.
     * @param streamArray Whether to return a top-level json array as a stream of elements rather than a single array.
     * @return The reactive processor.
     */
    @NonNull
    @Deprecated
    default Processor<byte[], JsonNode> createReactiveParser(@NonNull Consumer<Processor<byte[], JsonNode>> onSubscribe, boolean streamArray) {
        throw new UnsupportedOperationException("Reactive parser not supported");
    }

    /**
     * Transform an object value to a json tree.
     *
     * @param value The object value to transform.
     * @return The json representation.
     * @throws IOException If there are any mapping exceptions (e.g. illegal values).
     */
    @NonNull
    JsonNode writeValueToTree(@Nullable Object value) throws IOException;

    /**
     * Transform an object value to a json tree.
     *
     * @param type The object type
     * @param value The object value to transform.
     * @param <T> The type variable of the type.
     * @return The json representation.
     * @throws IOException If there are any mapping exceptions (e.g. illegal values).
     */
    @NonNull
    <T> JsonNode writeValueToTree(@NonNull Argument<T> type, @Nullable T value) throws IOException;

    /**
     * Write an object as json.
     *
     * @param outputStream The stream to write to.
     * @param object The object to serialize.
     * @throws IOException IOException
     */
    void writeValue(@NonNull OutputStream outputStream, @Nullable Object object) throws IOException;

    /**
     * Write an object as json.
     *
     * @param outputStream The stream to write to.
     * @param type The object type
     * @param object The object to serialize.
     * @param <T> The generic type
     * @throws IOException IOException
     */
    <T> void writeValue(@NonNull OutputStream outputStream, @NonNull Argument<T> type, @Nullable T object) throws IOException;

    /**
     * Write an object as json.
     *
     * @param object The object to serialize.
     * @return The serialized encoded json.
     * @throws IOException IOException
     */
    byte[] writeValueAsBytes(@Nullable Object object) throws IOException;

    /**
     * Write an object as json.
     *
     * @param type The object type
     * @param object The object to serialize.
     * @param <T> The generic type
     * @return The serialized encoded json.
     * @throws IOException IOException
     */
    <T> byte[] writeValueAsBytes(@NonNull Argument<T> type, @Nullable T object) throws IOException;

    /**
     * Write the given value as a string.
     *
     * @param object The object
     * @return The string
     * @throws IOException If an unrecoverable error occurs
     * @since 4.0.0
     */
    default @NonNull String writeValueAsString(@NonNull Object object) throws IOException {
        Objects.requireNonNull(object, "Object cannot be null");
        return new String(writeValueAsBytes(object), StandardCharsets.UTF_8);
    }

    /**
     * Write the given value as a string.
     *
     * @param type The type, never {@code null}
     * @param object The object
     * @param <T> The generic type
     * @return The string
     * @throws IOException If an unrecoverable error occurs
     * @since 4.0.0
     */
    default @NonNull <T> String writeValueAsString(@NonNull Argument<T> type, @Nullable T object) throws IOException {
        return writeValueAsString(type, object, StandardCharsets.UTF_8);
    }

    /**
     * Write the given value as a string.
     *
     * @param type The type, never {@code null}
     * @param object The object
     * @param charset The charset, never {@code null}
     * @param <T> The generic type
     * @return The string
     * @throws IOException If an unrecoverable error occurs
     * @since 4.0.0
     */
    default @NonNull <T> String writeValueAsString(@NonNull Argument<T> type, @Nullable T object, Charset charset) throws IOException {
        Objects.requireNonNull(charset, "Charset cannot be null");
        byte[] bytes = writeValueAsBytes(type, object);
        return new String(bytes, charset);
    }

    /**
     * Update an object from json data.
     *
     * @param value The object to update.
     * @param tree The json data to update from.
     * @throws IOException If there are any mapping exceptions (e.g. illegal values).
     * @throws UnsupportedOperationException If this operation is not supported.
     */
    @Experimental
    default void updateValueFromTree(Object value, @NonNull JsonNode tree) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * Create a copy of this mapper with the given json features as returned by {@link #detectFeatures}.
     *
     * @param features The json features to configure.
     * @return A new mapper.
     */
    @NonNull
    default JsonMapper cloneWithFeatures(@NonNull JsonFeatures features) {
        throw new UnsupportedOperationException();
    }

    /**
     * Detect {@link JsonFeatures} from the given annotation data.
     *
     * @param annotations The annotations to scan.
     * @return The json features for use in {@link #cloneWithFeatures}, or an empty optional if there were no feature
     * annotations detected (or feature annotations are not supported).
     */
    @NonNull
    default Optional<JsonFeatures> detectFeatures(@NonNull AnnotationMetadata annotations) {
        return Optional.empty();
    }

    /**
     * Create a copy of this mapper with the given view class.
     *
     * @param viewClass The view class to use for serialization and deserialization.
     * @return A new mapper.
     * @throws UnsupportedOperationException If views are not supported by this mapper.
     */
    @NonNull
    default JsonMapper cloneWithViewClass(@NonNull Class<?> viewClass) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return The configured stream config.
     */
    @NonNull
    JsonStreamConfig getStreamConfig();

    /**
     * Resolves the default {@link JsonMapper}.
     *
     * @return The default {@link JsonMapper}
     * @throws IllegalStateException If no {@link JsonMapper} implementation exists on the classpath.
     * @since 4.0.0
     */
    static @NonNull JsonMapper createDefault() {
        AtomicReference<IllegalStateException> ex = new AtomicReference<>();
        return ServiceLoader.load(JsonMapperSupplier.class).stream()
            .flatMap(p -> {
                try {
                    JsonMapperSupplier supplier = p.get();
                    return Stream.ofNullable(supplier);
                } catch (Exception e) {
                    addException(ex, e);
                    return Stream.empty();
                }
            })
            .sorted(OrderUtil.COMPARATOR_ZERO)
            .flatMap(jsonMapperSupplier -> {
                try {
                    return Stream.of(jsonMapperSupplier.get());
                } catch (Exception e) {
                    addException(ex, e);
                    return Stream.empty();
                }
            })
            .findFirst()
            .orElseThrow(() -> {
                if (ex.get() != null) {
                    return ex.get();
                } else {
                    return new IllegalStateException("No default JsonMapper implementation found");
                }
            });
    }

    private static void addException(AtomicReference<IllegalStateException> ref, Exception toAdd) {
        ref.updateAndGet(existing -> {
            if (existing == null) {
                return new IllegalStateException("Failed to initialize default JsonMapper", toAdd);
            } else {
                existing.addSuppressed(toAdd);
                return existing;
            }
        });
    }
}
