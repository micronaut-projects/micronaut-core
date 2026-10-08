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
package io.micronaut.inject.provider;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.exceptions.BeanDestructionException;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.DisposableBeanDefinition;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.InstantiatableBeanDefinition;
import io.micronaut.inject.qualifiers.AnyQualifier;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * A bean definition whose bean is built for the injection point it is injected into, rather than created from a
 * class of its own.
 *
 * <p>Such a bean is whatever the injection point asks for, such as a {@link jakarta.inject.Provider} of the type
 * the injection point names. There is no one {@code Provider<T>} class to generate a definition from, so the
 * definition is written by hand, as its own {@link BeanDefinitionReference}, and registered as a service of that
 * type. This class holds what such definitions share:</p>
 *
 * <ul>
 *     <li>The bean is built by {@link #build(BeanResolutionContext, BeanContext, InjectionPoint)}, for the
 *     injection point being resolved, every time it is injected. The definition is neither a singleton nor
 *     abstract.</li>
 *     <li>The definition's declared qualifier is {@link AnyQualifier}: it is a candidate for an injection point of
 *     any qualifier, and the qualifier is for the bean to read.</li>
 *     <li>The bean type has one type variable, {@code T}, which the type argument of the injection point
 *     binds. Each of the {@link #getExposedTypes() exposed types} is bound the same way.</li>
 *     <li>The definition is its own reference, named after its class, and definitions of one class are equal.</li>
 *     <li>The annotation metadata is the one given to the constructor, empty by default.</li>
 * </ul>
 *
 * <p>The bean is not disposed of: a definition whose bean has to be let go of when the bean it is injected into
 * is destroyed extends {@link Disposable} instead.</p>
 *
 * @param <T> The bean type
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public abstract class AbstractInjectionPointBeanDefinition<T> implements InstantiatableBeanDefinition<T>, BeanDefinitionReference<T> {

    private static final Argument<Object> TYPE_VARIABLE = Argument.ofTypeVariable(Object.class, "T");

    private final AnnotationMetadata annotationMetadata;

    /**
     * A definition with no annotation metadata.
     */
    protected AbstractInjectionPointBeanDefinition() {
        this(AnnotationMetadata.EMPTY_METADATA);
    }

    /**
     * A definition with the given annotation metadata, such as one declaring {@link AnnotationUtil#NULLABLE} for
     * a bean that may be built as {@code null}.
     *
     * @param annotationMetadata The annotation metadata of the definition
     */
    protected AbstractInjectionPointBeanDefinition(AnnotationMetadata annotationMetadata) {
        this.annotationMetadata = annotationMetadata;
    }

    /**
     * Builds the bean for the injection point it is injected into.
     *
     * <p>The injection point is the one of the segment the resolution is at: a field, or a constructor or method
     * argument, of the bean the definition is injected into. For a bean looked up from the context rather than
     * injected, it is the lookup itself, whose argument is the type that was looked up. It is {@code null} when
     * the definition is instantiated outside of any resolution.</p>
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param injectionPoint    The injection point, or {@code null}
     * @return The bean
     */
    protected abstract T build(BeanResolutionContext resolutionContext,
                               BeanContext context,
                               @Nullable InjectionPoint<?> injectionPoint);

    @Override
    public T instantiate(BeanResolutionContext resolutionContext, BeanContext context) throws BeanInstantiationException {
        BeanResolutionContext.Segment<?, ?> segment = resolutionContext.getPath().currentSegment().orElse(null);
        return build(resolutionContext, context, segment != null ? segment.getInjectionPoint() : null);
    }

    @Override
    public boolean isEnabled(BeanContext context, @Nullable BeanResolutionContext resolutionContext) {
        return isPresent();
    }

    @Override
    public boolean isPresent() {
        return true;
    }

    @Override
    public String getBeanDefinitionName() {
        return getClass().getName();
    }

    @Override
    public BeanDefinition<T> load() {
        return this;
    }

    @Override
    public final boolean isAbstract() {
        return false;
    }

    @Override
    public final boolean isSingleton() {
        return false;
    }

    @Override
    public boolean isContainerType() {
        return false;
    }

    @Override
    public boolean isConfigurationProperties() {
        return false;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    /**
     * The type arguments of the bean type, or of one of the types it is exposed as.
     *
     * <p>Every one of the {@link #getExposedTypes() exposed types} is given the type arguments of the bean type,
     * the way a {@code Provider<T>} implemented by a {@code BeanProvider<T>} is. The type parameters of an exposed
     * type are not read reflectively, so a definition exposed as a type that is parameterized otherwise, or not at
     * all, overrides this for that type.</p>
     *
     * @param type The bean type or an exposed type
     * @return The type arguments, or an empty list for any other type
     */
    @Override
    public List<Argument<?>> getTypeArguments(Class<?> type) {
        if (type == getBeanType() || getExposedTypes().contains(type)) {
            return getTypeArguments();
        }
        return Collections.emptyList();
    }

    @Override
    public List<Argument<?>> getTypeArguments(@Nullable String type) {
        if (type == null) {
            return Collections.emptyList();
        }
        Class<T> beanType = getBeanType();
        if (beanType.getName().equals(type)) {
            return getTypeArguments(beanType);
        }
        for (Class<?> exposedType : getExposedTypes()) {
            if (exposedType.getName().equals(type)) {
                return getTypeArguments(exposedType);
            }
        }
        return Collections.emptyList();
    }

    @Override
    public List<Argument<?>> getTypeArguments() {
        return Collections.singletonList(TYPE_VARIABLE);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Qualifier<T> getDeclaredQualifier() {
        return AnyQualifier.INSTANCE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /**
     * A definition whose bean is disposed of with the bean it is injected into.
     *
     * <p>Only this subclass is a {@link DisposableBeanDefinition}: the context disposes of a bean only when its
     * definition is one, so a definition whose bean needs no disposing, such as a provider, keeps the behaviour it
     * had.</p>
     *
     * @param <T> The bean type
     * @since 5.3.0
     */
    @Experimental
    public abstract static class Disposable<T> extends AbstractInjectionPointBeanDefinition<T> implements DisposableBeanDefinition<T> {

        /**
         * A definition with no annotation metadata.
         */
        protected Disposable() {
        }

        /**
         * A definition with the given annotation metadata.
         *
         * @param annotationMetadata The annotation metadata of the definition
         */
        protected Disposable(AnnotationMetadata annotationMetadata) {
            super(annotationMetadata);
        }

        /**
         * Disposes of a bean this definition built, when the bean it was injected into is destroyed.
         *
         * <p>By default an {@link AutoCloseable} bean is closed.</p>
         *
         * @param context The bean context
         * @param bean    The bean
         */
        protected void destroy(BeanContext context, T bean) {
            if (bean instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    throw new BeanDestructionException(this, e);
                }
            }
        }

        @Override
        public final T dispose(BeanContext context, T bean) {
            destroy(context, bean);
            return bean;
        }

        @Override
        public final T dispose(BeanResolutionContext resolutionContext, BeanContext context, T bean) {
            destroy(context, bean);
            return bean;
        }
    }
}
