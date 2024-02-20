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
package io.micronaut.http.server.netty.handler;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.netty.body.NettyWriteContext;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponse;
import org.reactivestreams.Publisher;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;

/**
 * @since 4.4.0
 */
@Internal
public interface OutboundAccess extends NettyWriteContext {
    @Override
    ByteBufAllocator alloc();

    void attachment(Object attachment);

    void closeAfterWrite();

    @Override
    void writeFull(FullHttpResponse response, boolean headResponse);

    @Override
    void writeStreamed(HttpResponse response, Publisher<HttpContent> content);

    @Override
    void writeStream(HttpResponse response, InputStream stream, ExecutorService executorService);
}
