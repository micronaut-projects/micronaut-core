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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.ResponseBodyWriter;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.server.types.files.SystemFile;
import jakarta.inject.Singleton;

import java.io.File;
import java.io.OutputStream;

/**
 * Body writer for {@link File}s.
 *
 * @since 4.0.0
 * @author Graeme Rocher
 */
@Internal
@Experimental
@Singleton
public final class FileBodyWriter implements ResponseBodyWriter<File> {
    private final SystemFileBodyWriter systemFileBodyWriter;

    public FileBodyWriter(SystemFileBodyWriter systemFileBodyWriter) {
        this.systemFileBodyWriter = systemFileBodyWriter;
    }

    @Override
    public ByteBodyHttpResponse<?> write(@NonNull ByteBodyFactory bodyFactory,
                                         HttpRequest<?> request,
                                         MutableHttpResponse<File> outgoingResponse,
                                         Argument<File> type,
                                         MediaType mediaType,
                                         File object) throws CodecException {
        SystemFile systemFile = new SystemFile(object);
        MutableHttpResponse<SystemFile> newResponse = outgoingResponse.body(systemFile);
        return systemFileBodyWriter.write(bodyFactory, request, newResponse, systemFile);
    }

    @Override
    public CloseableByteBody writePiece(@NonNull ByteBodyFactory bodyFactory,
                                        @NonNull HttpRequest<?> request,
                                        @NonNull HttpResponse<?> response,
                                        @NonNull Argument<File> type,
                                        @NonNull MediaType mediaType,
                                        File object) {
        return systemFileBodyWriter.writePiece(bodyFactory, new SystemFile(object));
    }

    @Override
    public void writeTo(Argument<File> type, MediaType mediaType, File object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        throw new UnsupportedOperationException("Can only be used in a Netty context");
    }
}
