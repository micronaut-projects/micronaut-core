/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.client.netty;

import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.scheduling.instrument.Instrumentation;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * An extension of Netty {@link SimpleChannelInboundHandler} that instruments the channel read handler
 * by using collection of available {@link io.micronaut.scheduling.instrument.InvocationInstrumenterFactory} (such as
 * {@link io.micronaut.http.context.ServerRequestContext#with(io.micronaut.http.HttpRequest, java.util.concurrent.Callable)}) if present during
 * the constructor call of the http client.
 * Thanks to that the {@link io.micronaut.http.context.ServerRequestContext#currentRequest()} returns parent request.
 *
 * @param <I> the type of the inbound message
 */
abstract class SimpleChannelInboundHandlerInstrumented<I> extends SimpleChannelInboundHandler<I> {
    private final InvocationInstrumenter instrumenter;
    private final PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();

    SimpleChannelInboundHandlerInstrumented(InvocationInstrumenter instrumenter) {
        this.instrumenter = instrumenter;
    }

    SimpleChannelInboundHandlerInstrumented(InvocationInstrumenter instrumenter, boolean autoRelease) {
        super(autoRelease);
        this.instrumenter = instrumenter;
    }

    protected abstract void channelReadInstrumented(ChannelHandlerContext ctx, I msg) throws Exception;

    @Override
    protected final void channelRead0(ChannelHandlerContext ctx, I msg) throws Exception {
        try (PropagatedContext.InContext ignore = propagatedContext.propagate()) {
            try (Instrumentation ignored = instrumenter.newInstrumentation()) {
                channelReadInstrumented(ctx, msg);
            }
        }
    }
}
