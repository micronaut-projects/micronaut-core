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
import io.micronaut.aop.chain.CachedProxyTargetHandler;
import io.micronaut.aop.chain.FixedProxyTargetHandler;
import io.micronaut.aop.chain.HotSwapProxyTargetHandler;
import io.micronaut.aop.chain.InterceptorChainFactory;
import io.micronaut.aop.chain.LazyProxyTargetHandler;
import io.micronaut.aop.chain.ProxyTargetHandler;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.InstantiatableBeanDefinition;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.qualifiers.InterceptorBindingQualifier;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Registers one kind of {@link ProxyTargetHandler} as an unscoped bean, so that every proxy injected with it gets
 * its own, as a dependent destroyed with the proxy. The handler is created from its injection point, the
 * constructor of the proxy: the interceptor binding is on the parameter and the qualifier is that of the proxy.
 *
 * @param <H> The handler type
 * @since 5.3.0
 */
@Internal
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract sealed class ProxyTargetHandlerBean<H extends ProxyTargetHandler> implements InstantiatableBeanDefinition<H>, BeanDefinitionReference<H> {
    private static final AnnotationMetadata ANNOTATION_METADATA;

    static {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addDeclaredAnnotation(BootstrapContextCompatible.class.getName(), Collections.emptyMap());
        ANNOTATION_METADATA = metadata;
    }

    private final Class<H> type;
    /**
     * The binding of each proxy constructor this handler is injected into, which is the same for every proxy it
     * creates. Keyed by the identity of the parameter: arguments are equal by type and name, which every handler
     * parameter of a kind shares.
     */
    private final Map<Argument<?>, Optional<Qualifier<Interceptor<?, ?>>>> bindings = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Function<ProxyTargetHandler.Creation, H> constructor;

    private ProxyTargetHandlerBean(Class<H> type, Function<ProxyTargetHandler.Creation, H> constructor) {
        this.type = type;
        this.constructor = constructor;
    }

    @Override
    public H instantiate(BeanResolutionContext resolutionContext, BeanContext context) throws BeanInstantiationException {
        Qualifier<Interceptor<?, ?>> binding = null;
        Qualifier<?> qualifier = null;
        // the injection point: the handler parameter of the constructor of the proxy
        BeanResolutionContext.Segment<?, ?> injectionPoint = resolutionContext.getPath().currentSegment().orElse(null);
        if (injectionPoint != null) {
            binding = bindings.computeIfAbsent(injectionPoint.getArgument(), ProxyTargetHandlerBean::bindingOf).orElse(null);
            qualifier = injectionPoint.getDeclaringTypeQualifier();
        }
        if (qualifier == null || qualifier instanceof InterceptorBindingQualifier) {
            qualifier = resolutionContext.getConfigurationPath().beanQualifier();
        }
        return constructor.apply(new ProxyTargetHandler.Creation(
            resolutionContext.getBean(InterceptorChainFactory.ARGUMENT), binding, resolutionContext, context, qualifier));
    }

    private static Optional<Qualifier<Interceptor<?, ?>>> bindingOf(Argument<?> handlerParameter) {
        AnnotationValue<Annotation> bound = handlerParameter.getAnnotationMetadata()
            .findAnnotation(ProxyTargetHandler.BINDING).orElse(null);
        if (bound == null || bound.getAnnotations(AnnotationMetadata.VALUE_MEMBER).isEmpty()) {
            // a proxy that takes its interceptors from its targets binds none
            return Optional.empty();
        }
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addDeclaredAnnotation(AnnotationUtil.ANN_INTERCEPTOR_BINDING_QUALIFIER, bound.getValues());
        return Optional.of(Qualifiers.byInterceptorBinding(metadata));
    }

    @Override
    public Class<?>[] getIndexes() {
        return new Class[]{type};
    }

    @Override
    public Set<Class<?>> getExposedTypes() {
        return Set.of(type);
    }

    @Override
    public Set<Class<?>> getRequiredComponents() {
        return Set.of(InterceptorChainFactory.class);
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
    public Class<H> getBeanType() {
        return type;
    }

    @Override
    public String getBeanDefinitionName() {
        return getClass().getName();
    }

    @Override
    public BeanDefinition<H> load() {
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
    public AnnotationMetadata getAnnotationMetadata() {
        return ANNOTATION_METADATA;
    }

    /** Registers {@link FixedProxyTargetHandler}. */
    public static final class Fixed extends ProxyTargetHandlerBean<FixedProxyTargetHandler> {
        public Fixed() {
            super(FixedProxyTargetHandler.class, FixedProxyTargetHandler::new);
        }
    }

    /** Registers {@link LazyProxyTargetHandler}. */
    public static final class Lazy extends ProxyTargetHandlerBean<LazyProxyTargetHandler> {
        public Lazy() {
            super(LazyProxyTargetHandler.class, LazyProxyTargetHandler::new);
        }
    }

    /** Registers {@link CachedProxyTargetHandler}. */
    public static final class Cached extends ProxyTargetHandlerBean<CachedProxyTargetHandler> {
        public Cached() {
            super(CachedProxyTargetHandler.class, CachedProxyTargetHandler::new);
        }
    }

    /** Registers {@link HotSwapProxyTargetHandler}. */
    public static final class HotSwap extends ProxyTargetHandlerBean<HotSwapProxyTargetHandler> {
        public HotSwap() {
            super(HotSwapProxyTargetHandler.class, HotSwapProxyTargetHandler::new);
        }
    }
}
