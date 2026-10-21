/*
 * Copyright 2017-2021 original authors
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

import io.micronaut.aop.InterceptorCandidateSelector;
import io.micronaut.aop.InterceptorRegistry;
import io.micronaut.aop.chain.DefaultInterceptorRegistry;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.InstantiatableBeanDefinition;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Registers the {@link InterceptorRegistry} instance.
 *
 * @author graemerocher
 * @since 3.0.0
 */
@Internal
public final class InterceptorRegistryBean implements InstantiatableBeanDefinition<InterceptorRegistry>, BeanDefinitionReference<InterceptorRegistry> {
    public static final AnnotationMetadata ANNOTATION_METADATA;

    /**
     * Set while this thread creates a registry. The singleton lock is reentrant, so a selector that needs the
     * registry would otherwise get a second registry, created without selectors.
     */
    private static final ThreadLocal<Boolean> CREATING_REGISTRY = new ThreadLocal<>();

    static {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addDeclaredAnnotation(BootstrapContextCompatible.class.getName(), Collections.emptyMap());
        ANNOTATION_METADATA = metadata;
    }

    @Override
    public Class<?>[] getIndexes() {
        return new Class[]{InterceptorRegistry.class};
    }

    @Override
    public Set<Class<?>> getExposedTypes() {
        return Set.of(
            InterceptorRegistry.class,
            DefaultInterceptorRegistry.class
        );
    }

    @Override
    public Set<Class<?>> getRequiredComponents() {
        return Set.of(InterceptorCandidateSelector.class);
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public boolean isPrimary() {
        return false;
    }

    @Override
    public boolean isParallel() {
        return false;
    }

    @Override
    public boolean isEnabled(BeanContext context, @Nullable BeanResolutionContext resolutionContext) {
        return true;
    }

    @Override
    public Class<InterceptorRegistry> getBeanType() {
        return InterceptorRegistry.class;
    }

    @Override
    public String getBeanDefinitionName() {
        return InterceptorRegistryBean.class.getName();
    }

    @Override
    public BeanDefinition<InterceptorRegistry> load() {
        return this;
    }

    @Override
    public boolean isPresent() {
        return true;
    }

    @Override
    public boolean isSingleton() {
        return true;
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
    public InterceptorRegistry instantiate(BeanResolutionContext resolutionContext, BeanContext context) throws BeanInstantiationException {
        if (CREATING_REGISTRY.get() != null) {
            throw new BeanInstantiationException(resolutionContext, "The interceptor registry is required while it is being created. "
                + "An InterceptorCandidateSelector bean must not be intercepted and must not depend on an intercepted bean or on the interceptor registry.");
        }
        CREATING_REGISTRY.set(Boolean.TRUE);
        try {
            return new DefaultInterceptorRegistry(context,
                List.copyOf(resolutionContext.getBeansOfType(InterceptorCandidateSelector.class)));
        } finally {
            CREATING_REGISTRY.remove();
        }
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return ANNOTATION_METADATA;
    }
}
