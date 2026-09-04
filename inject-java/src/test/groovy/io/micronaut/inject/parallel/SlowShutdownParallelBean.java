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
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A parallel bean whose construction can be held open by a test so that the context is stopped
 * while the bean is still being created.
 */
@Singleton
@Parallel
@Requires(property = "parallel.shutdown.bean.enabled")
public class SlowShutdownParallelBean {

    public static final CountDownLatch CONSTRUCTING = new CountDownLatch(1);
    public static final CountDownLatch RELEASE = new CountDownLatch(1);
    public static final CountDownLatch DESTROYED = new CountDownLatch(1);

    public SlowShutdownParallelBean() {
        CONSTRUCTING.countDown();
        try {
            if (!RELEASE.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test did not release the parallel bean construction");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    void close() {
        DESTROYED.countDown();
    }
}
