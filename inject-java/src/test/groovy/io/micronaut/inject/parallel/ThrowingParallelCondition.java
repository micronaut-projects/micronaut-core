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
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.value.PropertyResolver;

import java.util.concurrent.CountDownLatch;

/**
 * A condition that fails while the parallel bean discovery thread is loading the definition, so
 * that discovery itself, rather than a bean constructor, is what fails.
 */
public class ThrowingParallelCondition implements Condition {

    public static final CountDownLatch EVALUATED = new CountDownLatch(1);

    @Override
    public boolean matches(ConditionContext context) {
        BeanContext beanContext = context.getBeanContext();
        // only fail for the context started by this test, other contexts must not be affected
        if (!(beanContext instanceof PropertyResolver propertyResolver)
            || !propertyResolver.getProperty("parallel.throwing.condition.enabled", Boolean.class).orElse(false)) {
            return true;
        }
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
        EVALUATED.countDown();
        throw new IllegalStateException("Parallel bean condition evaluation failed on purpose");
    }
}
