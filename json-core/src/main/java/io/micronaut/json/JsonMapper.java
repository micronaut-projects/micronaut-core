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
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import org.reactivestreams.Processor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Common abstraction for mapping json to data structures.
 *
 * @author Jonas Konrad
 * @since 3.1
 */
@Experimental
public interface JsonMapper {
    /**
     * Transform a {@link JsonNode} to a value of the given type.
     *
     * @param tree The input json data.
     * @param type The type to deserialize.
     * @param <T>  Type variable of the return type.
     * @return The deserialized value.
     */
    <T> T readValueFromTree(@NonNull JsonNode tree, @NonNull Argument<T> type) throws IOException;

    /**
     * Transform a {@link JsonNode} to a value of the given type.
     *
     * @param tree The input json data.
     * @param type The type to deserialize.
     * @param <T>  Type variable of the return type.
     * @return The deserialized value.
     */
    default <T> T readValueFromTree(@NonNull JsonNode tree, @NonNull Class<T> type) throws IOException {
        return readValueFromTree(tree, Argument.of(type));
    }

    /**
     * Parse and map json from the given stream.
     *
     * @param inputStream The input data.
     * @param type        The type to deserialize to.
     * @param <T>         Type variable of the return type.
     * @return The deserialized object.
     */
    <T> T readValue(@NonNull InputStream inputStream, @NonNull Argument<T> type) throws IOException;

    /**
     * Parse and map json from the given byte array.
     *
     * @param byteArray The input data.
     * @param type      The type to deserialize to.
     * @param <T>       Type variable of the return type.
     * @return The deserialized object.
     */
    <T> T readValue(@NonNull byte[] byteArray, @NonNull Argument<T> type) throws IOException;

    /**
     * Parse and map json from the given string.
     *
     * @param string The input data.
     * @param type   The type to deserialize to.
     * @param <T>    Type variable of the return type.
     * @return The deserialized object.
     */
    default <T> T readValue(@NonNull String string, @NonNull Argument<T> type) throws IOException {
        return readValue(string.getBytes(StandardCharsets.UTF_8), type);
    }

    /**
     * Create a reactive {@link Processor} that accepts json bytes and parses them as {@link JsonNode}s.
     *
     * @param onSubscribe An additional function to invoke with this processor when the returned processor is subscribed to.
     * @param streamArray Whether to return a top-level json array as a stream of elements rather than a single array.
     * @return The reactive processor.
     */
    @NonNull
    Processor<byte[], JsonNode> createReactiveParser(@NonNull Consumer<Processor<byte[], JsonNode>> onSubscribe, boolean streamArray);

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
     * Write an object as json.
     *
     * @param outputStream The stream to write to.
     * @param object       The object to serialize.
     */
    void writeValue(@NonNull OutputStream outputStream, @Nullable Object object) throws IOException;

    /**
     * Write an object as json.
     *
     * @param object The object to serialize.
     * @return The serialized encoded json.
     */
    byte[] writeValueAsBytes(@Nullable Object object) throws IOException;

    /**
     * Update an object from json data.
     *
     * @param value The object to update.
     * @param tree  The json data to update from.
     * @throws IOException                   If there are any mapping exceptions (e.g. illegal values).
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
}
