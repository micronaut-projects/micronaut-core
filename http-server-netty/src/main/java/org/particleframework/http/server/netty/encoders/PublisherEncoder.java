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
    @Override
    public int getOrder() {
        return ObjectToStringFallbackEncoder.ORDER - 100;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof Publisher) {
            Publisher<?> publisher = (Publisher) msg;
            publisher.subscribe(new Subscriber<Object>() {
                Subscription subscription;
                AtomicBoolean complete = new AtomicBoolean(false);

                @Override
                public void onSubscribe(Subscription s) {
                    subscription = s;
                    s.request(1);
                }

                @Override
                public void onNext(Object o) {
                    if (!complete.get()) {
                        ctx.pipeline().write(o, promise).addListener(future -> {
                                    if (future.isSuccess() && !complete.get()) {
                                        subscription.request(1);
                                    }
                                }
                        );
                    }
                }

                @Override
                public void onError(Throwable t) {
                    if(complete.compareAndSet(false, true)) {
                        subscription.cancel();
                        ctx.pipeline().fireExceptionCaught(t);
                    }
                }

                @Override
                public void onComplete() {
                    if (complete.compareAndSet(false, true)) {
                        ctx.flush();
                    }
                }
            });
        } else {
            ctx.write(msg, promise);
        }
    }

}
