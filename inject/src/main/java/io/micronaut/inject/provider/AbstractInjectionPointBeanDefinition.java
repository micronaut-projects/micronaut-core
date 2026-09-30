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
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
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
 *     binds.</li>
 *     <li>The definition is its own reference, named after its class, and definitions of one class are equal.</li>
 * </ul>
 *
 * @param <T> The bean type
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public abstract class AbstractInjectionPointBeanDefinition<T> implements InstantiatableBeanDefinition<T>, BeanDefinitionReference<T> {

    private static final Argument<Object> TYPE_VARIABLE = Argument.ofTypeVariable(Object.class, "T");

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
    public final List<Argument<?>> getTypeArguments(Class<?> type) {
        if (type == getBeanType()) {
            return getTypeArguments();
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
}
