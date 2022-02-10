/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.server.netty.types.files;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.netty.NettyMutableHttpResponse;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.http.server.netty.SmartHttpContentCompressor;
import io.micronaut.http.server.netty.types.NettyFileCustomizableResponseType;
import io.micronaut.http.server.types.CustomizableResponseTypeException;
import io.micronaut.http.server.types.files.FileCustomizableResponseType;
import io.micronaut.http.server.types.files.SystemFile;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;
import org.jetbrains.annotations.NotNull;
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

    protected Optional<FileCustomizableResponseType> delegate = Optional.empty();

    /**
     * @param file The file
     */
    public NettySystemFileCustomizableResponseType(File file) {
        super(file);
        if (!file.canRead()) {
            throw new CustomizableResponseTypeException("Could not find file");
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

            NettyMutableHttpResponse nettyResponse = ((NettyMutableHttpResponse) response);

            // Write the request data
            final DefaultHttpResponse finalResponse = new DefaultHttpResponse(nettyResponse.getNettyHttpVersion(), nettyResponse.getNettyHttpStatus(), nettyResponse.getNettyHeaders());
            if (request instanceof NettyHttpRequest) {
                ((NettyHttpRequest<?>) request).prepareHttp2ResponseIfNecessary(finalResponse);
            }
            context.write(finalResponse, context.voidPromise());

            FileHolder file = new FileHolder(getFile());

            // Write the content.
            if (context.pipeline().get(SslHandler.class) == null && context.pipeline().get(SmartHttpContentCompressor.class).shouldSkip(finalResponse)) {
                // SSL not enabled - can use zero-copy file transfer.
                context.write(new DefaultFileRegion(file.raf.getChannel(), 0, getLength()), context.newProgressivePromise())
                        .addListener(file);
                context.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
            } else {
                // SSL enabled - cannot use zero-copy file transfer.
                try {
                    // HttpChunkedInput will write the end marker (LastHttpContent) for us.
                    final HttpChunkedInput chunkedInput = new HttpChunkedInput(new ChunkedFile(file.raf, 0, getLength(), LENGTH_8K));
                    context.writeAndFlush(chunkedInput, context.newProgressivePromise())
                            .addListener(file);
                } catch (IOException e) {
                    throw new CustomizableResponseTypeException("Could not read file", e);
                }
            }
        } else {
            throw new IllegalArgumentException("Unsupported response type. Not a Netty response: " + response);
        }
    }

    /**
     * Wrapper class around {@link RandomAccessFile} with two purposes: Leak detection, and implementation of
     * {@link ChannelFutureListener} that closes the file when called.
     */
    private static final class FileHolder implements ChannelFutureListener {
        private static final ResourceLeakDetector<RandomAccessFile> LEAK_DETECTOR =
                ResourceLeakDetectorFactory.instance().newResourceLeakDetector(RandomAccessFile.class);

        final RandomAccessFile raf;
        final long length;

        private final ResourceLeakTracker<RandomAccessFile> tracker;

        private final File file;

        FileHolder(File file) {
            this.file = file;
            try {
                this.raf = new RandomAccessFile(file, "r");
            } catch (FileNotFoundException e) {
                throw new CustomizableResponseTypeException("Could not find file", e);
            }
            this.tracker = LEAK_DETECTOR.track(raf);
            try {
                this.length = raf.length();
            } catch (IOException e) {
                close();
                throw new CustomizableResponseTypeException("Could not determine file length", e);
            }
        }

        @Override
        public void operationComplete(@NotNull ChannelFuture future) throws Exception {
            close();
        }

        void close() {
            try {
                raf.close();
            } catch (IOException e) {
                LOG.warn("An error occurred closing the file reference: " + file.getAbsolutePath(), e);
            }
            if (tracker != null) {
                tracker.close(raf);
            }
        }
    }
}
