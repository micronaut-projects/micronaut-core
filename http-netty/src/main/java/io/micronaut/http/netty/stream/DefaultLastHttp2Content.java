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
package io.micronaut.http.netty.stream;

import io.micronaut.http.netty.LastHttp2Content;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http2.Http2Stream;

/**
 * Represents last HTTP/2 content.
 *
 * @author graemerocher
 * @since 2.1.3
 */
final class DefaultLastHttp2Content extends DefaultLastHttpContent implements LastHttp2Content {
    private final Http2Stream stream;
    public DefaultLastHttp2Content(Http2Stream stream) {
        this.stream = stream;
    }

    public DefaultLastHttp2Content(ByteBuf content, Http2Stream stream) {
        super(content);
        this.stream = stream;
    }

    public DefaultLastHttp2Content(ByteBuf content, boolean validateHeaders, Http2Stream stream) {
        super(content, validateHeaders);
        this.stream = stream;
    }

    @Override
    public Http2Stream stream() {
        return stream;
    }
}
