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
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.particleframework.http.MutableHttpResponse;
import org.particleframework.http.server.netty.NettyHttpResponse;
import org.particleframework.http.server.netty.NettyHttpServer;
import org.particleframework.http.server.netty.async.DefaultCloseHandler;
import org.particleframework.http.server.netty.types.NettyFileSpecialType;
import org.particleframework.http.server.types.SpecialTypeHandlerException;
import org.particleframework.http.server.types.files.SystemFileSpecialType;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Optional;

/**
 * Writes a {@link File} to the Netty context
 *
 * @author James Kleeh
 * @since 1.0
 */
public class NettySystemFileSpecialType extends SystemFileSpecialType implements NettyFileSpecialType {

    protected final RandomAccessFile raf;
    protected final long rafLength;
    protected Optional<SystemFileSpecialType> delegate = Optional.empty();

    public NettySystemFileSpecialType(File file) {
        super(file);
        try {
            this.raf = new RandomAccessFile(file, "r");
        } catch (FileNotFoundException e) {
            throw new SpecialTypeHandlerException("Could not find file", e);
        }
        try {
            this.rafLength = raf.length();
        } catch (IOException e) {
            throw new SpecialTypeHandlerException("Could not determine file length", e);
        }
    }

    public NettySystemFileSpecialType(SystemFileSpecialType delegate) {
        this(delegate.getFile());
        this.delegate = Optional.of(delegate);
    }

    @Override
    public long getLength() {
        return rafLength;
    }

    @Override
    public long getLastModified() {
        return delegate.map(SystemFileSpecialType::getLastModified).orElse(super.getLastModified());
    }

    @Override
    public String getName() {
        return delegate.map(SystemFileSpecialType::getName).orElse(super.getName());
    }

    public void process(MutableHttpResponse response) {
        response.header(org.particleframework.http.HttpHeaders.CONTENT_LENGTH, String.valueOf(getLength()));
        delegate.ifPresent((type) -> type.process(response));
    }

    public void write(HttpRequest request, NettyHttpResponse response, ChannelHandlerContext context) {

        FullHttpResponse nettyResponse = response.getNativeResponse();

        //The streams codec prevents non full responses from being written
        Optional.ofNullable(context.pipeline().get(NettyHttpServer.HTTP_STREAMS_CODEC))
                .ifPresent(handler -> {
                    context.pipeline()
                            .replace(handler, "chunked-handler", new ChunkedWriteHandler());
                });

        // Write the request data
        context.write(new DefaultHttpResponse(nettyResponse.protocolVersion(), nettyResponse.status(), nettyResponse.headers()), context.voidPromise());

        // Write the content.
        ChannelFuture flushFuture;
        if (context.pipeline().get(SslHandler.class) == null) {
            context.write(new DefaultFileRegion(raf.getChannel(), 0, getLength()), context.newProgressivePromise());
            // Write the end marker.
            flushFuture = context.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        } else {
            try {
                // HttpChunkedInput will write the end marker (LastHttpContent) for us.
                flushFuture = context.writeAndFlush(new HttpChunkedInput(new ChunkedFile(raf, 0, getLength(), 8192)),
                        context.newProgressivePromise());
            } catch (IOException e) {
                throw new SpecialTypeHandlerException("Could not read file", e);
            }
        }

        flushFuture.addListener(new DefaultCloseHandler(context, request, nettyResponse));
    }

}
