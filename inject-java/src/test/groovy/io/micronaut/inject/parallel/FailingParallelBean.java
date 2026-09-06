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

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Parallel;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.concurrent.CountDownLatch;

/**
 * A parallel bean that fails to construct. {@link Parallel#shutdownOnError()} defaults to true, so
 * the failure must stop the context from the parallel worker thread.
 */
@Singleton
@Parallel
@Requires(property = "parallel.failing.bean.enabled")
public class FailingParallelBean {

    public static volatile CountDownLatch CONSTRUCTING = new CountDownLatch(1);

    public FailingParallelBean(BeanContext beanContext) {
        // only fail once startup has completed: a shutdown triggered while the context is still
        // starting is not the path under test
        for (int i = 0; i < 1000 && !beanContext.isRunning(); i++) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        CONSTRUCTING.countDown();
        throw new IllegalStateException("Parallel bean construction failed on purpose");
    }

    /**
     * Restores the latches, so that the spec is isolated even if it runs more than once in the
     * same JVM and a latch has already been counted down.
     */
    public static void reset() {
        CONSTRUCTING = new CountDownLatch(1);
    }
}
