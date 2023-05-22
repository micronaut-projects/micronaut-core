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
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.netty.NettyMutableHttpResponse;
import io.micronaut.http.netty.body.NettyBodyWriter;
import io.micronaut.http.netty.body.NettyWriteContext;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.types.files.StreamedFile;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.stream.ChunkedStream;
import jakarta.inject.Singleton;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Body writer for {@link StreamedFile}s.
 *
 * @since 4.0.0
 * @author Graeme Rocher
 */
@Singleton
@Experimental
@Internal
public final class StreamFileBodyWriter extends AbstractFileBodyWriter implements NettyBodyWriter<StreamedFile> {
    StreamFileBodyWriter(NettyHttpServerConfiguration.FileTypeHandlerConfiguration configuration) {
        super(configuration);
    }

    @Override
    public void writeTo(HttpRequest<?> request, MutableHttpResponse<StreamedFile> outgoingResponse, Argument<StreamedFile> type, MediaType mediaType, StreamedFile object, NettyWriteContext nettyContext) throws CodecException {
        if (outgoingResponse instanceof NettyMutableHttpResponse<?> nettyResponse) {
            if (handleIfModifiedAndHeaders(request, outgoingResponse, object, nettyResponse)) {
                nettyContext.writeFull(notModified(outgoingResponse));
            } else {
                HttpHeaders nettyHeaders = nettyResponse.getNettyHeaders();
                long length = object.getLength();
                if (length > -1) {
                    nettyHeaders.set(HttpHeaderNames.CONTENT_LENGTH, length);
                } else {
                    nettyHeaders.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
                }
                final DefaultHttpResponse finalResponse = new DefaultHttpResponse(
                    nettyResponse.getNettyHttpVersion(),
                    nettyResponse.getNettyHttpStatus(),
                    nettyHeaders
                );
                InputStream inputStream = object.getInputStream();
                HttpChunkedInput chunkedInput = new HttpChunkedInput(new ChunkedStream(inputStream));
                nettyContext.writeChunked(finalResponse, chunkedInput);
            }

        } else {
            throw new IllegalArgumentException("Unsupported response type. Not a Netty response: " + outgoingResponse);
        }
    }

    @Override
    public void writeTo(Argument<StreamedFile> type, MediaType mediaType, StreamedFile object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        throw new UnsupportedOperationException("Can only be used in a Netty context");
    }
}
