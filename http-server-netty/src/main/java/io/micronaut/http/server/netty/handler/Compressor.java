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
package io.micronaut.http.server.netty.handler;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.http.server.netty.DefaultHttpCompressionStrategy;
import io.micronaut.http.server.netty.HttpCompressionStrategy;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.compression.DecompressionException;
import io.netty.handler.codec.compression.DeflateOptions;
import io.netty.handler.codec.compression.GzipOptions;
import io.netty.handler.codec.compression.StandardCompressionOptions;
import io.micronaut.http.netty.compression.NettyZlibCodecFactory;
import io.micronaut.http.netty.compression.DefaultNettyZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Response compressor for the HTTP server. It holds no per-connection state, so the server
 * creates one instance with {@link #create(HttpCompressionStrategy)} and shares it between all
 * of its connections.
 *
 * @since 5.3.0
 */
@Internal
public final class Compressor {
    private final NettyZlibCodecFactory zlibCodecFactory;
    private final HttpCompressionStrategy strategy;
    private final GzipOptions gzipOptions;
    private final DeflateOptions deflateOptions;
    /**
     * The algorithms with options, i.e. those {@link #determineEncoding(Iterator)} may pick.
     */
    private final Set<Algorithm> available;

    Compressor(HttpCompressionStrategy strategy) {
        this(strategy, DefaultNettyZlibCodecFactory.INSTANCE);
    }

    Compressor(HttpCompressionStrategy strategy, NettyZlibCodecFactory zlibCodecFactory) {
        this.zlibCodecFactory = zlibCodecFactory;
        assert strategy.isEnabled();

        this.strategy = strategy;
        // only use configured compression level for gzip and deflate, other algos have different semantics for the level
        GzipOptions stdGzip = StandardCompressionOptions.gzip();
        this.gzipOptions = StandardCompressionOptions.gzip(strategy.getCompressionLevel(), stdGzip.windowBits(), stdGzip.memLevel());
        DeflateOptions stdDeflate = StandardCompressionOptions.deflate();
        this.deflateOptions = StandardCompressionOptions.deflate(strategy.getCompressionLevel(), stdDeflate.windowBits(), stdDeflate.memLevel());
        // snappy, gzip and deflate need no options and are always available
        EnumSet<Algorithm> algorithms = EnumSet.of(Algorithm.SNAPPY, Algorithm.GZIP, Algorithm.DEFLATE);
        if (zlibCodecFactory.isBrotliAvailable()) {
            algorithms.add(Algorithm.BR);
        }
        if (zlibCodecFactory.isZstdAvailable()) {
            algorithms.add(Algorithm.ZSTD);
        }
        this.available = Collections.unmodifiableSet(algorithms);
    }

    /**
     * Create a compressor for the given strategy. The result holds no per-connection state and
     * can be shared by all connections of a server.
     *
     * @param strategy The compression strategy
     * @return The compressor, or {@code null} if compression is disabled
     */
    @Nullable
    public static Compressor create(HttpCompressionStrategy strategy) {
        return create(strategy, DefaultNettyZlibCodecFactory.INSTANCE);
    }

    @Nullable
    Session prepare(ChannelHandlerContext ctx, HttpRequest request, HttpResponse response, long contentLength) {
        // from HttpContentEncoder: isPassthru
        int code = response.status().code();
        if (code < 200 || code == 204 || code == 304 ||
            (request.method().equals(HttpMethod.HEAD) || (request.method().equals(HttpMethod.CONNECT) && code == 200)) ||
            response.protocolVersion() == HttpVersion.HTTP_1_0) {
            return null;
        }
        if (response.headers().contains(HttpHeaderNames.CONTENT_ENCODING)) {
            // already encoded
            return null;
        }
        // check the request accepts a compression we can do before inspecting the content type
        if (!request.headers().contains(HttpHeaderNames.ACCEPT_ENCODING)) {
            // without the header there is nothing to iterate
            return null;
        }
        Algorithm encoding = determineEncoding(request.headers().valueStringIterator(HttpHeaderNames.ACCEPT_ENCODING));
        if (encoding == null) {
            return null;
        }
        if (strategy instanceof DefaultHttpCompressionStrategy def ? !def.shouldCompress(response, contentLength) : !strategy.shouldCompress(response)) {
            return null;
        }
        response.headers().add(HttpHeaderNames.CONTENT_ENCODING, encoding.contentEncoding);
        Supplier<ChannelHandler> handler = switch (encoding) {
            case BR -> zlibCodecFactory::newBrotliEncoder;
            case ZSTD -> () -> zlibCodecFactory.newZstdEncoder(strategy.getCompressionLevel(), strategy.getMaxZstdEncodeSize());
            case SNAPPY -> zlibCodecFactory::newSnappyFrameEncoder;
            case GZIP -> () -> zlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP, gzipOptions.compressionLevel(), gzipOptions.windowBits(), gzipOptions.memLevel());
            case DEFLATE -> () -> zlibCodecFactory.newZlibEncoder(ZlibWrapper.ZLIB, deflateOptions.compressionLevel(), deflateOptions.windowBits(), deflateOptions.memLevel());
        };
        OneShot oneShot = switch (encoding) {
            case GZIP -> (body, allocator) -> zlibCodecFactory.compressZlib(ZlibWrapper.GZIP, gzipOptions.compressionLevel(), gzipOptions.windowBits(), gzipOptions.memLevel(), body, allocator);
            case DEFLATE -> (body, allocator) -> zlibCodecFactory.compressZlib(ZlibWrapper.ZLIB, deflateOptions.compressionLevel(), deflateOptions.windowBits(), deflateOptions.memLevel(), body, allocator);
            default -> null;
        };
        return new Session(ctx, handler, oneShot);
    }

    /**
     * Creates a compressor using the given streaming codec factory.
     *
     * @param strategy The compression policy
     * @param zlibCodecFactory The codec factory
     * @return The compressor, or {@code null} if disabled
     */
    @Nullable
    public static Compressor create(HttpCompressionStrategy strategy, NettyZlibCodecFactory zlibCodecFactory) {
        return strategy.isEnabled() ? new Compressor(strategy, zlibCodecFactory) : null;
    }

    /**
     * Pick the response encoding from the {@code Accept-Encoding} header values among the
     * algorithms this compressor has options for.
     *
     * @param acceptEncodingValues The raw header values
     * @return The algorithm to use, or {@code null} for no compression
     */
    @Nullable
    Algorithm determineEncoding(Iterator<String> acceptEncodingValues) {
        return determineEncoding(acceptEncodingValues, available);
    }

    /**
     * Pick the response encoding from the {@code Accept-Encoding} header values. Each value is
     * a comma-separated list of {@code encoding[;q=weight]} entries; the entries are examined in
     * place, without splitting the header into strings.
     *
     * @param acceptEncodingValues The raw header values
     * @param available            The algorithms that may be chosen
     * @return The algorithm to use, or {@code null} for no compression
     */
    @SuppressWarnings("FloatingPointEquality")
    @Nullable
    static Algorithm determineEncoding(Iterator<String> acceptEncodingValues, Set<Algorithm> available) {
        // from HttpContentCompressor, slightly modified
        float starQ = -1.0f;
        float brQ = -1.0f;
        float zstdQ = -1.0f;
        float snappyQ = -1.0f;
        float gzipQ = -1.0f;
        float deflateQ = -1.0f;
        while (acceptEncodingValues.hasNext()) {
            String header = acceptEncodingValues.next();
            int length = header.length();
            int start = 0;
            while (start <= length) {
                int end = header.indexOf(',', start);
                if (end == -1) {
                    end = length;
                }
                float q = 1.0f;
                int equalsPos = header.indexOf('=', start, end);
                if (equalsPos != -1) {
                    try {
                        q = Float.parseFloat(header.substring(equalsPos + 1, end));
                    } catch (NumberFormatException e) {
                        // Ignore encoding
                        q = 0.0f;
                    }
                }
                if (header.indexOf("*", start, end) != -1) {
                    starQ = q;
                } else if (header.indexOf("br", start, end) != -1 && q > brQ) {
                    brQ = q;
                } else if (header.indexOf("zstd", start, end) != -1 && q > zstdQ) {
                    zstdQ = q;
                } else if (header.indexOf("snappy", start, end) != -1 && q > snappyQ) {
                    snappyQ = q;
                } else if (header.indexOf("gzip", start, end) != -1 && q > gzipQ) {
                    gzipQ = q;
                } else if (header.indexOf("deflate", start, end) != -1 && q > deflateQ) {
                    deflateQ = q;
                }
                start = end + 1;
            }
        }
        // RFC 9110 12.4.2: an encoding the client lists takes its own weight, any other the
        // weight of "*"; a weight of 0 means "not acceptable". The highest weight wins and the
        // order of the Algorithm constants (br, zstd, snappy, gzip, deflate) breaks ties.
        Algorithm best = null;
        float bestQ = 0.0f;
        for (Algorithm algorithm : Algorithm.BY_PREFERENCE) {
            float q = switch (algorithm) {
                case BR -> brQ;
                case ZSTD -> zstdQ;
                case SNAPPY -> snappyQ;
                case GZIP -> gzipQ;
                case DEFLATE -> deflateQ;
            };
            if (q == -1.0f) {
                q = starQ;
            }
            if (q > bestQ && available.contains(algorithm)) {
                best = algorithm;
                bestQ = q;
            }
        }
        return best;
    }

    enum Algorithm {
        BR(HttpHeaderValues.BR),
        ZSTD(HttpHeaderValues.ZSTD),
        SNAPPY(HttpHeaderValues.SNAPPY),
        GZIP(HttpHeaderValues.GZIP),
        DEFLATE(HttpHeaderValues.DEFLATE);

        /**
         * All algorithms, in order of preference when the client gives several the same weight. Kept here rather
         * than in a static field of the compressor, which holds no static state.
         */
        static final Algorithm[] BY_PREFERENCE = values();

        final CharSequence contentEncoding;

        Algorithm(CharSequence contentEncoding) {
            this.contentEncoding = contentEncoding;
        }
    }

    /**
     * Compresses a complete body in one call, or returns {@code null} to stream it instead.
     */
    @FunctionalInterface
    interface OneShot {
        @Nullable
        ByteBuf compress(ByteBuf body, ByteBufAllocator allocator);
    }

    static final class Session {
        private final ChannelHandlerContext ctx;
        private final Supplier<ChannelHandler> handler;
        private final @Nullable OneShot oneShot;
        private @Nullable EmbeddedChannel compressionChannel;
        /**
         * The only data pushed so far, kept back while it may still be the whole body.
         */
        private @Nullable ByteBuf held;
        private @Nullable ByteBuf compressed;
        private boolean pushed = false;
        private boolean finished = false;

        private Session(ChannelHandlerContext ctx, Supplier<ChannelHandler> handler, @Nullable OneShot oneShot) {
            this.ctx = ctx;
            this.handler = handler;
            this.oneShot = oneShot;
        }

        private EmbeddedChannel channel() {
            EmbeddedChannel channel = compressionChannel;
            if (channel == null) {
                channel = new EmbeddedChannel(
                    ctx.channel().id(),
                    ctx.channel().metadata().hasDisconnect(),
                    ctx.channel().config(),
                    handler.get()
                );
                compressionChannel = channel;
            }
            return channel;
        }

        void push(ByteBuf data) {
            if (finished) {
                throw new IllegalStateException("Compression already finished");
            }
            if (!data.isReadable()) {
                data.release();
            } else if (oneShot != null && !pushed) {
                held = data;
                pushed = true;
            } else {
                pushed = true;
                try (OwnedBuffer owned = new OwnedBuffer(data)) {
                    // if creating the encoder fails, nothing else takes the piece
                    streamHeld();
                    channel().writeOutbound(owned.take());
                }
            }
        }

        private void streamHeld() {
            ByteBuf data = held;
            if (data != null) {
                // Creating the encoder may throw; the held body stays owned here until the encoder takes it.
                EmbeddedChannel channel = channel();
                held = null;
                channel.writeOutbound(data);
            }
        }

        void finish() {
            if (finished) {
                return;
            }
            ByteBuf body = held;
            if (body != null) {
                held = null;
                try (OwnedBuffer owned = new OwnedBuffer(body)) {
                    // until the body reaches the encoder, a failure ends the session
                    finished = true;
                    ByteBuf result = Objects.requireNonNull(oneShot).compress(body, ctx.alloc());
                    if (result != null) {
                        // the one-shot result replaces the body, which the owner releases
                        compressed = result;
                        return;
                    }
                    EmbeddedChannel channel = channel();
                    finished = false;
                    channel.writeOutbound(owned.take());
                }
            }
            channel().finish();
            finished = true;
        }

        void discard() {
            ByteBuf body = held;
            if (body != null) {
                held = null;
                body.release();
            }
            ByteBuf result = compressed;
            if (result != null) {
                compressed = null;
                result.release();
            }
            if (!finished) {
                // Without an encoder there is nothing to release; creating one just to discard it would be waste.
                EmbeddedChannel channel = compressionChannel;
                if (channel != null) {
                    try {
                        channel.finishAndReleaseAll();
                    } catch (DecompressionException ignored) {
                        // the output is discarded, so a failure while finishing it is irrelevant
                    }
                }
                finished = true;
            }
        }

        void fixContentLength(HttpResponse hr) {
            if (!finished) {
                throw new IllegalStateException("Compression not finished yet");
            }
            // fix content-length if necessary
            if (hr.headers().contains(HttpHeaderNames.CONTENT_LENGTH)) {
                long newContentLength = 0;
                ByteBuf result = compressed;
                if (result != null) {
                    newContentLength = result.readableBytes();
                } else {
                    for (Object outboundMessage : channel().outboundMessages()) {
                        newContentLength += ((ByteBuf) outboundMessage).readableBytes();
                    }
                }
                ContentLengthValues.set(hr.headers(), newContentLength);
            }
        }

        @Nullable
        ByteBuf poll() {
            ByteBuf result = compressed;
            if (result != null) {
                compressed = null;
                return result;
            }
            // a caller polling before the end streams what it has pushed so far
            streamHeld();
            EmbeddedChannel channel = compressionChannel;
            if (channel == null) {
                return null;
            }
            int n = channel.outboundMessages().size();
            if (n == 0) {
                return null;
            } else if (n == 1) {
                return channel.readOutbound();
            }

            CompositeByteBuf buf = channel.alloc().compositeBuffer(n);
            while (true) {
                ByteBuf item = channel.readOutbound();
                if (item == null) {
                    break;
                }
                buf.addComponent(true, item);
            }
            return buf;
        }
    }
}
