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

import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.value.PropertyResolver;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A condition that blocks the parallel bean discovery thread, simulating condition evaluation that
 * never completes on its own.
 */
public class BlockingParallelCondition implements Condition {

    public static final String DISCOVERY_THREAD_NAME = "micronaut-parallel-bean-discovery";

    public static final CountDownLatch EVALUATING = new CountDownLatch(1);

    @Override
    public boolean matches(ConditionContext context) {
        // only block the discovery thread, never the thread that is starting the context
        if (!DISCOVERY_THREAD_NAME.equals(Thread.currentThread().getName())) {
            return true;
        }
        // and only for the context started by this test, other contexts must not be held up
        if (!(context.getBeanContext() instanceof PropertyResolver propertyResolver)
            || !propertyResolver.getProperty("parallel.blocking.condition.enabled", Boolean.class).orElse(false)) {
            return true;
        }
        EVALUATING.countDown();
        try {
            // nothing ever counts this down: only an interrupt from stop() releases the thread
            new CountDownLatch(1).await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return false;
    }
}
