/*
 * Copyright 2017-2026 original authors
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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.client.exceptions.ContentLengthExceededException;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * Body subscriber that fails and cancels the body once it exceeds the maximum content length.
 *
 * @param <T> The body type
 * @since 4.10.0
 */
@Internal
final class LimitedBodySubscriber<T> implements HttpResponse.BodySubscriber<T> {
    private final HttpResponse.BodySubscriber<T> delegate;
    private final long maxContentLength;
    private long received;
    private boolean failed;
    @Nullable
    private Flow.Subscription subscription;

    LimitedBodySubscriber(HttpResponse.BodySubscriber<T> delegate, long maxContentLength) {
        this.delegate = delegate;
        this.maxContentLength = maxContentLength;
    }

    @Override
    public CompletionStage<T> getBody() {
        return delegate.getBody();
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        delegate.onSubscribe(subscription);
    }

    @Override
    public void onNext(List<ByteBuffer> item) {
        if (failed) {
            return;
        }
        for (ByteBuffer buffer : item) {
            received += buffer.remaining();
        }
        if (received > maxContentLength) {
            failed = true;
            if (subscription != null) {
                subscription.cancel();
            }
            delegate.onError(new ContentLengthExceededException(maxContentLength, received));
            return;
        }
        delegate.onNext(item);
    }

    @Override
    public void onError(Throwable throwable) {
        if (!failed) {
            failed = true;
            delegate.onError(throwable);
        }
    }

    @Override
    public void onComplete() {
        if (!failed) {
            delegate.onComplete();
        }
    }
}
