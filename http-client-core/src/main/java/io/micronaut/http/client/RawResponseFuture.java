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
package io.micronaut.http.client;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.body.CloseableByteBody;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CompletableFuture;

/**
 * The {@link CompletableFuture} of an {@link AsyncRawHttpClient} exchange.
 * {@link #cancel(boolean) Cancelling} it cancels the exchange, and a response that arrives after
 * the future was cancelled is closed.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class RawResponseFuture extends CompletableFuture<HttpResponse<?>> {
    private volatile @Nullable Runnable onCancel;

    private RawResponseFuture() {
    }

    /**
     * The future of an exchange flow. Cancelling the future cancels the flow. The request body is
     * closed when the flow completes or the future is cancelled.
     *
     * @param flow        The exchange flow
     * @param requestBody The request body
     * @return The future
     */
    public static RawResponseFuture of(ExecutionFlow<? extends HttpResponse<?>> flow, CloseableByteBody requestBody) {
        RawResponseFuture future = new RawResponseFuture();
        future.onCancel = () -> {
            flow.cancel();
            requestBody.close();
        };
        flow.onComplete((response, error) -> {
            requestBody.close();
            future.deliver(response, error);
        });
        return future;
    }

    /**
     * The future of an exchange publisher, e.g. of a {@link RawHttpClient}. Cancelling the future
     * cancels the subscription.
     *
     * @param publisher The single response publisher
     * @return The future
     */
    public static RawResponseFuture of(Publisher<? extends HttpResponse<?>> publisher) {
        RawResponseFuture future = new RawResponseFuture();
        publisher.subscribe(new Subscriber<HttpResponse<?>>() {
            @Override
            public void onSubscribe(Subscription s) {
                future.onCancel = s::cancel;
                if (future.isCancelled()) {
                    s.cancel();
                } else {
                    s.request(Long.MAX_VALUE);
                }
            }

            @Override
            public void onNext(HttpResponse<?> response) {
                future.deliver(response, null);
            }

            @Override
            public void onError(Throwable t) {
                future.deliver(null, t);
            }

            @Override
            public void onComplete() {
                future.deliver(null, null);
            }
        });
        return future;
    }

    private void deliver(@Nullable HttpResponse<?> response, @Nullable Throwable error) {
        if (error != null) {
            completeExceptionally(error);
        } else if (response == null) {
            if (!isDone()) {
                completeExceptionally(new IllegalStateException("The exchange completed without a response"));
            }
        } else if (!complete(response) && response instanceof ByteBodyHttpResponse<?> byteBodyResponse) {
            // cancelled before the response arrived: nobody takes it
            byteBodyResponse.close();
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean cancelled = super.cancel(mayInterruptIfRunning);
        if (cancelled) {
            Runnable onCancel = this.onCancel;
            if (onCancel != null) {
                onCancel.run();
            }
        }
        return cancelled;
    }
}
