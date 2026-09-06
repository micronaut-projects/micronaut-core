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
package io.micronaut.inject.scope.custom.perbean;

import io.micronaut.context.BeanContext;
import jakarta.annotation.PreDestroy;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The bean {@link PerBeanReferrer} resolves as it is destroyed, which resolves the referrer back as it is destroyed
 * itself, so that whichever the scope destroys first reaches the other.
 */
@PerBeanScope
public class PerBeanReferent {

    public static final AtomicInteger CONSTRUCTIONS = new AtomicInteger();

    private final BeanContext beanContext;
    private PerBeanReferrer resolvedOnDestroy;

    public PerBeanReferent(BeanContext beanContext) {
        this.beanContext = beanContext;
        CONSTRUCTIONS.incrementAndGet();
    }

    @PreDestroy
    void destroy() {
        resolvedOnDestroy = beanContext.getBean(PerBeanReferrer.class);
    }

    public PerBeanReferrer getResolvedOnDestroy() {
        return resolvedOnDestroy;
    }
}
