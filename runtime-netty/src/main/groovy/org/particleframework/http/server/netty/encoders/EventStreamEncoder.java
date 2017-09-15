/*
 * Copyright 2017 original authors
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
package org.particleframework.http.server.netty.encoders;

import com.typesafe.netty.HandlerSubscriber;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import org.particleframework.core.order.Ordered;
import org.particleframework.http.MediaType;
import org.particleframework.http.server.netty.NettyHttpRequest;
import org.particleframework.http.server.netty.NettyHttpServer;
import org.particleframework.http.server.netty.handler.ChannelHandlerFactory;
import org.particleframework.http.sse.Event;
import org.particleframework.http.sse.EventStream;
import org.reactivestreams.Subscription;

import javax.inject.Singleton;
import java.nio.charset.Charset;

/**
 * Encodes a Server Sent Event {@link EventStream}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ChannelHandler.Sharable
public class EventStreamEncoder extends ChannelOutboundHandlerAdapter implements Ordered {

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        NettyHttpRequest request = NettyHttpRequest.lookup(ctx);
        if (msg instanceof EventStream) {
            EventStream stream = (EventStream) msg;
            HttpResponse streamResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            streamResponse.headers().add(HttpHeaderNames.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM);
            ctx.writeAndFlush(streamResponse, promise)
                    .addListener(future -> {
                                ChannelPromise writePromise = (ChannelPromise) future;
                                if (future.isSuccess()) {

                                    Channel channel = writePromise.channel();
                                    HandlerSubscriber handlerSubscriber = new HandlerSubscriber(ctx.executor()) {
                                        @Override
                                        protected void complete() {
                                            channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(future -> {
                                                        if (request == null || !request.getHeaders().isKeepAlive()) {
                                                            super.complete();
                                                        }
                                                    }
                                            );
                                        }
                                    };
                                    channel.pipeline().addLast(handlerSubscriber);
                                    // feed dummy subscription
                                    handlerSubscriber.onSubscribe(new Subscription() {
                                        @Override
                                        public void request(long n) {
                                        }

                                        @Override
                                        public void cancel() {
                                        }
                                    });
                                    stream.accept(handlerSubscriber);
                                }
                            }
                    );
        } else if (msg instanceof Event) {
            Event event = (Event) msg;
            Object data = event.getData();
            if (data instanceof ByteBuf) {
                ByteBuf bytes = (ByteBuf) data;
                ByteBuf eventData = Unpooled.wrappedBuffer(
                        Unpooled.copiedBuffer("data: ", Charset.defaultCharset()),
                        bytes,
                        Unpooled.copiedBuffer("\n\n", Charset.defaultCharset())
                );
                eventData.retain();
                ctx.writeAndFlush(eventData, promise)
                        .addListener(future -> {
                            eventData.release();
                        });
            } else {
                ChannelPipeline pipeline = ctx.pipeline();
                pipeline.addFirst(this);
                ctx.write(event, promise);
            }
        } else {
            ctx.write(msg, promise);
        }
    }

    @Singleton
    public static class Factory implements ChannelHandlerFactory {
        private final EventStreamEncoder encoder = new EventStreamEncoder();

        @Override
        public ChannelHandler build(Channel channel) {
            return encoder;
        }
    }
}
