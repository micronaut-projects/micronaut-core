/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.client.exceptions.ContentLengthExceededException;
import io.netty.buffer.ByteBuf;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Split buffers into lines for SSE parsing.
 *
 * @author Jonas Konrad
 * @since 4.7.0
 */
@Internal
final class SseSplitter {
    /**
     * Split a single buffer.
     *
     * @param buf The buffer
     * @return The individual split pieces
     */
    @NonNull
    static List<ByteBuf> split(@NonNull ByteBuf buf) {
        buf.touch();
        List<ByteBuf> split = new ArrayList<>();
        while (true) {
            int eol = findEndOfLine(buf);
            if (eol == -1) {
                break;
            }
            // todo: this is necessary because downstream handlers sometimes do the
            //  `if (refcnt > 0) release` pattern. We should eventually fix that.
            split.add(buf.readSlice(eol).copy());
            // skip newline
            if (buf.readByte() == '\r') {
                buf.skipBytes(1);
            }
        }
        split.add(buf);
        return split;
    }

    /**
     * Split a stream of bytes. Any content past the last newline is ignored.
     * (This matches legacy behavior)
     *
     * @param buf The input buffers
     * @param limits Buffer limits
     * @return The output buffers, split into lines
     */
    @NonNull
    static Flux<ByteBuf> split(@NonNull Flux<ByteBuf> buf, @NonNull BodySizeLimits limits) {
        AtomicReference<ByteBuf> last = new AtomicReference<>();
        return buf.concatMapIterable(bb -> {
            ByteBuf joined = last.get();
            if (joined == null) {
                joined = bb;
            } else {
                long combinedLength = (long) joined.readableBytes() + bb.readableBytes();
                if (combinedLength > limits.maxBufferSize()) {
                    bb.release();
                    throw new ContentLengthExceededException(limits.maxBufferSize(), combinedLength);
                }
                joined.writeBytes(bb);
            }
            List<ByteBuf> split = split(joined);
            ByteBuf l = split.get(split.size() - 1);
            // copy & release to avoid endless accumulation in the same buffer
            last.set(l.copy());
            l.release();
            return split.subList(0, split.size() - 1);
        })
            .doOnDiscard(ByteBuf.class, ByteBuf::release)
            .doOnTerminate(() -> {
                // last line is *not* emitted, same as LineBasedFrameDecoder. This line is normally
                // empty anyway
                ByteBuf l = last.get();
                if (l != null) {
                    last.set(null);
                    l.release();
                }
            });
    }

    private static int findEndOfLine(final ByteBuf buffer) {
        // adapted from netty LineBasedFrameDecoder
        int i = buffer.indexOf(buffer.readerIndex(), buffer.readerIndex() + buffer.readableBytes(), (byte) '\n');
        if (i >= 0) {
            if (i > 0 && buffer.getByte(i - 1) == '\r') {
                i--;
            }
            i -= buffer.readerIndex();
        }
        return i;
    }
}
