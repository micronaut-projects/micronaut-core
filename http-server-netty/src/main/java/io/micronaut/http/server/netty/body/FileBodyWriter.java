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

import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.netty.body.NettyMessageBodyWriter;
import io.micronaut.http.server.types.files.SystemFile;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Singleton;

import java.io.File;
import java.io.OutputStream;

@Singleton
public final class FileBodyWriter implements NettyMessageBodyWriter<File> {
    private static final Argument<SystemFile> SYSTEM_FILE_ARGUMENT = Argument.of(SystemFile.class);
    private final SystemFileBodyWriter systemFileBodyWriter;

    public FileBodyWriter(SystemFileBodyWriter systemFileBodyWriter) {
        this.systemFileBodyWriter = systemFileBodyWriter;
    }

    @Override
    public void writeTo(HttpRequest<?> request, MutableHttpResponse<File> outgoingResponse, Argument<File> type, File object, MediaType mediaType, NettyWriteContext nettyContext) throws CodecException {
        SystemFile systemFile = new SystemFile(object);
        MutableHttpResponse<SystemFile> newResponse = outgoingResponse.body(systemFile);
        this.systemFileBodyWriter.writeTo(
            request,
            newResponse,
            SYSTEM_FILE_ARGUMENT,
            systemFile,
            systemFile.getMediaType(),
            nettyContext
        );
    }

    @Override
    public void writeTo(Argument<File> type, File object, MediaType mediaType, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        SystemFile systemFile = new SystemFile(object);
        systemFileBodyWriter.writeTo(
            SYSTEM_FILE_ARGUMENT,
            systemFile,
            systemFile.getMediaType(),
            outgoingHeaders,
            outputStream
        );
    }
}
