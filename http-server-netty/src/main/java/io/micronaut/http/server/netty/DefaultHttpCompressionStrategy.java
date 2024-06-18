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
package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.MediaType;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Default implementation of {@link HttpCompressionStrategy}.
 *
 * @author James Kleeh
 * @since 1.2.0
 */
@Internal
@Singleton
final class DefaultHttpCompressionStrategy implements HttpCompressionStrategy {

    private final int compressionThreshold;
    private final int compressionLevel;
    private final int blockSize;
    private final int maxBlockSize;

    /**
     * @param serverConfiguration The netty server configuration
     */
    @Inject
    DefaultHttpCompressionStrategy(NettyHttpServerConfiguration serverConfiguration) {
        this.compressionThreshold = serverConfiguration.getCompressionThreshold();
        this.compressionLevel = serverConfiguration.getCompressionLevel();
        this.blockSize = serverConfiguration.getBlockSize();
        this.maxBlockSize = serverConfiguration.getMaxBlockSize();
    }

    /**
     * @param compressionThreshold The compression threshold
     * @param compressionLevel The compression level (0-9)
     */
    DefaultHttpCompressionStrategy(int compressionThreshold, int compressionLevel, int blockSize, int maxBlockSize) {
        this.compressionThreshold = compressionThreshold;
        this.compressionLevel = compressionLevel;
        this.blockSize = blockSize;
        this.maxBlockSize = maxBlockSize;
    }

    @Override
    public boolean isEnabled() {
        return compressionThreshold >= 0;
    }

    @Override
    public boolean shouldCompress(HttpResponse response) {
        if (!isEnabled()) {
            return false;
        }

        HttpHeaders headers = response.headers();
        String contentType = headers.get(HttpHeaderNames.CONTENT_TYPE);
        Integer contentLength = headers.getInt(HttpHeaderNames.CONTENT_LENGTH);

        return contentType != null &&
                (contentLength == null || contentLength >= compressionThreshold) &&
                MediaType.isTextBased(contentType);
    }

    @Override
    public int getCompressionLevel() {
        return compressionLevel;
    }

    @Override
    public int getBlockSize() {
        return blockSize;
    }

    @Override
    public int getMaxBlockSize() {
        return maxBlockSize;
    }
}
