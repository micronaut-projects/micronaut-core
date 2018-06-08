/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.http.server.netty.types.files;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.netty.NettyMutableHttpResponse;
import io.micronaut.http.server.netty.NettyHttpServer;
import io.micronaut.http.server.netty.types.NettyFileCustomizableResponseType;
import io.micronaut.http.server.types.files.StreamedFile;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.stream.ChunkedStream;
import io.netty.handler.stream.ChunkedWriteHandler;

import java.io.InputStream;
import java.net.URL;
import java.util.Optional;

/**
 * Writes an {@link InputStream} to the Netty context.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class NettyStreamedFileCustomizableResponseType extends StreamedFile implements NettyFileCustomizableResponseType {

    /**
     * @param inputStream The input stream
     * @param name        The name
     */
    public NettyStreamedFileCustomizableResponseType(InputStream inputStream, String name) {
        super(inputStream, name);
    }

    /**
     * @param url The URL
     */
    public NettyStreamedFileCustomizableResponseType(URL url) {
        super(url);
    }

    /**
     * @param delegate The streamed file
     */
    public NettyStreamedFileCustomizableResponseType(StreamedFile delegate) {
        super(delegate.getInputStream(), delegate.getName(), delegate.getLastModified(), delegate.getLength());
    }

    @Override
    public void process(MutableHttpResponse response) {
        long length = getLength();
        if (length > -1) {
            response.header(io.micronaut.http.HttpHeaders.CONTENT_LENGTH, String.valueOf(length));
        }
    }

    @Override
    public void write(HttpRequest<?> request, MutableHttpResponse<?> response, ChannelHandlerContext context) {
        if (response instanceof NettyMutableHttpResponse) {
            FullHttpResponse nettyResponse = ((NettyMutableHttpResponse) response).getNativeResponse();

            //The streams codec prevents non full responses from being written
            Optional
                .ofNullable(context.pipeline().get(NettyHttpServer.HTTP_STREAMS_CODEC))
                .ifPresent(handler -> context.pipeline().replace(handler, "chunked-handler", new ChunkedWriteHandler()));

            // Write the request data
            context.write(new DefaultHttpResponse(nettyResponse.protocolVersion(), nettyResponse.status(), nettyResponse.headers()), context.voidPromise());
            context.writeAndFlush(new HttpChunkedInput(new ChunkedStream(getInputStream())));

        } else {
            throw new IllegalArgumentException("Unsupported response type. Not a Netty response: " + response);
        }
    }
}
