/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.server.netty.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.server.netty.FormRouteCompleter;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.json.convert.LazyJsonNode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.util.ReferenceCounted;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * {@link HttpBody} that contains a single object. This is used to implement
 * {@link NettyHttpRequest#getBody()} and {@link java.util.concurrent.CompletableFuture} binding.
 *
 * @since 4.0.0
 * @author Jonas Konrad
 */
@Internal
public final class ImmediateSingleObjectBody extends ManagedBody<Object> implements HttpBody, MultiObjectBody {
    ImmediateSingleObjectBody(Object value) {
        super(value);
    }

    @Override
    void release(Object value) {
        release0(value);
    }

    static void release0(Object value) {
        if (value instanceof LazyJsonNode rc) {
            // need to release LazyJsonNode in case it hasn't been converted yet. But conversion
            // can also happen multiple times, so tryRelease is the best we can do, unfortunately.
            rc.tryRelease();
        } else if (value instanceof ReferenceCounted rc) {
            rc.release();
        }
    }

    /**
     * Get the value and transfer ownership to the caller. The caller must release the value after
     * it's done. Can only be called once.
     *
     * @return The claimed value
     */
    public Object claimForExternal() {
        return claim();
    }

    /**
     * Get the value without transferring ownership. The returned value may become invalid when
     * other code calls {@link #claimForExternal()} or when the netty request is destroyed.
     *
     * @return The unclaimed value
     */
    public Object valueUnclaimed() {
        return value();
    }

    public Optional<ImmediateSingleObjectBody> convert(ConversionService conversionService, ArgumentConversionContext<?> context) {
        Object o = prepareClaim();
        Optional<?> converted;
        if (o instanceof io.netty.util.ReferenceCounted rc) {
            if (rc instanceof ByteBuf byteBuf) {
                converted = conversionService.convert(byteBuf, ByteBuf.class, context.getArgument().getType(), context);
            } else {
                converted = conversionService.convert(rc, context);
            }
            // stolen from NettyConverters.refCountAwareConvert. We don't need the isEmpty branch,
            // because we don't call next() in that case and don't transfer ownership.
            if (converted.isPresent()) {
                rc.touch();
                Object item = converted.get();
                // this is not great, but what can we do?
                boolean targetRefCounted = item instanceof ReferenceCounted || item instanceof io.micronaut.core.io.buffer.ReferenceCounted;
                if (!targetRefCounted) {
                    rc.release();
                }
            }
        } else {
            converted = conversionService.convert(o, context);
        }
        return converted.map(p -> next(new ImmediateSingleObjectBody(p)));
    }

    @Override
    public InputStream coerceToInputStream(ByteBufAllocator alloc) {
        return new ByteBufInputStream((ByteBuf) claim(), true);
    }

    @Override
    public Publisher<?> asPublisher() {
        return Flux.just(claim()).doOnDiscard(ReferenceCounted.class, ReferenceCounted::release);
    }

    @Override
    public MultiObjectBody mapNotNull(Function<Object, Object> transform) {
        Object result = transform.apply(prepareClaim());
        return next(result == null ? new ImmediateMultiObjectBody(List.of()) : new ImmediateSingleObjectBody(result));
    }

    @Override
    public void handleForm(FormRouteCompleter formRouteCompleter) {
        Flux.just(prepareClaim()).subscribe(formRouteCompleter);
        next(formRouteCompleter);
    }
}
