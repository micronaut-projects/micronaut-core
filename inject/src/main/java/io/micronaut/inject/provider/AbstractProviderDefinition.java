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
package io.micronaut.inject.provider;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Any;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.context.exceptions.DisabledBeanException;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.core.annotation.*;
import io.micronaut.core.naming.Named;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ArgumentCoercible;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.BeanFactory;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.qualifiers.AnyQualifier;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Abstract bean definition for other providers to extend from.
 *
 * @param <T> The generic type
 * @since 3.0.0
 * @author graemerocher
 */
public abstract class AbstractProviderDefinition<T> implements BeanDefinition<T>, BeanFactory<T>, BeanDefinitionReference<T> {

    private static final Argument<Object> TYPE_VARIABLE = Argument.ofTypeVariable(Object.class, "T");
    private final AnnotationMetadata annotationMetadata;

    public AbstractProviderDefinition() {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addDeclaredAnnotation(Any.class.getName(), Collections.emptyMap());
        metadata.addDeclaredStereotype(
                Collections.singletonList(Any.class.getName()),
                AnnotationUtil.QUALIFIER,
                Collections.emptyMap()
        );
        metadata.addDeclaredAnnotation(BootstrapContextCompatible.class.getName(), Collections.emptyMap());
        try {
            metadata.addDeclaredAnnotation(Indexes.class.getName(), Collections.singletonMap(AnnotationMetadata.VALUE_MEMBER, getBeanType()));
        } catch (NoClassDefFoundError e) {
            // ignore, might happen if javax.inject is not the classpath
        }
        annotationMetadata = metadata;
    }

    @Override
    public boolean isContainerType() {
        return false;
    }

    @Override
    public boolean isEnabled(@NonNull BeanContext context, @Nullable BeanResolutionContext resolutionContext) {
        return isPresent();
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
    public boolean isPresent() {
        return false;
    }

    /**
     * Builds a provider implementation.
     *
     * @param resolutionContext The resolution context
     * @param context The context
     * @param argument The argument
     * @param qualifier The qualifier
     * @param singleton Whether the bean is a singleton
     * @return The provider
     */
    protected abstract @NonNull T buildProvider(
            @NonNull BeanResolutionContext resolutionContext,
            @NonNull BeanContext context,
            @NonNull Argument<Object> argument,
            @Nullable Qualifier<Object> qualifier,
            boolean singleton);

    @Override
    public T build(
            BeanResolutionContext resolutionContext,
            BeanContext context,
            BeanDefinition<T> definition) throws BeanInstantiationException {
        final BeanResolutionContext.Segment<?> segment = resolutionContext.getPath().currentSegment().orElse(null);
        if (segment != null) {
            final InjectionPoint<?> injectionPoint = segment.getInjectionPoint();
            if (injectionPoint instanceof ArgumentCoercible) {
                Argument<?> injectionPointArgument = ((ArgumentCoercible<?>) injectionPoint)
                        .asArgument();

                Argument<?> resolveArgument = injectionPointArgument;
                if (resolveArgument.isOptional()) {
                    resolveArgument = resolveArgument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
                }
                @SuppressWarnings("unchecked") Argument<Object> argument =
                        (Argument<Object>) resolveArgument
                                .getFirstTypeVariable()
                                .orElse(null);
                if (argument != null) {
                    Qualifier<Object> qualifier = (Qualifier<Object>) resolutionContext.getCurrentQualifier();
                    if (qualifier == null && segment.getDeclaringType().isIterable()) {
                        final Object n = resolutionContext.getAttribute(Named.class.getName());
                        if (n != null) {
                            qualifier = Qualifiers.byName(n.toString());
                        }
                    }

                    boolean hasBean = context.containsBean(argument, qualifier);
                    if (hasBean) {
                        return buildProvider(
                                resolutionContext,
                                context,
                                argument,
                                qualifier,
                                definition.isSingleton()
                        );
                    } else {
                        if (injectionPointArgument.isOptional()) {
                            return (T) Optional.empty();
                        } else if (injectionPointArgument.isNullable()) {
                            throw new DisabledBeanException("Nullable bean doesn't exist");
                        } else {
                            if (qualifier instanceof AnyQualifier || isAllowEmptyProviders(context)) {
                                return buildProvider(
                                        resolutionContext,
                                        context,
                                        argument,
                                        qualifier,
                                        definition.isSingleton()
                                );
                            } else {
                                throw new NoSuchBeanException(argument, qualifier);
                            }
                        }
                    }

                }
            }
        }
        throw new UnsupportedOperationException("Cannot inject provider for Object type");
    }

    /**
     * Return whether missing providers are allowed for this implementation. If {@code false} a {@link io.micronaut.context.exceptions.NoSuchBeanException} is thrown.
     * @param context The context
     * @return Returns {@code true} if missing providers are allowed
     */
    protected boolean isAllowEmptyProviders(BeanContext context) {
        return context.getContextConfiguration().isAllowEmptyProviders();
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
    @NonNull
    public final List<Argument<?>> getTypeArguments(Class<?> type) {
        if (type == getBeanType()) {
            return getTypeArguments();
        }
        return Collections.emptyList();
    }

    @Override
    @NonNull
    public final List<Argument<?>> getTypeArguments() {
        return Collections.singletonList(TYPE_VARIABLE);
    }    

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    public Qualifier<T> getDeclaredQualifier() {
        return null;
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
