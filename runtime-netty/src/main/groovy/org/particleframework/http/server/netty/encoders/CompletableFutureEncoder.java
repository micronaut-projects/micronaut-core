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
import org.particleframework.http.server.netty.handler.ChannelHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * Handles {@link CompletableFuture} return types and encodes the result
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ChannelHandler.Sharable
public class CompletableFutureEncoder extends ChannelOutboundHandlerAdapter implements Ordered {
    private static final Logger LOG = LoggerFactory.getLogger(CompletableFutureEncoder.class);

    @Override
    public int getOrder() {
        return ObjectToStringFallbackEncoder.ORDER - 100;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if(msg instanceof CompletableFuture) {
            CompletableFuture<?> future = (CompletableFuture) msg;
            future.whenComplete((BiConsumer<Object, Throwable>) (o, throwable) -> {
                if(throwable != null) {
                    if(LOG.isErrorEnabled()) {
                        LOG.error("Error executing future: " + throwable.getMessage(), throwable);
                    }
                    if(!promise.isDone()) {
                        promise.setFailure(throwable);
                    }
                }
                else {
                    ctx.writeAndFlush(o, promise);
                }
            });
        }
        else {
            ctx.write(msg, promise);
        }
    }

    @Singleton
    public static class CompletableFutureEncoderFactory implements ChannelHandlerFactory {
        private final CompletableFutureEncoder completableFutureEncoder = new CompletableFutureEncoder();

        @Override
        public ChannelHandler build(Channel channel) {
            return completableFutureEncoder;
        }
    }
}
