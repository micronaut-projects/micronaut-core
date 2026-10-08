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
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicInteger;

/** Standalone optional-linkage probe, independent of server binders and test-engine discovery. */
@Internal
public final class ReactorAbsentInteropProbe {
    private ReactorAbsentInteropProbe() {
    }

    /** @param args Unused */
    public static void main(String[] args) {
        if (ReactorAbsentInteropProbe.class.getClassLoader().getResource("reactor/core/publisher/Mono.class") != null) {
            throw new AssertionError("Reactor must be absent for this probe");
        }
        AtomicInteger received = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        Publisher<Integer> source = subscriber -> subscriber.onSubscribe(new Subscription() {
            private boolean finished;

            @Override
            public void request(long n) {
                if (!finished) {
                    finished = true;
                    subscriber.onNext(42);
                    subscriber.onComplete();
                }
            }

            @Override
            public void cancel() {
                finished = true;
            }
        });
        ReactorInterop.subscribe(source, new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(1);
            }

            @Override
            public void onNext(Integer value) {
                received.set(value);
            }

            @Override
            public void onError(Throwable error) {
                throw new AssertionError(error);
            }

            @Override
            public void onComplete() {
                completed.incrementAndGet();
            }
        }, null, null);
        if (received.get() != 42 || completed.get() != 1) {
            throw new AssertionError("Native publisher did not complete correctly");
        }
    }
}
