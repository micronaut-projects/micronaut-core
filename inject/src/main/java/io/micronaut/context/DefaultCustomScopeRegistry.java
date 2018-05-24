/*
 * Copyright 2017-2018 original authors
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

import io.micronaut.context.scope.CustomScope;
import io.micronaut.context.scope.CustomScopeRegistry;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of the {@link CustomScopeRegistry} interface.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class DefaultCustomScopeRegistry implements CustomScopeRegistry {

    private final BeanLocator beanLocator;
    private final Map<Class, Optional<CustomScope>> scopes = new ConcurrentHashMap<>(1);

    /**
     * @param beanLocator The bean locator
     */
    DefaultCustomScopeRegistry(BeanLocator beanLocator) {
        this.beanLocator = beanLocator;
    }

    @Override
    public Optional<CustomScope> findScope(Class<? extends Annotation> scopeAnnotation) {
        return scopes.computeIfAbsent(scopeAnnotation, type -> beanLocator.findBean(CustomScope.class, Qualifiers.byTypeArguments(type)));
    }
}
