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
package io.micronaut.inject.parallel;

import io.micronaut.context.annotation.Parallel;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A parallel bean whose construction is held open until the context has begun stopping, and which
 * then fails. Its {@code shutdownOnError} shutdown must not re-enter the shutdown that is already
 * under way and waiting for this very initialization.
 */
@Singleton
@Parallel
@Requires(property = "parallel.late.failure.enabled")
public class LateFailingParallelBean {

    public static volatile CountDownLatch CONSTRUCTING = new CountDownLatch(1);
    public static volatile CountDownLatch RELEASE = new CountDownLatch(1);
    public static volatile CountDownLatch FAILED = new CountDownLatch(1);

    public LateFailingParallelBean() {
        CONSTRUCTING.countDown();
        try {
            if (!RELEASE.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test did not release the parallel bean construction");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        FAILED.countDown();
        throw new IllegalStateException("Parallel bean construction failed on purpose after shutdown began");
    }

    /**
     * Restores the latches, so that the spec is isolated even if it runs more than once in the
     * same JVM and a latch has already been counted down.
     */
    public static void reset() {
        CONSTRUCTING = new CountDownLatch(1);
        RELEASE = new CountDownLatch(1);
        FAILED = new CountDownLatch(1);
    }
}
