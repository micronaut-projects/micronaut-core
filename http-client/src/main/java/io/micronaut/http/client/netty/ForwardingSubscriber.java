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

import io.micronaut.core.annotation.Internal;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.FluxSink;

/**
 * {@link Subscriber} implementation that forwards items into a {@link FluxSink}.
 *
 * @since 3.5.0
 * @author yawkat
 * @param <T> The message type
 */
@Internal
final class ForwardingSubscriber<T> implements Subscriber<T> {
    private final FluxSink<T> sink;

    ForwardingSubscriber(FluxSink<T> sink) {
        this.sink = sink;
    }

    @Override
    public void onSubscribe(Subscription s) {
        sink.onRequest(s::request);
    }

    @Override
    public void onNext(T t) {
        sink.next(t);
    }

    @Override
    public void onError(Throwable t) {
        sink.error(t);
    }

    @Override
    public void onComplete() {
        sink.complete();
    }
}
