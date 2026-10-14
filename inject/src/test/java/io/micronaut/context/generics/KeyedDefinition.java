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
package io.micronaut.context.generics;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * A hand-written definition that records type argument keys and exposes one of them, for the interface default of
 * {@link BeanDefinition#getTypeClosure()}.
 */
public final class KeyedDefinition implements BeanDefinition<IntRepo> {

    @Override
    public Class<IntRepo> getBeanType() {
        return IntRepo.class;
    }

    @Override
    public String getName() {
        return IntRepo.class.getName();
    }

    @Override
    public boolean isEnabled(BeanContext context, @Nullable BeanResolutionContext resolutionContext) {
        return true;
    }

    @Override
    public Set<Class<?>> getExposedTypes() {
        return Set.of(Repo.class);
    }

    @Override
    public Collection<String> getTypeArgumentKeys() {
        return List.of(IntRepo.class.getName(), NumberRepo.class.getName(), Repo.class.getName());
    }

    @Override
    public List<Argument<?>> getTypeArguments(String type) {
        return IntRepo.class.getName().equals(type) ? List.of() : List.of(Argument.of(Integer.class));
    }
}
