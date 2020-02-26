/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context;

import io.micronaut.context.condition.ConditionContext;
import io.micronaut.context.condition.Failure;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.Internal;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A Default context implementation.
 *
 * @param <T> The condition context type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class DefaultConditionContext<T extends AnnotationMetadataProvider> implements ConditionContext<T> {

    private final BeanContext beanContext;
    private final T component;
    private final List<Failure> failures = new ArrayList<>(2);

    /**
     * @param beanContext The bean context
     * @param component   The component type
     */
    DefaultConditionContext(BeanContext beanContext, T component) {
        this.beanContext = beanContext;
        this.component = component;
    }

    @Override
    public T getComponent() {
        return component;
    }

    @Override
    public BeanContext getBeanContext() {
        return beanContext;
    }

    @Override
    public ConditionContext<T> fail(@NonNull Failure failure) {
        failures.add(failure);
        return this;
    }

    @Override
    public String toString() {
        return component.toString();
    }

    @Override
    public List<Failure> getFailures() {
        return Collections.unmodifiableList(failures);
    }
}
