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

/**
 * A bean of the per-bean-locked scope whose destruction resolves another bean of the scope.
 */
@PerBeanScope
public class PerBeanReferrer {

    private final BeanContext beanContext;
    private PerBeanReferent resolvedOnDestroy;

    public PerBeanReferrer(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @PreDestroy
    void destroy() {
        resolvedOnDestroy = beanContext.getBean(PerBeanReferent.class);
    }

    public PerBeanReferent getResolvedOnDestroy() {
        return resolvedOnDestroy;
    }
}
