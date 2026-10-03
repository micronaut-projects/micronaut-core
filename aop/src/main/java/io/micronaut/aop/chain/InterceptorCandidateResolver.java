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
package io.micronaut.aop.chain;

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.InterceptorRegistry;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.proxy.InterceptedBean;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.qualifiers.Qualifiers;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Acquires interceptor candidates through their dependency owner, independently of invocation construction.
 * An {@link InterceptorRegistry} returns a subclass from {@link InterceptorRegistry#candidateResolver()} to
 * customize acquisition.
 *
 * @since 5.3.0
 */
@Internal
@NullMarked
public class InterceptorCandidateResolver {
    private final InterceptorRegistry registry;
    private final LegacyInterceptorCandidateResolver legacyResolver = new LegacyInterceptorCandidateResolver();

    /**
     * @param registry The selection strategy used after candidate acquisition
     */
    public InterceptorCandidateResolver(InterceptorRegistry registry) {
        this.registry = registry;
    }

    /**
     * Acquires the combined construction and lifecycle candidates described by a bean's constructor metadata.
     * @param resolutionContext The creation context
     * @param constructor The constructor with combined bean and constructor metadata
     * @param <T> The bean type
     * @return The candidates, or null when the bean declares no binding
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> @Nullable List<BeanRegistration<Interceptor<T, T>>> resolveBeanCandidates(
        BeanResolutionContext resolutionContext, AnnotationMetadataProvider constructor) {
        AnnotationMetadata metadata = constructor.getAnnotationMetadata();
        if (metadata.getAnnotationValuesByName(AnnotationUtil.ANN_INTERCEPTOR_BINDING).isEmpty()) {
            return null;
        }
        return new ArrayList((Collection) resolutionContext.getInterceptorRegistrations(
            Interceptor.ARGUMENT, Qualifiers.byInterceptorBinding(metadata)));
    }

    /**
     * Retains all lifecycle candidates before injection and initialization, including an empty result.
     * Destruction-only targets can borrow their owning proxy's candidates; initialization keeps its own set.
     * @param resolutionContext The creation context
     * @param definition The bean definition
     * @param bean The constructed instance
     * @param initialization Whether post-construct interception needs its own candidates
     */
    @UsedByGeneratedCode
    public void captureLifecycleCandidates(BeanResolutionContext resolutionContext, BeanDefinition<?> definition,
                                           @Nullable Object bean, boolean initialization) {
        if (bean == null || resolutionContext.getBeanInterceptors(definition) != null) {
            return;
        }
        if (!initialization) {
            List<?> destructionCandidates = resolutionContext.getBeanDestructionInterceptors(definition);
            if (destructionCandidates != null) {
                resolutionContext.setBeanInterceptors(definition, destructionCandidates);
                return;
            }
        }
        List<?> candidates = bean instanceof InterceptedBean proxy
            ? proxy.$interceptorRegistrations()
            : List.copyOf(resolutionContext.getInterceptorRegistrations(
                Interceptor.ARGUMENT, Qualifiers.byInterceptorBinding(definition.getAnnotationMetadata())));
        resolutionContext.setBeanInterceptors(definition, candidates);
    }

    /**
     * Acquires and retains the complete candidate set of a runtime proxy.
     * @param resolutionContext The creation context
     * @param definition The proxy definition
     * @param methods The intercepted methods
     * @param <T> The bean type
     * @return The acquired registrations
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> List<BeanRegistration<Interceptor<T, ?>>> captureProxyCandidates(
        BeanResolutionContext resolutionContext, BeanDefinition<T> definition,
        Collection<ExecutableMethod<T, ?>> methods) {
        List<AnnotationMetadata> metadata = new ArrayList<>(methods.size() + 2);
        metadata.add(definition.getAnnotationMetadata());
        metadata.add(definition.getConstructor().getAnnotationMetadata());
        metadata.addAll(methods);
        List<BeanRegistration<Interceptor<T, ?>>> candidates = new ArrayList<>((Collection) resolutionContext.getInterceptorRegistrations(
            Interceptor.ARGUMENT,
            Qualifiers.byInterceptorBinding(new AnnotationMetadataHierarchy(metadata.toArray(AnnotationMetadata[]::new)))
        ));
        resolutionContext.setBeanInterceptors(definition, candidates);
        return candidates;
    }

    /**
     * Returns the authoritative retained lifecycle candidates, falling back to legacy discovery only when
     * the caller has no retained selection. An empty retained selection is authoritative.
     * @param resolutionContext The resolution context
     * @param definition The lifecycle owner
     * @param method The lifecycle method
     * @param bean The bean instance
     * @param kind The lifecycle kind
     * @return The candidates
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Collection<BeanRegistration<Interceptor<?, ?>>> resolveLifecycleCandidates(
        BeanResolutionContext resolutionContext, BeanDefinition<?> definition,
        ExecutableMethod<?, ?> method, Object bean, InterceptorKind kind) {
        List<?> retained = resolutionContext.getBeanInterceptors(definition);
        return retained == null
            ? legacyResolver.resolveLifecycleCandidates(resolutionContext, definition, method, bean, kind)
            : (List) retained;
    }
}
