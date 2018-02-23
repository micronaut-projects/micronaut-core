/*
 * Copyright 2017 original authors
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
package org.particleframework.http.server.netty.types.files;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.handler.stream.ChunkedStream;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.particleframework.http.MutableHttpResponse;
import org.particleframework.http.server.netty.NettyHttpResponse;
import org.particleframework.http.server.netty.NettyHttpServer;
import org.particleframework.http.server.netty.async.DefaultCloseHandler;
import org.particleframework.http.server.netty.types.NettyFileSpecialType;
import org.particleframework.http.server.types.files.StreamedFileSpecialType;

import java.io.InputStream;
import java.net.URL;
import java.util.Optional;

/**
 * Writes an {@link InputStream} to the Netty context
 *
 * @author James Kleeh
 * @since 1.0
 */
public class NettyStreamedFileSpecialType extends StreamedFileSpecialType implements NettyFileSpecialType {

    public NettyStreamedFileSpecialType(InputStream inputStream, String name) {
        super(inputStream, name);
    }

    public NettyStreamedFileSpecialType(URL url) {
        super(url);
    }

    @Override
    public void process(MutableHttpResponse response) {
        long length = getLength();
        if (length > -1) {
            response.header(org.particleframework.http.HttpHeaders.CONTENT_LENGTH, String.valueOf(length));
        }
    }

    @Override
    public void write(HttpRequest request, NettyHttpResponse response, ChannelHandlerContext context) {
        FullHttpResponse nettyResponse = response.getNativeResponse();

        //The streams codec prevents non full responses from being written
        Optional.ofNullable(context.pipeline().get(NettyHttpServer.HTTP_STREAMS_CODEC))
                .ifPresent(handler ->
                        context.pipeline()
                               .replace(handler, "chunked-handler", new ChunkedWriteHandler())
                );

        // Write the request data
        context.write(new DefaultHttpResponse(nettyResponse.protocolVersion(), nettyResponse.status(), nettyResponse.headers()), context.voidPromise());

        ChannelFuture flushFuture = context.writeAndFlush(new HttpChunkedInput(new ChunkedStream(getInputStream())));

        flushFuture.addListener(new DefaultCloseHandler(context, request, nettyResponse));
    }

}
