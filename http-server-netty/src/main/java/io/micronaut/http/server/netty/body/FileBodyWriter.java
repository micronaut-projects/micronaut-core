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
package io.micronaut.http.server.netty.body;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.ServerHttpResponse;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.netty.body.NettyBodyWriter;
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
public final class FileBodyWriter implements NettyBodyWriter<File> {
    private final SystemFileBodyWriter systemFileBodyWriter;

    public FileBodyWriter(SystemFileBodyWriter systemFileBodyWriter) {
        this.systemFileBodyWriter = systemFileBodyWriter;
    }

    @Override
    public ServerHttpResponse<?> writeTo(ByteBufferFactory<?, ?> bufferFactory, HttpRequest<?> request, MutableHttpResponse<File> outgoingResponse, Argument<File> type, MediaType mediaType, File object) throws CodecException {
        SystemFile systemFile = new SystemFile(object);
        MutableHttpResponse<SystemFile> newResponse = outgoingResponse.body(systemFile);
        return systemFileBodyWriter.writeTo(request, newResponse, systemFile);
    }

    @Override
    public void writeTo(Argument<File> type, MediaType mediaType, File object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        throw new UnsupportedOperationException("Can only be used in a Netty context");
    }
}
