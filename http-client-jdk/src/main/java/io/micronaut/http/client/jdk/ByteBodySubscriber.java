/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.client.jdk;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.DelayedSubscriber;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.ReactiveByteBufferByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import org.reactivestreams.Subscription;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * {@link HttpResponse.BodySubscriber} implementation that pushes data into a
 * {@link ReactiveByteBufferByteBody.SharedBuffer}.
 *
 * @since 4.8.0
 * @author Jonas Konrad
 */
@Internal
final class ByteBodySubscriber implements HttpResponse.BodySubscriber<CloseableByteBody> {
    private static final ByteBodyFactory BODY_FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    private final DelayedSubscriber<ReadBuffer> defer = new DelayedSubscriber<>();
    private final CloseableByteBody mapped;

    public ByteBodySubscriber(BodySizeLimits limits) {
        this.mapped = BODY_FACTORY.adapt(defer, limits, null, null);
    }

    @Override
    public CompletionStage<CloseableByteBody> getBody() {
        return CompletableFuture.completedFuture(mapped);
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        defer.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
                subscription.request(n);
            }

            @Override
            public void cancel() {
                subscription.cancel();
            }
        });
    }

    @Override
    public void onNext(List<ByteBuffer> item) {
        if (item.isEmpty()) {
            defer.onNext(BODY_FACTORY.readBufferFactory().createEmpty());
        } else if (item.size() == 1) {
            defer.onNext(BODY_FACTORY.readBufferFactory().adapt(item.get(0)));
        } else {
            List<ReadBuffer> buffers = new ArrayList<>(item.size());
            for (ByteBuffer byteBuffer : item) {
                buffers.add(BODY_FACTORY.readBufferFactory().adapt(byteBuffer));
            }
            defer.onNext(BODY_FACTORY.readBufferFactory().compose(buffers));
        }
    }

    @Override
    public void onError(Throwable throwable) {
        defer.onError(throwable);
    }

    @Override
    public void onComplete() {
        defer.onComplete();
    }
}
