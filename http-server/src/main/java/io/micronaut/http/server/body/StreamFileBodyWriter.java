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
package io.micronaut.http.server.body;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.ByteBodyHttpResponseWrapper;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.ResponseBodyWriter;
import io.micronaut.http.body.stream.InputStreamByteBody;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.OptionalLong;
import java.util.concurrent.ExecutorService;

/**
 * Body writer for {@link StreamedFile}s.
 *
 * @since 4.0.0
 * @author Graeme Rocher
 */
@Singleton
@Experimental
@Internal
public final class StreamFileBodyWriter extends AbstractFileBodyWriter implements ResponseBodyWriter<StreamedFile> {
    private final ExecutorService ioExecutor;

    StreamFileBodyWriter(HttpServerConfiguration.FileTypeHandlerConfiguration configuration, @Named(TaskExecutors.BLOCKING) ExecutorService ioExecutor) {
        super(configuration);
        this.ioExecutor = ioExecutor;
    }

    @Override
    public ByteBodyHttpResponse<?> write(@NonNull ByteBodyFactory bodyFactory,
                                         HttpRequest<?> request,
                                         MutableHttpResponse<StreamedFile> outgoingResponse,
                                         Argument<StreamedFile> type,
                                         MediaType mediaType,
                                         StreamedFile object) throws CodecException {
        if (handleIfModifiedAndHeaders(request, outgoingResponse, object, outgoingResponse)) {
            return notModified(bodyFactory, outgoingResponse);
        } else {
            return ByteBodyHttpResponseWrapper.wrap(outgoingResponse, writePiece(bodyFactory, request, outgoingResponse, type, mediaType, object));
        }
    }

    @Override
    public CloseableByteBody writePiece(@NonNull ByteBodyFactory bodyFactory,
                                        @NonNull HttpRequest<?> request,
                                        @NonNull HttpResponse<?> response,
                                        @NonNull Argument<StreamedFile> type,
                                        @NonNull MediaType mediaType,
                                        StreamedFile object) {
        long length = object.getLength();
        InputStream inputStream = object.getInputStream();
        return InputStreamByteBody.create(inputStream, length > -1 ? OptionalLong.of(length) : OptionalLong.empty(), ioExecutor, bodyFactory);
    }

    @Override
    public void writeTo(Argument<StreamedFile> type, MediaType mediaType, StreamedFile object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        throw new UnsupportedOperationException("Can only be used in a Netty context");
    }
}
