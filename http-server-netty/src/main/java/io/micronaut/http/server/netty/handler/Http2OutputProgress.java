/*
 * Copyright 2017-2026 original authors
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
import io.netty.channel.Channel;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.handler.codec.http2.Http2ConnectionHandler;

/**
 * Tracks whether an HTTP/2 connection is still sending response data, for the idle timeout.
 * Only data that actually leaves the connection counts: frames added to an outbound buffer that
 * the peer does not read (e.g. a PING ACK each idle period) and WINDOW_UPDATEs that let no data
 * through do not.
 *
 * @since 5.2.21
 */
@Internal
public final class Http2OutputProgress {
    private int lastMessageHashCode;
    private long lastPendingWriteBytes;
    private long lastProgress;
    /**
     * The connection send window as of the last check, plus the connection WINDOW_UPDATEs read
     * since. If the actual window is smaller, response data was sent.
     */
    private long expectedSendWindow;

    /**
     * Called when the peer sends a WINDOW_UPDATE for the connection (stream 0).
     *
     * @param windowSizeIncrement The increment
     */
    public void onConnectionWindowUpdate(int windowSizeIncrement) {
        expectedSendWindow += windowSizeIncrement;
    }

    /**
     * Whether response data was sent since the last call.
     *
     * @param channel           The connection channel
     * @param connectionHandler The HTTP/2 connection handler
     * @return {@code true} if data was sent or drained to the peer
     */
    public boolean progressed(Channel channel, Http2ConnectionHandler connectionHandler) {
        boolean changed = false;
        // data frames sent since the last check use up the connection send window
        int sendWindow = connectionHandler.encoder().flowController().windowSize(connectionHandler.connection().connectionStream());
        if (sendWindow < expectedSendWindow) {
            changed = true;
        }
        expectedSendWindow = sendWindow;
        ChannelOutboundBuffer buf = channel.unsafe().outboundBuffer();
        if (buf == null) {
            return changed;
        }
        // only count draining, like IdleStateHandler does with observeOutput, but without
        // counting a buffer that grows
        Object current = buf.current();
        int messageHashCode = current == null ? 0 : System.identityHashCode(current);
        long pendingWriteBytes = buf.totalPendingWriteBytes();
        long progress = buf.currentProgress();
        if (lastMessageHashCode != 0 && messageHashCode != lastMessageHashCode) {
            // the message that was at the head of the buffer has been written
            changed = true;
        } else if (messageHashCode != 0 && messageHashCode == lastMessageHashCode && progress != lastProgress) {
            // the same message was partially written
            changed = true;
        } else if (pendingWriteBytes < lastPendingWriteBytes) {
            changed = true;
        }
        lastMessageHashCode = messageHashCode;
        lastPendingWriteBytes = pendingWriteBytes;
        lastProgress = progress;
        return changed;
    }
}
