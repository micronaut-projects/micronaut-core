/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.netty.AbstractNettyHttpRequest;
import io.micronaut.http.netty.NettyMutableHttpResponse;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.http.server.netty.SmartHttpContentCompressor;
import io.micronaut.http.server.netty.types.NettyFileCustomizableResponseType;
import io.micronaut.http.server.types.CustomizableResponseTypeException;
import io.micronaut.http.server.types.files.FileCustomizableResponseType;
import io.micronaut.http.server.types.files.SystemFile;
import io.netty.channel.*;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Optional;

/**
 * Writes a {@link File} to the Netty context.
 *
 * @author James Kleeh
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class NettySystemFileCustomizableResponseType extends SystemFile implements NettyFileCustomizableResponseType {

    private static final int LENGTH_8K = 8192;
    private static final Logger LOG = LoggerFactory.getLogger(NettySystemFileCustomizableResponseType.class);

    protected final RandomAccessFile raf;
    protected final long rafLength;
    protected Optional<FileCustomizableResponseType> delegate = Optional.empty();

    /**
     * @param file The file
     */
    public NettySystemFileCustomizableResponseType(File file) {
        super(file);
        try {
            this.raf = new RandomAccessFile(file, "r");
        } catch (FileNotFoundException e) {
            throw new CustomizableResponseTypeException("Could not find file", e);
        }
        try {
            this.rafLength = raf.length();
        } catch (IOException e) {
            throw new CustomizableResponseTypeException("Could not determine file length", e);
        }
    }

    /**
     * @param delegate The system file customizable response type
     */
    public NettySystemFileCustomizableResponseType(SystemFile delegate) {
        this(delegate.getFile());
        this.delegate = Optional.of(delegate);
    }

    @Override
    public long getLength() {
        return rafLength;
    }

    @Override
    public long getLastModified() {
        return delegate.map(FileCustomizableResponseType::getLastModified).orElse(super.getLastModified());
    }

    @Override
    public MediaType getMediaType() {
        return delegate.map(FileCustomizableResponseType::getMediaType).orElse(super.getMediaType());
    }

    /**
     * @param response The response to modify
     */
    @Override
    public void process(MutableHttpResponse response) {
        response.header(io.micronaut.http.HttpHeaders.CONTENT_LENGTH, String.valueOf(getLength()));
        delegate.ifPresent(type -> type.process(response));
    }

    @Override
    public void write(HttpRequest<?> request, MutableHttpResponse<?> response, ChannelHandlerContext context) {

        if (response instanceof NettyMutableHttpResponse) {

            FullHttpResponse nettyResponse = ((NettyMutableHttpResponse) response).getNativeResponse();

            // Write the request data
            HttpHeaders headers = nettyResponse.headers();
            final DefaultHttpResponse finalResponse = new DefaultHttpResponse(nettyResponse.protocolVersion(), nettyResponse.status(), headers);
            final io.micronaut.http.HttpVersion httpVersion = request.getHttpVersion();
            final boolean isHttp2 = httpVersion == io.micronaut.http.HttpVersion.HTTP_2_0;
            if (isHttp2) {
                if (request instanceof NettyHttpRequest) {
                    final io.netty.handler.codec.http.HttpHeaders nativeHeaders = ((NettyHttpRequest<?>) request).getNativeRequest().headers();
                    final String streamId = nativeHeaders.get(AbstractNettyHttpRequest.STREAM_ID);
                    if (streamId != null) {
                        finalResponse.headers().set(AbstractNettyHttpRequest.STREAM_ID, streamId);
                    }
                }
            }
            context.write(finalResponse, context.voidPromise());

            ChannelFuture sendFileFuture;
            // Write the content.
            if (context.pipeline().get(SslHandler.class) == null && context.pipeline().get(SmartHttpContentCompressor.class).shouldSkip(nettyResponse)) {
                // SSL not enabled - can use zero-copy file transfer.
                sendFileFuture = context.write(new DefaultFileRegion(raf.getChannel(), 0, getLength()), context.newProgressivePromise());
                context.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
            } else {
                // SSL enabled - cannot use zero-copy file transfer.
                try {
                    // HttpChunkedInput will write the end marker (LastHttpContent) for us.
                    sendFileFuture = context.writeAndFlush(new HttpChunkedInput(new ChunkedFile(raf, 0, getLength(), LENGTH_8K)),
                        context.newProgressivePromise());
                } catch (IOException e) {
                    throw new CustomizableResponseTypeException("Could not read file", e);
                }
            }

            sendFileFuture.addListener(future -> {
                try {
                    raf.close();
                } catch (IOException e) {
                    LOG.warn("An error occurred closing the file reference: " + getFile().getAbsolutePath(), e);
                }
            });

        } else {
            throw new IllegalArgumentException("Unsupported response type. Not a Netty response: " + response);
        }
    }
}
