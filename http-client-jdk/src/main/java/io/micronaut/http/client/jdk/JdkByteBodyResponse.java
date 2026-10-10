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
package io.micronaut.http.client.jdk;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.stream.AvailableByteArrayBody;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * A response of the JDK client: its body bytes are streamed, or, for an exchange that reads the
 * body whole, already read into an array.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class JdkByteBodyResponse extends BaseHttpResponseAdapter<Object, Object> implements ByteBodyHttpResponse<Object> {

    private final byte @Nullable [] bytes;
    private @Nullable CloseableByteBody body;

    /**
     * @param httpResponse      The response of the JDK client
     * @param bytes             The bytes of the body, if it is read already, else the body of
     *                          the response is a {@link CloseableByteBody}
     * @param conversionService The conversion service
     */
    @SuppressWarnings("unchecked")
    JdkByteBodyResponse(java.net.http.HttpResponse<?> httpResponse, byte @Nullable [] bytes, ConversionService conversionService) {
        super((java.net.http.HttpResponse<Object>) httpResponse, conversionService);
        this.bytes = bytes;
        if (bytes == null) {
            this.body = (CloseableByteBody) httpResponse.body();
        }
    }

    /**
     * @return The response of the JDK client
     */
    java.net.http.HttpResponse<?> jdkResponse() {
        return httpResponse;
    }

    /**
     * @return The bytes of the body if they are read already, else {@code null}
     */
    byte @Nullable [] bytes() {
        return bytes;
    }

    @Override
    public Optional<Object> getBody() {
        return Optional.empty();
    }

    @Override
    public ByteBody byteBody() {
        CloseableByteBody b = body;
        if (b == null) {
            b = AvailableByteArrayBody.create(ByteArrayBufferFactory.INSTANCE, Objects.requireNonNull(bytes));
            body = b;
        }
        return b;
    }

    @Override
    public void close() {
        CloseableByteBody b = body;
        if (b != null) {
            b.close();
        }
    }
}
