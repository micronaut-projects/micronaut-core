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
package io.micronaut.context;

import io.micronaut.inject.BeanDefinition;

import java.util.List;

/**
 * What a {@link BeanResolutionTracer} sees of a {@link BeanResolutionContext}, which stays internal.
 *
 * @since 5.3.0
 */
final class TracedResolution implements BeanResolutionTracer.Resolution {

    private final AbstractBeanResolutionContext context;

    /**
     * @param context The traced resolution context
     */
    TracedResolution(AbstractBeanResolutionContext context) {
        this.context = context;
    }

    /**
     * The resolution context of a traced resolution, for the tracers of the trace mode.
     *
     * @param resolution The traced resolution
     * @return Its resolution context
     */
    static BeanResolutionContext resolutionContext(BeanResolutionTracer.Resolution resolution) {
        return ((TracedResolution) resolution).context;
    }

    @Override
    public BeanContext getBeanContext() {
        return context.getContext();
    }

    @Override
    public List<BeanDefinition<?>> getBeansInCreation() {
        return context.getBeansInCreation();
    }

    @Override
    public List<BeanResolutionTracer.Injection> getInjectionPath() {
        return context.getTracedInjections();
    }

    @Override
    public String toString() {
        return "TracedResolution" + getInjectionPath();
    }
}
