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
package io.micronaut.aop.internal;

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InterceptorTarget;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.InstantiatableBeanDefinition;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Resolves {@link InterceptorTarget} from the active interceptor resolution path.
 *
 * <p>This manually implemented prototype bean definition is registered as built-in AOP infrastructure. When an
 * interceptor constructor requests {@link InterceptorTarget}, it reads the root definition from the current
 * {@link BeanResolutionContext}. The root represents the intercepted bean being built, while the current path segment
 * identifies the interceptor whose dependency is being resolved. The resulting descriptor captures only the target
 * {@link BeanDefinition}; it does not retain or expose a target instance.</p>
 *
 * <p>The path is validated before producing a descriptor: the current declaring bean must implement
 * {@link Interceptor}, and it must be different from the root target definition. Consequently, an application cannot
 * inject {@code InterceptorTarget} into an arbitrary bean or resolve it directly from the bean context. Invalid use
 * fails immediately with {@link BeanInstantiationException} instead of returning ambiguous target metadata.</p>
 *
 * <p>The definition is also added explicitly to the Java, Groovy, Kotlin, and Python in-memory compiler bean sets so
 * compile-time framework tests and embedded compiler environments observe the same built-in behavior as a packaged
 * application.</p>
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Internal
public final class InterceptorTargetBean implements InstantiatableBeanDefinition<InterceptorTarget>, BeanDefinitionReference<InterceptorTarget> {

    @Override
    public Class<?>[] getIndexes() {
        return new Class[]{InterceptorTarget.class};
    }

    @Override
    public Set<Class<?>> getExposedTypes() {
        return Set.of(InterceptorTarget.class);
    }

    @Override
    public boolean isEnabled(BeanContext context, @Nullable BeanResolutionContext resolutionContext) {
        return true;
    }

    @Override
    public Class<InterceptorTarget> getBeanType() {
        return InterceptorTarget.class;
    }

    @Override
    public String getBeanDefinitionName() {
        return InterceptorTargetBean.class.getName();
    }

    @Override
    public BeanDefinition<InterceptorTarget> load() {
        return this;
    }

    @Override
    public boolean isPresent() {
        return true;
    }

    @Override
    public boolean isSingleton() {
        return false;
    }

    @Override
    public boolean isConfigurationProperties() {
        return false;
    }

    @Override
    public boolean isAbstract() {
        return false;
    }

    @Override
    public InterceptorTarget instantiate(BeanResolutionContext resolutionContext,
                                         BeanContext context) throws BeanInstantiationException {
        BeanDefinition<?> targetDefinition = resolutionContext.getRootDefinition();
        BeanResolutionContext.Segment<?, ?> segment = resolutionContext.getPath().currentSegment().orElse(null);
        BeanDefinition<?> interceptorDefinition = segment == null ? null : segment.getDeclaringType();
        if (targetDefinition == null
            || interceptorDefinition == null
            || targetDefinition == interceptorDefinition
            || !Interceptor.class.isAssignableFrom(interceptorDefinition.getBeanType())) {
            throw new BeanInstantiationException(this,
                "InterceptorTarget can only be injected while an interceptor is created for an intercepted bean");
        }
        return () -> targetDefinition;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return AnnotationMetadata.EMPTY_METADATA;
    }
}
