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
import io.micronaut.http.client.exceptions.ResponseClosedException;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Subscription;

import java.io.EOFException;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

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
    @Nullable
    private final Consumer<@Nullable Throwable> onEnd;
    private final AtomicBoolean ended = new AtomicBoolean();

    public ByteBodySubscriber(BodySizeLimits limits) {
        this(limits, null);
    }

    /**
     * @param limits The body size limits
     * @param onEnd  Called once when the body ends: with {@code null} when it is complete, or
     *               when its consumer let the rest go, and with the failure when it failed
     */
    public ByteBodySubscriber(BodySizeLimits limits, @Nullable Consumer<@Nullable Throwable> onEnd) {
        this.mapped = BODY_FACTORY.adapt(defer, limits, null, null);
        this.onEnd = onEnd;
    }

    private void end(@Nullable Throwable failure) {
        if (onEnd != null && ended.compareAndSet(false, true)) {
            onEnd.accept(failure);
        }
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
                // the consumer let the rest of the body go
                end(null);
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
        if (isTruncatedBody(throwable)) {
            throwable = new ResponseClosedException("Connection closed before the response body was received completely", true);
        }
        end(throwable);
        defer.onError(throwable);
    }

    /**
     * Whether the JDK client reports a connection closed before the body was complete: an EOF, or
     * its messages for a fixed-length or a chunked body that ended early.
     */
    static boolean isTruncatedBody(Throwable throwable) {
        if (throwable instanceof EOFException) {
            return true;
        }
        if (throwable instanceof IOException) {
            String message = throwable.getMessage();
            return message != null && (message.startsWith("fixed content-length:") || message.startsWith("chunked transfer encoding"));
        }
        return false;
    }

    @Override
    public void onComplete() {
        end(null);
        defer.onComplete();
    }
}
