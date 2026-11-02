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
package io.micronaut.aop.cachedvalues;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Answers the member of {@link Planned} through a plan kept per method, counting how often the plan is computed.
 */
@Singleton
@Requires(property = "spec.name", value = "CachedValuesTest")
public class PlanInterceptor implements MethodInterceptor<Object, Object> {

    final AtomicInteger computed = new AtomicInteger();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Plan plan = context.getCachedValue(Plan.class, method -> {
            computed.incrementAndGet();
            return new Plan(method.stringValue(Planned.class).orElse(null));
        });
        return plan.value();
    }

    record Plan(String value) {
    }
}
