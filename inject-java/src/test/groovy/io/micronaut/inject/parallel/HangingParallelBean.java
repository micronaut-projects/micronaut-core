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
 * A parallel bean whose construction never finishes on its own, so that the wait in {@code stop()}
 * has to give up on it rather than block the shutdown indefinitely.
 */
@Singleton
@Parallel(shutdownOnError = false)
@Requires(property = "parallel.hanging.bean.enabled")
public class HangingParallelBean {

    public static final CountDownLatch CONSTRUCTING = new CountDownLatch(1);
    public static final CountDownLatch RELEASE = new CountDownLatch(1);

    public HangingParallelBean() {
        CONSTRUCTING.countDown();
        try {
            // only the test releases this, well after the shutdown wait has timed out
            RELEASE.await(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
