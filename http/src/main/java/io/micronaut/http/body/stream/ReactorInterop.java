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
package io.micronaut.http.body.stream;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.CorePublisher;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Interop with Reactor sources, the only place the publishers of bodies refer to Reactor: a
 * Reactor source is subscribed to with the Reactor context of the downstream subscriber, and with
 * a discard hook, so that it releases the items it drops when it is cancelled. Any other source
 * is subscribed to directly, without a wrapper. Reactor operators are not used.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class ReactorInterop {

    private ReactorInterop() {
    }

    /**
     * Subscribe to a source.
     *
     * @param source     The source
     * @param subscriber The subscriber
     * @param downstream The subscriber whose Reactor context a Reactor source sees, once there is
     *                   one, or {@code null}
     * @param discard    Releases an item a Reactor source drops, or {@code null}
     * @param <T>        The type of an item
     */
    public static <T> void subscribe(Publisher<T> source,
                                     Subscriber<? super T> subscriber,
                                     @Nullable Supplier<? extends @Nullable Subscriber<?>> downstream,
                                     @Nullable Consumer<Object> discard) {
        if (source instanceof CorePublisher<?>) {
            source.subscribe(new ContextSubscriber<>(subscriber, downstream, discard));
        } else {
            source.subscribe(subscriber);
        }
    }

    /**
     * Passes the signals of a Reactor source on, and gives it a context.
     *
     * @param <T> The type of an item
     */
    private static final class ContextSubscriber<T> implements CoreSubscriber<T> {
        private final Subscriber<? super T> actual;
        private final @Nullable Supplier<? extends @Nullable Subscriber<?>> downstream;
        private final @Nullable Consumer<Object> discard;
        private @Nullable Context context;

        ContextSubscriber(Subscriber<? super T> actual, @Nullable Supplier<? extends @Nullable Subscriber<?>> downstream, @Nullable Consumer<Object> discard) {
            this.actual = actual;
            this.downstream = downstream;
            this.discard = discard;
        }

        @Override
        public Context currentContext() {
            Context c = context;
            if (c != null) {
                return c;
            }
            Subscriber<?> d = downstream == null ? null : downstream.get();
            c = d instanceof CoreSubscriber<?> core ? core.currentContext() : Context.empty();
            if (discard != null) {
                c = Operators.enableOnDiscard(c, discard);
            }
            if (downstream == null || d != null) {
                // the downstream subscriber is known, its context does not change
                context = c;
            }
            return c;
        }

        @Override
        public void onSubscribe(Subscription s) {
            actual.onSubscribe(s);
        }

        @Override
        public void onNext(T t) {
            actual.onNext(t);
        }

        @Override
        public void onError(Throwable t) {
            actual.onError(t);
        }

        @Override
        public void onComplete() {
            actual.onComplete();
        }
    }
}
