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

import org.jspecify.annotations.Nullable;
import io.micronaut.http.server.netty.DefaultHttpCompressionStrategy;
import io.micronaut.http.server.netty.HttpCompressionStrategy;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.compression.Brotli;
import io.netty.handler.codec.compression.BrotliEncoder;
import io.netty.handler.codec.compression.BrotliOptions;
import io.netty.handler.codec.compression.DecompressionException;
import io.netty.handler.codec.compression.DeflateOptions;
import io.netty.handler.codec.compression.GzipOptions;
import io.netty.handler.codec.compression.SnappyFrameEncoder;
import io.netty.handler.codec.compression.SnappyOptions;
import io.netty.handler.codec.compression.StandardCompressionOptions;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.compression.Zstd;
import io.netty.handler.codec.compression.ZstdEncoder;
import io.netty.handler.codec.compression.ZstdOptions;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;

import java.util.Iterator;
import java.util.Objects;

final class Compressor {
    private final HttpCompressionStrategy strategy;
    @Nullable
    private final BrotliOptions brotliOptions;
    private final GzipOptions gzipOptions;
    private final DeflateOptions deflateOptions;
    @Nullable
    private final ZstdOptions zstdOptions;
    private final SnappyOptions snappyOptions;

    Compressor(HttpCompressionStrategy strategy) {
        assert strategy.isEnabled();

        this.strategy = strategy;
        // only use configured compression level for gzip and deflate, other algos have different semantics for the level
        this.brotliOptions = Brotli.isAvailable() ? StandardCompressionOptions.brotli() : null;
        GzipOptions stdGzip = StandardCompressionOptions.gzip();
        this.gzipOptions = StandardCompressionOptions.gzip(strategy.getCompressionLevel(), stdGzip.windowBits(), stdGzip.memLevel());
        DeflateOptions stdDeflate = StandardCompressionOptions.deflate();
        this.deflateOptions = StandardCompressionOptions.deflate(strategy.getCompressionLevel(), stdDeflate.windowBits(), stdDeflate.memLevel());
        this.zstdOptions = Zstd.isAvailable()
            ? StandardCompressionOptions.zstd(strategy.getCompressionLevel(),
            StandardCompressionOptions.zstd().blockSize(),
            strategy.getMaxZstdEncodeSize())
            : null;
        this.snappyOptions = StandardCompressionOptions.snappy();
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
        if (strategy instanceof DefaultHttpCompressionStrategy def ? !def.shouldCompress(response, contentLength) : !strategy.shouldCompress(response)) {
            return null;
        }
        if (response.headers().contains(HttpHeaderNames.CONTENT_ENCODING)) {
            // already encoded
            return null;
        }
        Algorithm encoding = determineEncoding(request.headers().valueStringIterator(HttpHeaderNames.ACCEPT_ENCODING));
        if (encoding == null) {
            return null;
        }
        response.headers().add(HttpHeaderNames.CONTENT_ENCODING, encoding.contentEncoding);
        ChannelHandler handler = switch (encoding) {
            case BR -> makeBrotliEncoder();
            case ZSTD -> new ZstdEncoder(Objects.requireNonNull(zstdOptions, "ZSTD not available").compressionLevel(), zstdOptions.blockSize(), strategy.getMaxZstdEncodeSize());
            case SNAPPY -> new SnappyFrameEncoder();
            case GZIP -> ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP, gzipOptions.compressionLevel(), gzipOptions.windowBits(), gzipOptions.memLevel());
            case DEFLATE -> ZlibCodecFactory.newZlibEncoder(ZlibWrapper.ZLIB, deflateOptions.compressionLevel(), deflateOptions.windowBits(), deflateOptions.memLevel());
        };
        return new Session(ctx, handler);
    }

    private BrotliEncoder makeBrotliEncoder() {
        return new BrotliEncoder(Objects.requireNonNull(brotliOptions, "Brotli not available").parameters());
    }

    /**
     * Pick the response encoding from the {@code Accept-Encoding} header values. Each value is
     * a comma-separated list of {@code encoding[;q=weight]} entries; the entries are examined in
     * place, without splitting the header into strings.
     *
     * @param acceptEncodingValues The raw header values
     * @return The algorithm to use, or {@code null} for no compression
     */
    @SuppressWarnings("FloatingPointEquality")
    @Nullable
    Algorithm determineEncoding(Iterator<String> acceptEncodingValues) {
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
                int equalsPos = header.indexOf('=', start);
                if (equalsPos != -1 && equalsPos < end) {
                    try {
                        q = Float.parseFloat(header.substring(equalsPos + 1, end));
                    } catch (NumberFormatException e) {
                        // Ignore encoding
                        q = 0.0f;
                    }
                }
                if (contains(header, start, end, "*")) {
                    starQ = q;
                } else if (contains(header, start, end, "br") && q > brQ) {
                    brQ = q;
                } else if (contains(header, start, end, "zstd") && q > zstdQ) {
                    zstdQ = q;
                } else if (contains(header, start, end, "snappy") && q > snappyQ) {
                    snappyQ = q;
                } else if (contains(header, start, end, "gzip") && q > gzipQ) {
                    gzipQ = q;
                } else if (contains(header, start, end, "deflate") && q > deflateQ) {
                    deflateQ = q;
                }
                start = end + 1;
            }
        }
        if (brQ > 0.0f || zstdQ > 0.0f || snappyQ > 0.0f || gzipQ > 0.0f || deflateQ > 0.0f) {
            if (brQ != -1.0f && brQ >= zstdQ && this.brotliOptions != null) {
                return Algorithm.BR;
            } else if (zstdQ != -1.0f && zstdQ >= snappyQ && this.zstdOptions != null) {
                return Algorithm.ZSTD;
            } else if (snappyQ != -1.0f && snappyQ >= gzipQ && this.snappyOptions != null) {
                return Algorithm.SNAPPY;
            } else if (gzipQ != -1.0f && gzipQ >= deflateQ && this.gzipOptions != null) {
                return Algorithm.GZIP;
            } else if (deflateQ != -1.0f && this.deflateOptions != null) {
                return Algorithm.DEFLATE;
            }
        }
        if (starQ > 0.0f) {
            if (brQ == -1.0f && this.brotliOptions != null) {
                return Algorithm.BR;
            }
            if (zstdQ == -1.0f && this.zstdOptions != null) {
                return Algorithm.ZSTD;
            }
            if (snappyQ == -1.0f && this.snappyOptions != null) {
                return Algorithm.SNAPPY;
            }
            if (gzipQ == -1.0f && this.gzipOptions != null) {
                return Algorithm.GZIP;
            }
            if (deflateQ == -1.0f && this.deflateOptions != null) {
                return Algorithm.DEFLATE;
            }
        }
        return null;
    }

    /**
     * Whether {@code needle} occurs inside {@code s[start, end)}.
     */
    private static boolean contains(String s, int start, int end, String needle) {
        int i = s.indexOf(needle, start);
        return i != -1 && i + needle.length() <= end;
    }

    enum Algorithm {
        BR(HttpHeaderValues.BR),
        ZSTD(HttpHeaderValues.ZSTD),
        SNAPPY(HttpHeaderValues.SNAPPY),
        GZIP(HttpHeaderValues.GZIP),
        DEFLATE(HttpHeaderValues.DEFLATE);

        final CharSequence contentEncoding;

        Algorithm(CharSequence contentEncoding) {
            this.contentEncoding = contentEncoding;
        }
    }

    static final class Session {
        private final EmbeddedChannel compressionChannel;
        private boolean finished = false;

        private Session(ChannelHandlerContext ctx, ChannelHandler handler) {
            compressionChannel = new EmbeddedChannel(
                ctx.channel().id(),
                ctx.channel().metadata().hasDisconnect(),
                ctx.channel().config(),
                handler
            );
        }

        void push(ByteBuf data) {
            if (finished) {
                throw new IllegalStateException("Compression already finished");
            }
            if (data.isReadable()) {
                compressionChannel.writeOutbound(data);
            } else {
                data.release();
            }
        }

        void finish() {
            if (!finished) {
                compressionChannel.finish();
                finished = true;
            }
        }

        void discard() {
            if (!finished) {
                try {
                    compressionChannel.finishAndReleaseAll();
                } catch (DecompressionException ignored) {
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
                for (Object outboundMessage : compressionChannel.outboundMessages()) {
                    newContentLength += ((ByteBuf) outboundMessage).readableBytes();
                }
                hr.headers().set(HttpHeaderNames.CONTENT_LENGTH, newContentLength);
            }
        }

        @Nullable
        ByteBuf poll() {
            int n = compressionChannel.outboundMessages().size();
            if (n == 0) {
                return null;
            } else if (n == 1) {
                return compressionChannel.readOutbound();
            }

            CompositeByteBuf buf = compressionChannel.alloc().compositeBuffer(n);
            while (true) {
                ByteBuf item = compressionChannel.readOutbound();
                if (item == null) {
                    break;
                }
                buf.addComponent(true, item);
            }
            return buf;
        }
    }
}
