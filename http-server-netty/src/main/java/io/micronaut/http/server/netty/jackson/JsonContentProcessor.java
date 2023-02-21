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
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.netty.AbstractHttpContentProcessor;
import io.micronaut.http.server.netty.HttpContentProcessor;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.convert.LazyJsonNode;
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
    private final JsonCounter counter = new JsonCounter();
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

        if (nettyHttpRequest.getContentType()
            .map(mediaType -> mediaType.equals(MediaType.APPLICATION_JSON_TYPE))
            .orElse(false)) {

            // if the content type is application/json, we can only have one root-level value
            counter.noTokenization();
        }
    }

    @Override
    public HttpContentProcessor resultType(Argument<?> type) {

        boolean isJsonStream = nettyHttpRequest.getContentType()
            .map(mediaType -> mediaType.equals(MediaType.APPLICATION_JSON_STREAM_TYPE))
            .orElse(false);

        if (type != null) {
            Class<?> targetType = type.getType();
            if (Publishers.isConvertibleToPublisher(targetType) && !Publishers.isSingle(targetType)) {
                Optional<Argument<?>> genericArgument = type.getFirstTypeVariable();
                if (genericArgument.isPresent() && !Iterable.class.isAssignableFrom(genericArgument.get().getType()) && !isJsonStream) {
                    // if the generic argument is not a iterable type them stream the array into the publisher
                    counter.unwrapTopLevelArray();
                }
            }
        }
        return this;
    }

    @Override
    protected void onData(ByteBufHolder message, Collection<Object> out) throws Throwable {
        ByteBuf content = message.content();
        try {
            countLoop(out, content);
        } catch (Exception e) {
            if (this.buffer != null) {
                this.buffer.release();
                this.buffer = null;
            }
            throw e;
        } finally {
            content.release();
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
                flush(out, content.retainedSlice(
                    Math.toIntExact(start - bias),
                    Math.toIntExact(bufferRegion.end() - start)
                ));
            }
        }
        if (counter.isBuffering()) {
            int currentBufferStart = Math.toIntExact(Math.max(initialPosition, counter.bufferStart()) - bias);
            bufferForNextRun(content.retainedSlice(currentBufferStart, content.writerIndex() - currentBufferStart));
        }
    }

    private void bufferForNextRun(ByteBuf buffer) {
        if (this.buffer == null) {
            this.buffer = buffer.alloc().compositeBuffer(100000);
        }
        this.buffer.addComponent(true, buffer);
    }

    private void flush(Collection<Object> out, ByteBuf completedNode) throws IOException {
        if (this.buffer != null) {
            completedNode = completedNode == null ? this.buffer : this.buffer.addComponent(true, completedNode);
            this.buffer = null;
        }
        ByteBuffer<ByteBuf> wrapped = NettyByteBufferFactory.DEFAULT.wrap(completedNode);
        if (configuration.isEagerParsing()) {
            try {
                out.add(jsonMapper.readValue(wrapped, Argument.of(JsonNode.class)));
            } finally {
                completedNode.release();
            }
        } else {
            out.add(new LazyJsonNode(wrapped));
        }
    }

    @Override
    public void complete(Collection<Object> out) throws Throwable {
        if (this.buffer != null) {
            flush(out, null);
        }
    }
}
