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

/** Ownership exists before the bean instance, and survives successful creation in its registration. */
@Internal
final class BeanCreationState {
    final BeanDefinition<?> definition;
    final BeanDependencies dependencies = new BeanDependencies();
    final List<BeanRegistration<?>> proxyInterceptors;

    BeanCreationState(BeanDefinition<?> definition, List<BeanRegistration<?>> proxyInterceptors) {
        this.definition = definition;
        this.proxyInterceptors = proxyInterceptors;
    }

    @Nullable List<BeanRegistration<?>> lifecycleInterceptors() {
        List<BeanRegistration<?>> interceptors = dependencies.interceptorRegistrations();
        if (proxyInterceptors.isEmpty()) {
            return interceptors;
        }
        ArrayList<BeanRegistration<?>> selected = new ArrayList<>(proxyInterceptors);
        if (interceptors != null) {
            for (BeanRegistration<?> candidate : interceptors) {
                if (proxyInterceptors.stream().noneMatch(existing -> existing.definition().equals(candidate.definition()))) {
                    selected.add(candidate);
                }
            }
        }
        return List.copyOf(selected);
    }
}
