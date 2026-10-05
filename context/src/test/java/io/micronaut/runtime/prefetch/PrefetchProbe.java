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
package io.micronaut.runtime.prefetch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * What the fixture references report to. A test can define them with a class loader of its own,
 * so that their static initializers run again, and they then reach the test only through this
 * class, which the test's class loader defines.
 */
public final class PrefetchProbe {

    public static final String INITIALIZED = "initialized";
    public static final String CONSTRUCTED = "constructed";

    private static final List<Event> EVENTS = new CopyOnWriteArrayList<>();
    private static final Map<String, Throwable> THROWN = new ConcurrentHashMap<>();
    private static volatile CountDownLatch entered = new CountDownLatch(1);
    private static volatile CountDownLatch released = new CountDownLatch(1);

    private PrefetchProbe() {
    }

    /**
     * One thing a fixture did.
     *
     * @param kind {@link #INITIALIZED} or {@link #CONSTRUCTED}
     * @param name The fixture
     * @param thread The thread it happened on
     */
    public record Event(String kind, String name, Thread thread) {
    }

    public static void initialized(String name) {
        add(INITIALIZED, name);
    }

    public static void constructed(String name) {
        add(CONSTRUCTED, name);
    }

    public static void failRuntime(String name) {
        IllegalStateException failure = new IllegalStateException("probe failure of " + name);
        THROWN.put(name, failure);
        add(INITIALIZED, name);
        throw failure;
    }

    public static void failField(String name) {
        add(INITIALIZED, name);
        throw new NoSuchFieldError("probe field of " + name);
    }

    public static void failLinkage(String name) {
        add(INITIALIZED, name);
        throw new NoClassDefFoundError("probe/Missing");
    }

    /**
     * Keeps a static initializer running until {@link #release()}.
     *
     * @param name The fixture
     */
    public static void block(String name) {
        add(INITIALIZED, name);
        entered.countDown();
        try {
            if (!released.await(60, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the test never released " + name);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public static void reset() {
        EVENTS.clear();
        THROWN.clear();
        entered = new CountDownLatch(1);
        released = new CountDownLatch(1);
    }

    public static boolean awaitEntered() throws InterruptedException {
        return entered.await(60, TimeUnit.SECONDS);
    }

    public static void release() {
        released.countDown();
    }

    public static Throwable thrown(String name) {
        return THROWN.get(name);
    }

    public static List<Event> events(String kind, String name) {
        List<Event> found = new ArrayList<>();
        for (Event event : EVENTS) {
            if (event.kind().equals(kind) && event.name().equals(name)) {
                found.add(event);
            }
        }
        return found;
    }

    private static void add(String kind, String name) {
        EVENTS.add(new Event(kind, name, Thread.currentThread()));
    }
}
