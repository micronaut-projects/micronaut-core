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
package io.micronaut.http.server.netty.jackson;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.netty.AbstractHttpContentProcessor;
import io.micronaut.http.server.netty.HttpContentProcessor;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.CompositeByteBuf;

import java.io.IOException;
import java.util.Collection;
import java.util.Optional;

/**
 * This class will handle subscribing to a JSON stream and binding once the events are complete in a non-blocking
 * manner.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class JsonContentProcessor extends AbstractHttpContentProcessor {

    private final JsonMapper jsonMapper;
    private JsonCounter counter;
    private CompositeByteBuf buffer;

    /**
     * @param nettyHttpRequest The Netty Http request
     * @param configuration    The Http server configuration
     * @param jsonMapper        The json codec
     */
    public JsonContentProcessor(
            NettyHttpRequest<?> nettyHttpRequest,
            HttpServerConfiguration configuration,
            JsonMapper jsonMapper) {
        super(nettyHttpRequest, configuration);
        this.jsonMapper = jsonMapper;
    }

    @Override
    public HttpContentProcessor resultType(Argument<?> type) {
        boolean streamArray = false;

        boolean isJsonStream = nettyHttpRequest.getContentType()
            .map(mediaType -> mediaType.equals(MediaType.APPLICATION_JSON_STREAM_TYPE))
            .orElse(false);

        if (type != null) {
            Class<?> targetType = type.getType();
            if (Publishers.isConvertibleToPublisher(targetType) && !Publishers.isSingle(targetType)) {
                Optional<Argument<?>> genericArgument = type.getFirstTypeVariable();
                if (genericArgument.isPresent() && !Iterable.class.isAssignableFrom(genericArgument.get().getType()) && !isJsonStream) {
                    // if the generic argument is not a iterable type them stream the array into the publisher
                    streamArray = true;
                }
            }
        }
        counter = JsonCounter.create(streamArray);
        return this;
    }

    @Override
    protected void onData(ByteBufHolder message, Collection<Object> out) throws Throwable {
        if (counter == null) {
            resultType(null);
        }

        ByteBuf content = message.content();
        try {
            int end = content.writerIndex();
            int i = content.readerIndex();
            JsonCounter.FeedResult feedResult;
            int bufferStart = -1;
            if (this.buffer != null) {
                bufferStart = i;
            }
            for (; i < end; i++) {
                feedResult = counter.feed(content.getByte(i));
                switch (feedResult) {
                    case MUST_SKIP -> {
                        if (bufferStart != -1) {
                            throw new IllegalStateException("Cannot skip input while buffering");
                        }
                    }
                    case MAY_SKIP -> {
                    }
                    case BUFFER -> {
                        if (bufferStart == -1) {
                            bufferStart = i;
                        }
                    }
                    case FLUSH_AFTER -> {
                        if (bufferStart == -1) {
                            bufferStart = i;
                        }
                        flush(out, content.retainedSlice(bufferStart, i - bufferStart + 1));
                        bufferStart = -1;
                    }
                    case FLUSH_BEFORE_AND_SKIP -> {
                        if (bufferStart == -1) {
                            bufferStart = i;
                        }
                        flush(out, content.retainedSlice(bufferStart, i - bufferStart));
                        bufferStart = -1;
                    }
                }
            }
            if (bufferStart != -1) {
                if (this.buffer == null) {
                    this.buffer = content.alloc().compositeBuffer();
                }
                this.buffer.addComponent(true, content.retainedSlice(bufferStart, i - bufferStart));
            }
        } catch (Exception e) {
            content.release();
            if (this.buffer != null) {
                this.buffer.release();
                this.buffer = null;
            }
            throw e;
        }
        content.release();
    }

    private void flush(Collection<Object> out, ByteBuf completedNode) throws IOException {
        if (this.buffer != null) {
            completedNode = completedNode == null ? this.buffer : this.buffer.addComponent(true, completedNode);
            this.buffer = null;
        }
        try {
            out.add(jsonMapper.readValue(NettyByteBufferFactory.DEFAULT.wrap(completedNode), Argument.of(JsonNode.class)));
        } finally {
            completedNode.release();
        }
    }

    @Override
    public void complete(Collection<Object> out) throws Throwable {
        if (this.buffer != null) {
            flush(out, null);
        }
    }
}
