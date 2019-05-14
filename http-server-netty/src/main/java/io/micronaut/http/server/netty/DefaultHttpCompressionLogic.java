/*
 * Copyright 2017-2019 original authors
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

package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.MediaType;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @author James Kleeh
 * @since 1.2.0
 */
@Internal
@Singleton
class DefaultHttpCompressionLogic implements HttpCompressionLogic {

    private final int compressionThreshold;

    /**
     * @param serverConfiguration The netty server configuration
     */
    @Inject
    DefaultHttpCompressionLogic(NettyHttpServerConfiguration serverConfiguration) {
        this.compressionThreshold = serverConfiguration.getCompressionThreshold();
    }

    DefaultHttpCompressionLogic(int compressionThreshold) {
        this.compressionThreshold = compressionThreshold;
    }

    @Override
    public boolean shouldCompress(HttpResponse response) {
        HttpHeaders headers = response.headers();
        String contentType = headers.get(HttpHeaderNames.CONTENT_TYPE);
        Integer contentLength = headers.getInt(HttpHeaderNames.CONTENT_LENGTH);

        return contentType != null &&
                (contentLength == null || contentLength >= compressionThreshold) &&
                MediaType.isTextBased(contentType);
    }
}
