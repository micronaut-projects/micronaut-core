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
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReactorInteropTest {
    @Test
    void subscribesToPlainPublisherWithoutReactor() throws Exception {
        AtomicInteger received = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger requested = new AtomicInteger();
        Publisher<Integer> source = subscriber -> subscriber.onSubscribe(new Subscription() {
            private boolean finished;

            @Override
            public void request(long n) {
                requested.addAndGet((int) n);
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
        Subscriber<Integer> subscriber = new Subscriber<>() {
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
        };
        // Reload the interop class without Reactor, but share Reactive Streams interfaces
        // with the test loader. This runs in the normal test suite without a custom task.
        try (URLClassLoader loader = new ReactorHidingClassLoader()) {
            assertThrows(ClassNotFoundException.class, () -> loader.loadClass("reactor.core.CorePublisher"));
            Class<?> interop = loader.loadClass(ReactorInterop.class.getName());
            assertSame(loader, interop.getClassLoader());
            interop.getMethod("subscribe", Publisher.class, Subscriber.class, Supplier.class, Consumer.class)
                .invoke(null, source, subscriber, null, null);
        }
        assertEquals(1, requested.get());
        assertEquals(42, received.get());
        assertEquals(1, completed.get());
    }

    @Internal
    private static final class ReactorHidingClassLoader extends URLClassLoader {
        private ReactorHidingClassLoader() {
            super(new URL[]{ReactorInterop.class.getProtectionDomain().getCodeSource().getLocation()},
                ReactorInteropTest.class.getClassLoader());
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("reactor.")) {
                throw new ClassNotFoundException(name);
            }
            if (name.equals(ReactorInterop.class.getName()) || name.startsWith(ReactorInterop.class.getName() + "$")) {
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    type = findClass(name);
                }
                if (resolve) {
                    resolveClass(type);
                }
                return type;
            }
            return super.loadClass(name, resolve);
        }
    }
}
