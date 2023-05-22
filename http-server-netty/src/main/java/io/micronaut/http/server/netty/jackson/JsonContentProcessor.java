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
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.netty.body.JsonCounter;
import io.micronaut.http.server.netty.AbstractHttpContentProcessor;
import io.micronaut.http.server.netty.HttpContentProcessor;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.convert.LazyJsonNode;
import io.micronaut.json.tree.JsonNode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.CompositeByteBuf;
import io.netty.handler.codec.http.DefaultHttpContent;

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
public final class JsonContentProcessor extends AbstractHttpContentProcessor {

    private final JsonMapper jsonMapper;
    private final JsonCounter counter = new JsonCounter();
    private boolean tokenize;
    private ByteBuf singleBuffer;
    private CompositeByteBuf compositeBuffer;

    /**
     * @param nettyHttpRequest The Netty Http request
     * @param configuration    The Http server configuration
     * @param jsonMapper        The json codec
     */
    public JsonContentProcessor(
            NettyHttpRequest<?> nettyHttpRequest,
            NettyHttpServerConfiguration configuration,
            JsonMapper jsonMapper) {
        super(nettyHttpRequest, configuration);
        this.jsonMapper = jsonMapper;
        this.tokenize = !hasContentType(MediaType.APPLICATION_JSON_TYPE);
        if (!tokenize) {
            // if the content type is application/json, we can only have one root-level value
            counter.noTokenization();
        }
    }

    @Override
    public HttpContentProcessor resultType(Argument<?> type) {

        boolean isJsonStream = hasContentType(MediaType.APPLICATION_JSON_STREAM_TYPE);

        if (type != null) {
            Class<?> targetType = type.getType();
            if (Publishers.isConvertibleToPublisher(targetType) && !Publishers.isSingle(targetType)) {
                Optional<Argument<?>> genericArgument = type.getFirstTypeVariable();
                if (genericArgument.isPresent() && !Iterable.class.isAssignableFrom(genericArgument.get().getType()) && !isJsonStream) {
                    // if the generic argument is not a iterable type them stream the array into the publisher
                    counter.unwrapTopLevelArray();
                    tokenize = true;
                }
            }
        }
        return this;
    }

    @Override
    public Object processSingle(ByteBuf data) throws Throwable {
        // if data is empty, we return no json nodes, so can't use this method
        if (tokenize || !data.isReadable()) {
            return null;
        }

        if (data.readableBytes() > requestMaxSize) {
            fireExceedsLength(data.readableBytes(), requestMaxSize, new DefaultHttpContent(data));
        }
        int start = data.readerIndex();
        counter.feed(data);
        data.readerIndex(start);
        ByteBuffer<ByteBuf> wrapped = NettyByteBufferFactory.DEFAULT.wrap(data);
        if (((NettyHttpServerConfiguration) configuration).isEagerParsing()) {
            try {
                return jsonMapper.readValue(wrapped, Argument.of(JsonNode.class));
            } finally {
                data.release();
            }
        } else {
            return new LazyJsonNode(wrapped);
        }
    }

    private boolean hasContentType(MediaType expected) {
        Optional<MediaType> actual = nettyHttpRequest.getContentType();
        return actual.isPresent() && actual.get().equals(expected);
    }

    @Override
    protected void onData(ByteBufHolder message, Collection<Object> out) throws Throwable {
        ByteBuf content = message.content();
        try {
            countLoop(out, content);
        } catch (Exception e) {
            releaseBuffers();
            throw e;
        } finally {
            content.release();
        }
    }

    private void releaseBuffers() {
        if (this.singleBuffer != null) {
            this.singleBuffer.release();
            this.singleBuffer = null;
        }
        if (this.compositeBuffer != null) {
            this.compositeBuffer.release();
            this.compositeBuffer = null;
        }
    }

    private void countLoop(Collection<Object> out, ByteBuf content) throws IOException {
        long initialPosition = counter.position();
        long bias = initialPosition - content.readerIndex();
        while (content.isReadable()) {
            counter.feed(content);
            JsonCounter.BufferRegion bufferRegion = counter.pollFlushedRegion();
            if (bufferRegion != null) {
                long start = Math.max(initialPosition, bufferRegion.start());
                buffer(content.retainedSlice(
                    Math.toIntExact(start - bias),
                    Math.toIntExact(bufferRegion.end() - start)
                ));
                flush(out);
            }
        }
        if (counter.isBuffering()) {
            int currentBufferStart = Math.toIntExact(Math.max(initialPosition, counter.bufferStart()) - bias);
            content.readerIndex(currentBufferStart);
            buffer(content.retain());
        }
    }

    private void buffer(ByteBuf buffer) {
        if (this.singleBuffer == null && this.compositeBuffer == null) {
            this.singleBuffer = buffer;
        } else {
            if (this.compositeBuffer == null) {
                // number of components should not be too small to avoid unnecessary consolidation
                this.compositeBuffer = buffer.alloc().compositeBuffer(((NettyHttpServerConfiguration) configuration).getJsonBufferMaxComponents());
                this.compositeBuffer.addComponent(true, this.singleBuffer);
                this.singleBuffer = null;
            }
            this.compositeBuffer.addComponent(true, buffer);
        }
    }

    private void flush(Collection<Object> out) throws IOException {
        ByteBuf completedNode = compositeBuffer == null ? singleBuffer : compositeBuffer;
        ByteBuffer<ByteBuf> wrapped = NettyByteBufferFactory.DEFAULT.wrap(completedNode);
        if (((NettyHttpServerConfiguration) configuration).isEagerParsing()) {
            try {
                out.add(jsonMapper.readValue(wrapped, Argument.of(JsonNode.class)));
            } finally {
                releaseBuffers();
            }
        } else {
            out.add(new LazyJsonNode(wrapped));
            compositeBuffer = null;
            singleBuffer = null;
        }
    }

    @Override
    public void complete(Collection<Object> out) throws Throwable {
        if (this.singleBuffer != null || this.compositeBuffer != null) {
            flush(out);
        }
    }
}
