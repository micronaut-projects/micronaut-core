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
package io.micronaut.retry.intercept;

import io.micronaut.core.annotation.Internal;
import io.micronaut.retry.BulkheadSnapshot;

import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The places of a bulkhead. A call asks for a place and is given a future: {@code true} when the place
 * is its own, at once or when another call left, and {@code false} when the call gave up waiting, which
 * is the caller's to say by completing the future. Whoever completes the future first is right, so a
 * place is handed to a call or the call gives up, and never both.
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Internal
final class BulkheadPermits {
    private final String name;
    private final int maxConcurrent;
    private final String user;
    private final ReentrantLock lock = new ReentrantLock();
    private final ArrayDeque<CompletableFuture<Boolean>> waiting = new ArrayDeque<>();
    private int active;

    /**
     * @param name The name of the bulkhead, or what describes it where it has none
     * @param maxConcurrent How many calls hold a place at the same time
     * @param user The method that created it
     */
    BulkheadPermits(String name, int maxConcurrent, String user) {
        this.name = name;
        this.maxConcurrent = maxConcurrent;
        this.user = user;
    }

    String name() {
        return name;
    }

    int maxConcurrent() {
        return maxConcurrent;
    }

    String user() {
        return user;
    }

    /**
     * @return A future that is {@code true} already where there was a place, and waits for one otherwise
     */
    CompletableFuture<Boolean> enter() {
        lock.lock();
        try {
            if (active < maxConcurrent) {
                active++;
                return CompletableFuture.completedFuture(Boolean.TRUE);
            }
            CompletableFuture<Boolean> place = new CompletableFuture<>();
            waiting.add(place);
            return place;
        } finally {
            lock.unlock();
        }
    }

    /**
     * A call that waited gives up: it is refused unless a place was handed to it meanwhile.
     *
     * @param place The future of the call
     * @return Whether the call is refused; {@code false} where the place is its own after all
     */
    boolean giveUp(CompletableFuture<Boolean> place) {
        if (place.complete(Boolean.FALSE)) {
            lock.lock();
            try {
                waiting.remove(place);
            } finally {
                lock.unlock();
            }
            return true;
        }
        return !place.getNow(Boolean.FALSE);
    }

    /**
     * A call leaves, and its place goes to the call that waited longest and has not given up.
     */
    void leave() {
        while (true) {
            CompletableFuture<Boolean> next;
            lock.lock();
            try {
                next = waiting.poll();
                if (next == null) {
                    active--;
                    return;
                }
            } finally {
                lock.unlock();
            }
            if (next.complete(Boolean.TRUE)) {
                return;
            }
        }
    }

    BulkheadSnapshot snapshot() {
        lock.lock();
        try {
            return new BulkheadSnapshot(name, maxConcurrent, active, waiting.size());
        } finally {
            lock.unlock();
        }
    }
}
