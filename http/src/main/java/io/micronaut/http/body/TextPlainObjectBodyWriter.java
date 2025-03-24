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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.codec.CodecException;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.OutputStream;

/**
 * The body writer that will call {@link Object#toString()} and write it as a string for content type {@value MediaType#TEXT_PLAIN}.
 *
 * @author Denis Stepanov
 * @since 4.6
 */
@Produces(MediaType.TEXT_PLAIN)
@Consumes(MediaType.TEXT_PLAIN)
@Singleton
@Internal
final class TextPlainObjectBodyWriter implements TypedMessageBodyWriter<Object>, ResponseBodyWriter<Object> {

    @Override
    public Argument<Object> getType() {
        return Argument.OBJECT_ARGUMENT;
    }

    @Override
    public boolean isWriteable(Argument<Object> type, MediaType mediaType) {
        return ClassUtils.isJavaBasicType(type.getType());
    }

    @Override
    public void writeTo(Argument<Object> type, MediaType mediaType, Object object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        outgoingHeaders.setIfMissing(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN);
        try {
            outputStream.write(object.toString().getBytes(MessageBodyWriter.getCharset(mediaType, outgoingHeaders)));
        } catch (IOException e) {
            throw new CodecException("Error writing body text: " + e.getMessage(), e);
        }
    }

    @Override
    public @NonNull CloseableByteBody writePiece(@NonNull ByteBodyFactory bodyFactory, @NonNull HttpRequest<?> request, @NonNull HttpResponse<?> response, @NonNull Argument<Object> type, @NonNull MediaType mediaType, Object object) throws CodecException {
        return bodyFactory.copyOf(object instanceof CharSequence cs ? cs : object.toString(), MessageBodyWriter.getCharset(mediaType, response.getHeaders()));
    }
}
