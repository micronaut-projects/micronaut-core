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

import io.netty.util.concurrent.Future;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * {@link Publisher} implementation that reads items from a netty {@link Future}.
 *
 * @since 3.5.0
 * @author yawkat
 * @param <T> The message type
 */
final class NettyFuturePublisher<T> implements Publisher<T> {
    private final Future<T> future;
    private final boolean forwardCancel;

    /**
     * @param future        The netty future to use as the source of this publisher
     * @param forwardCancel Whether to forward calls to {@link Subscription#cancel()} to the future.
     */
    NettyFuturePublisher(Future<T> future, boolean forwardCancel) {
        this.future = future;
        this.forwardCancel = forwardCancel;
    }

    @Override
    public void subscribe(Subscriber<? super T> s) {
        s.onSubscribe(new Subscription() {
            boolean requested = false;

            @Override
            public void request(long n) {
                if (!requested) {
                    requested = true;
                    future.addListener((Future<T> f) -> {
                        if (f.isSuccess()) {
                            s.onNext(f.getNow());
                            s.onComplete();
                        } else {
                            s.onError(f.cause());
                        }
                    });
                }
            }

            @Override
            public void cancel() {
                if (forwardCancel) {
                    future.cancel(true);
                }
            }
        });
    }
}
