/*
 * Copyright 2017-2023 original authors
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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.http.MediaType;
import org.reactivestreams.Publisher;

/**
 * Variant of {@link MessageBodyReader} that allows piecewise reading of the input, e.g. for
 * json-stream.<br>
 * todo: what are the semantics of {@code createSpecific}?
 *
 * @param <T> The type to read
 */
@Experimental
public interface PiecewiseMessageBodyReader<T> extends MessageBodyReader<T> {
    Publisher<T> readPiecewise(
        @NonNull Argument<T> type,
        @Nullable MediaType mediaType,
        @NonNull Headers httpHeaders,
        Publisher<ByteBuffer<?>> input
    );
}
