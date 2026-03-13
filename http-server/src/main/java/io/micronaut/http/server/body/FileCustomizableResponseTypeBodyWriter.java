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
package io.micronaut.http.server.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.body.ResponseBodyWriter;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.server.types.files.FileCustomizableResponseType;
import jakarta.inject.Singleton;

import java.io.OutputStream;

@Internal
@Singleton
@Order(-11)
/**
 * Delegates {@link FileCustomizableResponseType} responses to the writer for the runtime subtype.
 * This avoids preselecting the generic JSON writer for declared
 * {@code HttpResponse<FileCustomizableResponseType>} responses.
 *
 * @author Jonas Konrad
 * @since 5.0.0
 */
final class FileCustomizableResponseTypeBodyWriter implements ResponseBodyWriter<FileCustomizableResponseType> {
    private final MessageBodyHandlerRegistry messageBodyHandlerRegistry;

    FileCustomizableResponseTypeBodyWriter(MessageBodyHandlerRegistry messageBodyHandlerRegistry) {
        this.messageBodyHandlerRegistry = messageBodyHandlerRegistry;
    }

    @Override
    public ByteBodyHttpResponse<?> write(ByteBodyFactory bodyFactory,
                                         HttpRequest<?> request,
                                         MutableHttpResponse<FileCustomizableResponseType> outgoingResponse,
                                         Argument<FileCustomizableResponseType> type,
                                         MediaType mediaType,
                                         FileCustomizableResponseType object) throws CodecException {
        return findResponseWriter(object, mediaType).write(bodyFactory, request, outgoingResponse, runtimeType(object), mediaType, object);
    }

    @Override
    public CloseableByteBody writePiece(ByteBodyFactory bodyFactory,
                                        HttpRequest<?> request,
                                        HttpResponse<?> response,
                                        Argument<FileCustomizableResponseType> type,
                                        MediaType mediaType,
                                        FileCustomizableResponseType object) {
        return findResponseWriter(object, mediaType).writePiece(bodyFactory, request, response, runtimeType(object), mediaType, object);
    }

    @Override
    public void writeTo(Argument<FileCustomizableResponseType> type,
                        MediaType mediaType,
                        FileCustomizableResponseType object,
                        MutableHeaders outgoingHeaders,
                        OutputStream outputStream) throws CodecException {
        findResponseWriter(object, mediaType).writeTo(runtimeType(object), mediaType, object, outgoingHeaders, outputStream);
    }

    @Override
    public boolean isBlocking() {
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ResponseBodyWriter<FileCustomizableResponseType> findResponseWriter(FileCustomizableResponseType object,
                                                                                 MediaType mediaType) {
        Argument<FileCustomizableResponseType> runtimeType = runtimeType(object);
        MessageBodyWriter<FileCustomizableResponseType> writer = messageBodyHandlerRegistry.findWriter(runtimeType, mediaType)
            .orElse(null);
        if (writer == null || writer instanceof FileCustomizableResponseTypeBodyWriter) {
            throw new CodecException("Cannot encode FileCustomizableResponseType runtime type [" + runtimeType + "] for media type [" + mediaType + "]");
        }
        return (ResponseBodyWriter) ResponseBodyWriter.wrap(writer);
    }

    @SuppressWarnings("unchecked")
    private static Argument<FileCustomizableResponseType> runtimeType(FileCustomizableResponseType object) {
        return (Argument<FileCustomizableResponseType>) Argument.of(object.getClass());
    }
}
