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

import io.netty.channel.*;
import org.particleframework.core.async.subscriber.CompletionAwareSubscriber;
import org.particleframework.core.order.Ordered;
import org.particleframework.http.server.netty.handler.ChannelOutboundHandlerFactory;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import javax.inject.Singleton;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An encoder for a Reactive streams {@link Publisher}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ChannelHandler.Sharable
@Singleton
public class PublisherEncoder extends ChannelOutboundHandlerAdapter implements Ordered {

    public static final int ORDER = ObjectToStringFallbackEncoder.OBJECT_FALLBACK_ORDER_START - 1000;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof Publisher) {
            Publisher<?> publisher = (Publisher) msg;
            publisher.subscribe(new CompletionAwareSubscriber<Object>() {


                @Override
                protected void doOnSubscribe(Subscription subscription) {
                    subscription.request(1);
                }

                @Override
                protected void doOnNext(Object message) {
                    ctx.pipeline().write(message, promise).addListener(future -> {
                                if (future.isSuccess()) {
                                    subscription.request(1);
                                }
                                else {
                                    Throwable cause = future.cause();
                                    onError(cause);
                                }
                            }
                    );
                }

                @Override
                protected void doOnError(Throwable t) {
                    ctx.pipeline().fireExceptionCaught(t);
                }

                @Override
                protected void doOnComplete() {
                    ctx.flush();
                }
            });
        } else {
            ctx.write(msg, promise);
        }
    }

}
