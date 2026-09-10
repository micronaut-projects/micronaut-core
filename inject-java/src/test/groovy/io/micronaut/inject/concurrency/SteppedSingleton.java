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
package io.micronaut.inject.concurrency;

import jakarta.inject.Singleton;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A singleton whose constructions are stepped by the test: the n-th construction reports that it has started,
 * waits to be released, and then either completes or, for the first one when so configured, throws.
 */
@Singleton
public class SteppedSingleton {

    static final int MAX_CONSTRUCTIONS = 5;
    static final AtomicInteger CONSTRUCTIONS = new AtomicInteger();
    static volatile boolean failFirst;
    static volatile CountDownLatch[] started;
    static volatile CountDownLatch[] released;

    public SteppedSingleton() {
        if (started == null) {
            // Not stepped: constructed by another spec, for example through the eager initialization of singletons
            return;
        }
        int n = CONSTRUCTIONS.incrementAndGet();
        started[n].countDown();
        try {
            released[n].await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        if (n == 1 && failFirst) {
            throw new IllegalStateException("The first construction fails");
        }
    }

    static void reset(boolean failFirst) {
        SteppedSingleton.failFirst = failFirst;
        CONSTRUCTIONS.set(0);
        started = new CountDownLatch[MAX_CONSTRUCTIONS + 1];
        released = new CountDownLatch[MAX_CONSTRUCTIONS + 1];
        for (int i = 0; i <= MAX_CONSTRUCTIONS; i++) {
            started[i] = new CountDownLatch(1);
            released[i] = new CountDownLatch(1);
        }
    }
}
