/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.context.condition.ConditionContext2;
import io.micronaut.context.condition.Failure;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertyResolverDelegate;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.value.PropertyResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Internal
class DefaultConditionContext2<B extends AnnotationMetadataProvider> implements PropertyResolverDelegate, ConditionContext2<B> {

    private final Environment environment;
    private final B component;
    private final List<Failure> failures = new ArrayList<>(2);

    DefaultConditionContext2(Environment environment, B component) {
        this.environment = environment;
        this.component = component;
    }

    @Override
    public B getComponent() {
        return component;
    }

    @Override
    public PropertyResolver getPropertyResolverDelegate() {
        return environment;
    }

    @Override
    public Environment getEnvironment() {
        return environment;
    }

    @Override
    public ConditionContext2<B> fail(@NonNull Failure failure) {
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
