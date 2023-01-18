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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
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
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Optional;
import java.util.function.Supplier;

import static io.micronaut.http.HttpHeaders.CONTENT_RANGE;

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
    private static final String UNIT_BYTES = "bytes";
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
        delegate.ifPresent(type -> type.process(response));
    }

    @Override
    public ChannelFuture write(HttpRequest<?> request, MutableHttpResponse<?> response, ChannelHandlerContext context) {

        if (response instanceof NettyMutableHttpResponse) {

            NettyMutableHttpResponse<?> nettyResponse = ((NettyMutableHttpResponse<?>) response);

            // Parse the range headers (if any), and determine the position and content length
            // Only `bytes` ranges are supported. Only single ranges are supported. Invalid ranges fall back to returning the full response.
            // See https://httpwg.org/specs/rfc9110.html#field.range
            long fileLength = getLength();
            String rangeHeader = request.getHeaders().get(HttpHeaders.RANGE);
            long position = 0;
            long contentLength = fileLength;
            if (rangeHeader != null
                && request.getMethod() == HttpMethod.GET // A server MUST ignore a Range header field received with a request method that is unrecognized or for which range handling is not defined.
                && rangeHeader.startsWith(UNIT_BYTES) // An origin server MUST ignore a Range header field that contains a range unit it does not understand.
                && response.status() == HttpStatus.OK // The Range header field is evaluated after evaluating the precondition header fields defined in Section 13.1, and only if the result in absence of the Range header field would be a 200 (OK) response.
            ) {
                IntRange range = parseRangeHeader(rangeHeader, fileLength);
                if (range != null // A server that supports range requests MAY ignore or reject a Range header field that contains an invalid ranges-specifier (Section 14.1.1)
                    && range.firstPos < range.lastPos // A server that supports range requests MAY ignore a Range header field when the selected representation has no content (i.e., the selected representation's data is of zero length).
                    && range.firstPos < fileLength
                    && range.lastPos < fileLength
                ) {
                    position = range.firstPos;
                    contentLength = range.lastPos + 1 - range.firstPos;
                    response.status(HttpStatus.PARTIAL_CONTENT);
                    response.header(CONTENT_RANGE, String.format("%s %d-%d/%d", UNIT_BYTES, range.firstPos, range.lastPos, fileLength));
                }
            }

            response.header(HttpHeaders.ACCEPT_RANGES, UNIT_BYTES);
            response.header(HttpHeaders.CONTENT_LENGTH, Long.toString(contentLength));

            // Write the request data
            final DefaultHttpResponse finalResponse = new DefaultHttpResponse(nettyResponse.getNettyHttpVersion(), nettyResponse.getNettyHttpStatus(), nettyResponse.getNettyHeaders());
            if (request instanceof NettyHttpRequest) {
                ((NettyHttpRequest<?>) request).prepareHttp2ResponseIfNecessary(finalResponse);
            }
            context.write(finalResponse, context.voidPromise());

            FileHolder file = new FileHolder(getFile());

            // Write the content.
            if (context.pipeline().get(SslHandler.class) == null &&
                context.pipeline().get(SmartHttpContentCompressor.class).shouldSkip(finalResponse) &&
                !(context.channel() instanceof Http2StreamChannel)) {
                // SSL not enabled - can use zero-copy file transfer.
                context.write(new DefaultFileRegion(file.raf.getChannel(), position, contentLength), context.newProgressivePromise())
                    .addListener(file);
                return context.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
            } else {
                // SSL enabled - cannot use zero-copy file transfer.
                try {
                    // HttpChunkedInput will write the end marker (LastHttpContent) for us.
                    final HttpChunkedInput chunkedInput = new HttpChunkedInput(new ChunkedFile(file.raf, position, contentLength, LENGTH_8K));
                    return context.writeAndFlush(chunkedInput, context.newProgressivePromise())
                        .addListener(file);
                } catch (IOException e) {
                    throw new CustomizableResponseTypeException("Could not read file", e);
                }
            }
        } else {
            throw new IllegalArgumentException("Unsupported response type. Not a Netty response: " + response);
        }
    }

    @Nullable
    private static IntRange parseRangeHeader(String value, long contentLength) {
        int equalsIdx = value.indexOf('=');
        if (equalsIdx < 0 || equalsIdx == value.length() - 1) {
            return null; // Malformed range
        }

        int minusIdx = value.indexOf('-', equalsIdx + 1);
        if (minusIdx < 0) {
            return null; // Malformed range
        }

        String from = value.substring(equalsIdx + 1, minusIdx).trim();
        String to = value.substring(minusIdx + 1).trim();
        try {
            long fromPosition = from.isEmpty() ? 0 : Long.parseLong(from);
            long toPosition = to.isEmpty() ? contentLength - 1 : Long.parseLong(to);
            return new IntRange(fromPosition, toPosition);
        } catch (NumberFormatException e) {
            return null; // Malformed range
        }
    }

    // See https://httpwg.org/specs/rfc9110.html#rule.int-range
    private static class IntRange {
        private final long firstPos;
        private final long lastPos;

        IntRange(long firstPos, long lastPos) {
            this.firstPos = firstPos;
            this.lastPos = lastPos;
        }
    }

    /**
     * Wrapper class around {@link RandomAccessFile} with two purposes: Leak detection, and implementation of
     * {@link ChannelFutureListener} that closes the file when called.
     */
    private static final class FileHolder implements ChannelFutureListener {
        //to avoid initializing Netty at build time
        private static final Supplier<ResourceLeakDetector<RandomAccessFile>> LEAK_DETECTOR = SupplierUtil.memoized(() ->
            ResourceLeakDetectorFactory.instance().newResourceLeakDetector(RandomAccessFile.class));

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
            this.tracker = LEAK_DETECTOR.get().track(raf);
            try {
                this.length = raf.length();
            } catch (IOException e) {
                close();
                throw new CustomizableResponseTypeException("Could not determine file length", e);
            }
        }

        @Override
        public void operationComplete(@NonNull ChannelFuture future) throws Exception {
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
