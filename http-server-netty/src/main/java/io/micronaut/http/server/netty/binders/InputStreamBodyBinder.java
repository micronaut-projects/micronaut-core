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
package io.micronaut.http.server.netty.binders;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.bind.binders.NonBlockingBodyArgumentBinder;
import io.micronaut.http.server.netty.HttpContentProcessorResolver;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.http.server.netty.NettyHttpServer;
import io.micronaut.http.server.netty.body.ImmediateByteBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Optional;

/**
 * Responsible for binding to a {@link InputStream} argument from the body of the request.
 *
 * @author James Kleeh
 * @since 2.5.0
 */
@Internal
public class InputStreamBodyBinder implements NonBlockingBodyArgumentBinder<InputStream> {

    public static final Argument<InputStream> TYPE = Argument.of(InputStream.class);
    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpServer.class);

    private final HttpContentProcessorResolver processorResolver;

    /**
     * @param processorResolver The http content processor resolver
     */
    public InputStreamBodyBinder(HttpContentProcessorResolver processorResolver) {
        this.processorResolver = processorResolver;
    }

    @Override
    public Argument<InputStream> argumentType() {
        return TYPE;
    }

    @SuppressWarnings("unchecked")
    @Override
    public BindingResult<InputStream> bind(ArgumentConversionContext<InputStream> context, HttpRequest<?> source) {
        if (source instanceof NettyHttpRequest<?> nhr) {
            if (nhr.rootBody() instanceof ImmediateByteBody imm && imm.empty()) {
                return BindingResult.EMPTY;
            }
            try {
                InputStream s = nhr.rootBody().processMulti(processorResolver.resolve(nhr, context.getArgument())).coerceToInputStream(nhr.getChannelHandlerContext().alloc());
                return () -> Optional.of(s);
            } catch (Throwable t) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Server received error for argument [" + context.getArgument() + "]: " + t.getMessage(), t);
                }
                return BindingResult.EMPTY;
            }
        }
        return BindingResult.EMPTY;
    }
}
