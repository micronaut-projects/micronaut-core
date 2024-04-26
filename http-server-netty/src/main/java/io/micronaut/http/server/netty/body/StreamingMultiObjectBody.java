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
import io.micronaut.http.netty.PublisherAsBlocking;
import io.micronaut.http.netty.PublisherAsStream;
import io.micronaut.http.netty.reactive.HotObservable;
import io.micronaut.http.server.netty.FormRouteCompleter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.io.InputStream;
import java.util.function.Function;

/**
 * {@link MultiObjectBody} derived from a {@link StreamingByteBody}. Operations are lazy.
 *
 * @since 4.0.0
 * @author Jonas Konrad
 */
@Internal
public final class StreamingMultiObjectBody extends ManagedBody<Publisher<?>> implements MultiObjectBody {
    public StreamingMultiObjectBody(Publisher<?> publisher) {
        super(publisher);
    }

    @Override
    void release(Publisher<?> value) {
        if (value instanceof HotObservable<?> hot) {
            hot.closeIfNoSubscriber();
        }
    }

    @Override
    public InputStream coerceToInputStream(ByteBufAllocator alloc) {
        PublisherAsBlocking<ByteBuf> publisherAsBlocking = new PublisherAsBlocking<>();
        //noinspection unchecked
        ((Publisher<ByteBuf>) claim()).subscribe(publisherAsBlocking);
        return new PublisherAsStream(publisherAsBlocking);
    }

    @Override
    public Publisher<?> asPublisher() {
        return claim();
    }

    @Override
    public MultiObjectBody mapNotNull(Function<Object, Object> transform) {
        return next(new StreamingMultiObjectBody(Flux.from(prepareClaim()).mapNotNull(transform)));
    }

    @Override
    public void handleForm(FormRouteCompleter formRouteCompleter) {
        prepareClaim().subscribe(formRouteCompleter);
        //next(formRouteCompleter);
    }
}
