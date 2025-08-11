/*
 * Copyright 2017-2025 original authors
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

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecException;
import jakarta.inject.Singleton;

import java.io.OutputStream;

/**
 * The body writer for ByteBody.
 *
 * @author Jonas Konrad
 * @since 4.10.0
 */
@Singleton
@BootstrapContextCompatible
@Internal
final class ByteBodyWriter implements TypedMessageBodyWriter<ByteBody>, ResponseBodyWriter<ByteBody> {
    @Override
    public @NonNull CloseableByteBody writePiece(@NonNull ByteBodyFactory bodyFactory, @NonNull HttpRequest<?> request, @NonNull HttpResponse<?> response, @NonNull Argument<ByteBody> type, @NonNull MediaType mediaType, ByteBody object) throws CodecException {
        return object.move();
    }

    @Override
    public @NonNull Argument<ByteBody> getType() {
        return Argument.of(ByteBody.class);
    }

    @Override
    public void writeTo(@NonNull Argument<ByteBody> type, @NonNull MediaType mediaType, ByteBody object, @NonNull MutableHeaders outgoingHeaders, @NonNull OutputStream outputStream) throws CodecException {
        throw new UnsupportedOperationException("Cannot write ByteBody to OutputStream");
    }
}
