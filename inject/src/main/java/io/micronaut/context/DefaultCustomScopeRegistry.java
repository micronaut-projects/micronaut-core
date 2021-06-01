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

import io.micronaut.context.scope.CustomScope;
import io.micronaut.context.scope.CustomScopeRegistry;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanType;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of the {@link CustomScopeRegistry} interface.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class DefaultCustomScopeRegistry implements CustomScopeRegistry {

    private final BeanLocator beanLocator;
    private final Map<String, Optional<CustomScope<?>>> scopes = new ConcurrentHashMap<>(2);

    /**
     * @param beanLocator The bean locator
     */
    DefaultCustomScopeRegistry(BeanLocator beanLocator) {
        this.beanLocator = beanLocator;
    }

    @Override
    public Optional<CustomScope<?>> findDeclaredScope(@NonNull Argument<?> argument) {
        return argument.getAnnotationMetadata().getAnnotationNameByStereotype(AnnotationUtil.SCOPE).flatMap(this::findScope);
    }

    @Override
    public Optional<CustomScope<?>> findDeclaredScope(@NonNull BeanType<?> beanType) {
        final List<String> scopeHierarchy = beanType.getAnnotationMetadata().getAnnotationNamesByStereotype(AnnotationUtil.SCOPE);
        Optional<CustomScope<?>> registeredScope = Optional.empty();
        for (String scope : scopeHierarchy) {
            registeredScope = findScope(scope);
            if (registeredScope.isPresent()) {
                break;
            }
        }
        return registeredScope;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Optional<CustomScope<?>> findScope(Class<? extends Annotation> scopeAnnotation) {
        return scopes.computeIfAbsent(scopeAnnotation.getName(), s -> {
            final Qualifier qualifier = Qualifiers.byTypeArguments(scopeAnnotation);
            return beanLocator.findBean(CustomScope.class, qualifier);
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public Optional<CustomScope<?>> findScope(String scopeAnnotation) {
        return scopes.computeIfAbsent(scopeAnnotation, type -> {
            final Qualifier qualifier = Qualifiers.byExactTypeArgumentName(scopeAnnotation);
            return beanLocator.findBean(CustomScope.class, qualifier);
        });
    }

}
