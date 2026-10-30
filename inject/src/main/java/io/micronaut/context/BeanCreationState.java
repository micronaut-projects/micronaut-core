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

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The ownership of a bean while it is being created.
 *
 * <p>A bean can resolve dependencies and select interceptors before its instance exists, so the owner is created
 * first and kept on the resolution context for the duration of the creation. On success the same
 * {@link DefaultBeanDependencies} becomes the owner held by the registration of the bean; on failure it releases what
 * was created. Each creation has its own state, so a nested creation does not attach to its parent.</p>
 */
@Internal
final class BeanCreationState {
    final BeanDefinition<?> definition;
    final DefaultBeanDependencies dependencies = new DefaultBeanDependencies();
    final List<BeanRegistration<?>> proxyInterceptors;
    /** The creation this one is nested in on the same resolution context, which resumes when this one ends. */
    final @Nullable BeanCreationState parent;

    BeanCreationState(BeanDefinition<?> definition, List<BeanRegistration<?>> proxyInterceptors) {
        this(definition, proxyInterceptors, null);
    }

    BeanCreationState(BeanDefinition<?> definition, List<BeanRegistration<?>> proxyInterceptors,
                      @Nullable BeanCreationState parent) {
        this.definition = definition;
        this.proxyInterceptors = proxyInterceptors;
        this.parent = parent;
    }

    /**
     * Whether this is the creation of the bean of the given definition. A qualified {@link BeanDefinitionDelegate},
     * such as the one of an {@link io.micronaut.context.annotation.EachBean} bean, is created as itself, while the
     * definition it wraps is the one instantiated and the one its injection segments name, so that definition
     * matches too. This state, the one of the qualified bean, remains the owner.
     *
     * @param candidate The definition
     * @return Whether this state creates the bean of the definition
     */
    boolean isCreating(BeanDefinition<?> candidate) {
        return definition.equals(candidate)
            || (definition instanceof BeanDefinitionDelegate<?> delegate && delegate.getDelegate().equals(candidate));
    }

    InterceptorCandidates lifecycleInterceptorCandidates() {
        InterceptorCandidates candidates = dependencies.interceptorCandidates();
        if (proxyInterceptors.isEmpty()) {
            return candidates;
        }
        ArrayList<BeanRegistration<?>> selected = new ArrayList<>(proxyInterceptors);
        if (candidates instanceof InterceptorCandidates.Resolved resolved) {
            for (BeanRegistration<?> candidate : resolved.registrations()) {
                if (proxyInterceptors.stream().noneMatch(existing -> existing.definition().equals(candidate.definition()))) {
                    selected.add(candidate);
                }
            }
        }
        return new InterceptorCandidates.Resolved(selected);
    }
}
