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
import org.jspecify.annotations.Nullable;

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
    /**
     * The places being handed over on this thread. A call that is handed a place and leaves at once hands it on from
     * inside the hand-over: that one is queued here and made by the first, so that a long line of such calls does not
     * nest one hand-over in another for each of them.
     */
    private static final ThreadLocal<@Nullable ArrayDeque<HandOver>> HANDING_OVER = new ThreadLocal<>();

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
        CompletableFuture<Boolean> next = next();
        if (next == null) {
            return;
        }
        ArrayDeque<HandOver> handingOver = HANDING_OVER.get();
        if (handingOver != null) {
            handingOver.add(new HandOver(this, next));
            return;
        }
        handingOver = new ArrayDeque<>();
        HANDING_OVER.set(handingOver);
        try {
            BulkheadPermits permits = this;
            while (true) {
                // a call that gave up meanwhile leaves the place to the one behind it
                while (next != null && !next.complete(Boolean.TRUE)) {
                    next = permits.next();
                }
                HandOver queued = handingOver.poll();
                if (queued == null) {
                    return;
                }
                permits = queued.permits();
                next = queued.place();
            }
        } finally {
            HANDING_OVER.remove();
        }
    }

    /**
     * @return The call that waited longest, which the place goes to, or {@code null} where none waits and the place is free
     */
    private @Nullable CompletableFuture<Boolean> next() {
        lock.lock();
        try {
            CompletableFuture<Boolean> next = waiting.poll();
            if (next == null) {
                active--;
            }
            return next;
        } finally {
            lock.unlock();
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

    /**
     * @param permits The bulkhead
     * @param place The call its place goes to
     */
    private record HandOver(BulkheadPermits permits, CompletableFuture<Boolean> place) {
    }
}
