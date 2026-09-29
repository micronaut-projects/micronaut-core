/*
 * Copyright 2017-2025 original authors
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
import io.netty.handler.codec.http2.Http2Settings;
import org.jspecify.annotations.Nullable;

/**
 * Tracks the number of streams the pool may open concurrently on one HTTP/2 connection: the
 * configured limit, capped by the latest {@code SETTINGS_MAX_CONCURRENT_STREAMS} value advertised
 * by the peer. A peer limit of zero is preserved and means that no new streams may be opened
 * until the peer raises the limit again.
 *
 * @since 4.9.0
 */
@Internal
final class Http2StreamLimit {
    private final int configuredLimit;
    private int remoteLimit = Integer.MAX_VALUE;

    Http2StreamLimit(int configuredLimit) {
        this.configuredLimit = configuredLimit;
    }

    /**
     * Apply the given settings of the peer.
     *
     * @param remoteSettings The settings received from the peer, or {@code null} if none have
     *                       been received yet
     * @return The effective concurrent stream limit
     */
    int update(@Nullable Http2Settings remoteSettings) {
        Long maxConcurrentStreams = remoteSettings == null ? null : remoteSettings.maxConcurrentStreams();
        if (maxConcurrentStreams != null) {
            remoteLimit = (int) Math.min(Integer.MAX_VALUE, maxConcurrentStreams);
        }
        return Math.min(configuredLimit, remoteLimit);
    }
}
