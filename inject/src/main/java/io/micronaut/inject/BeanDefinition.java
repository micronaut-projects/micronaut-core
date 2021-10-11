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
package io.micronaut.inject;

import io.micronaut.context.annotation.DefaultScope;
import io.micronaut.context.annotation.Provided;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.AnnotationMetadataDelegate;
import io.micronaut.core.naming.Named;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ArgumentCoercible;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Defines a bean definition and its requirements. A bean definition must have a singled injectable constructor or a
 * no-args constructor.
 *
 * @param <T> The bean type
 * @author Graeme Rocher
 * @since 1.0
 */
public interface BeanDefinition<T> extends AnnotationMetadataDelegate, Named, BeanType<T>, ArgumentCoercible<T> {

    /**
     * Attribute used to store a dynamic bean name.
     */
    String NAMED_ATTRIBUTE = Named.class.getName();

    /**
     * @return The scope of the bean
     */
    default Optional<Class<? extends Annotation>> getScope() {
        return Optional.empty();
    }

    /**
     * @return The name of the scope
     */
    default Optional<String> getScopeName() {
        return Optional.empty();
    }

    /**
     * @return Whether the scope is singleton
     */
    default boolean isSingleton() {
        final String scopeName = getScopeName().orElse(null);
        if (scopeName != null && scopeName.equals(AnnotationUtil.SINGLETON)) {
            return true;
        } else {
            return getAnnotationMetadata().stringValue(DefaultScope.class)
                    .map(t -> t.equals(Singleton.class.getName()) || t.equals(AnnotationUtil.SINGLETON))
                    .orElse(false);
        }
    }

    /**
     * If {@link #isContainerType()} returns true this will return the container element.
     * @return The container element.
     */
    default Optional<Argument<?>> getContainerElement() {
        return Optional.empty();
    }

    @Override
    default boolean isCandidateBean(@Nullable Argument<?> beanType) {
        if (beanType == null) {
            return false;
        }
        if (BeanType.super.isCandidateBean(beanType)) {
            final Argument<?>[] typeArguments = beanType.getTypeParameters();
            final int len = typeArguments.length;
            Class<?> beanClass = beanType.getType();
            if (len == 0) {
                if (isContainerType()) {
                    final Optional<Argument<?>> containerElement = getContainerElement();
                    if (containerElement.isPresent()) {
                        final Class<?> t = containerElement.get().getType();
                        return beanType.isAssignableFrom(t) || beanClass == t;
                    } else {
                        return false;
                    }
                } else {
                    return true;
                }
            } else {
                final Argument<?>[] beanTypeParameters;
                if (!Iterable.class.isAssignableFrom(beanClass)) {
                    final Optional<Argument<?>> containerElement = getContainerElement();
                    //noinspection OptionalIsPresent
                    if (containerElement.isPresent()) {
                        beanTypeParameters = containerElement.get().getTypeParameters();
                    } else {
                        beanTypeParameters = getTypeArguments(beanClass).toArray(Argument.ZERO_ARGUMENTS);
                    }
                } else {
                    beanTypeParameters = getTypeArguments(beanClass).toArray(Argument.ZERO_ARGUMENTS);
                }
                if (len != beanTypeParameters.length) {
                    return false;
                }

                for (int i = 0; i < beanTypeParameters.length; i++) {
                    Argument<?> candidateParameter = beanTypeParameters[i];
                    final Argument<?> requestedParameter = typeArguments[i];
                    if (!requestedParameter.isAssignableFrom(candidateParameter.getType())) {
                        if (!(candidateParameter.isTypeVariable() && candidateParameter.isAssignableFrom(requestedParameter.getType()))) {
                            return false;
                        }
                    }
                }
                return true;
            }
        }
        return false;
    }

    /**
     * @return Is this definition provided by another bean
     * @deprecated Provided beans are deprecated
     * @see Provided
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    default boolean isProvided() {
        return getAnnotationMetadata().hasDeclaredStereotype(Provided.class);
    }

    /**
     * @return Whether the bean declared with {@link io.micronaut.context.annotation.EachProperty} or
     * {@link io.micronaut.context.annotation.EachBean}
     */
    default boolean isIterable() {
        return false;
    }

    /**
     * @return The produced bean type
     */
    @Override
    Class<T> getBeanType();

    /**
     * @return The type that declares this definition, null if not applicable.
     */
    default Optional<Class<?>> getDeclaringType() {
        return Optional.empty();
    }

    /**
     * The single concrete constructor that is an injection point for creating the bean.
     *
     * @return The constructor injection point
     */
    default ConstructorInjectionPoint<T> getConstructor() {
        return new ConstructorInjectionPoint<T>() {
            @Override
            public T invoke(Object... args) {
                throw new UnsupportedOperationException("Cannot be instantiated directly");
            }

            @Override
            public Argument<?>[] getArguments() {
                return Argument.ZERO_ARGUMENTS;
            }

            @Override
            public BeanDefinition<T> getDeclaringBean() {
                return BeanDefinition.this;
            }

            @Override
            public boolean requiresReflection() {
                return false;
            }
        };
    }

    /**
     * @return All required components for this entity definition
     */
    default Collection<Class<?>> getRequiredComponents() {
        return Collections.emptyList();
    }

    /**
     * All methods that require injection. This is a subset of all the methods in the class.
     *
     * @return The required properties
     */
    default Collection<MethodInjectionPoint<T, ?>> getInjectedMethods() {
        return Collections.emptyList();
    }

    /**
     * All the fields that require injection.
     *
     * @return The required fields
     */
    default Collection<FieldInjectionPoint<T, ?>> getInjectedFields() {
        return Collections.emptyList();
    }

    /**
     * All the methods that should be called once the bean has been fully initialized and constructed.
     *
     * @return Methods to call post construct
     */
    default Collection<MethodInjectionPoint<T, ?>> getPostConstructMethods() {
        return Collections.emptyList();
    }

    /**
     * All the methods that should be called when the object is to be destroyed.
     *
     * @return Methods to call pre-destroy
     */
    default Collection<MethodInjectionPoint<T, ?>> getPreDestroyMethods() {
        return Collections.emptyList();
    }

    /**
     * @return The class name
     */
    @Override
    @NonNull
    default String getName() {
        return getBeanType().getName();
    }

    /**
     * Finds a single {@link ExecutableMethod} for the given name and argument types.
     *
     * @param name          The method name
     * @param argumentTypes The argument types
     * @param <R>           The return type
     * @return An optional {@link ExecutableMethod}
     */
    default <R> Optional<ExecutableMethod<T, R>> findMethod(String name, Class<?>... argumentTypes) {
        return Optional.empty();
    }

    /**
     * Finds possible methods for the given method name.
     *
     * @param name The method name
     * @param <R>  The return type
     * @return The possible methods
     */
    default <R> Stream<ExecutableMethod<T, R>> findPossibleMethods(String name) {
        return Stream.empty();
    }

    /**
     * Inject the given bean with the context.
     *
     * @param context The context
     * @param bean    The bean
     * @return The injected bean
     */
    default T inject(BeanContext context, T bean) {
        return bean;
    }

    /**
     * Inject the given bean with the context.
     *
     * @param resolutionContext the resolution context
     * @param context           The context
     * @param bean              The bean
     * @return The injected bean
     */
    default T inject(BeanResolutionContext resolutionContext, BeanContext context, T bean) {
        return bean;
    }

    /**
     * @return The {@link ExecutableMethod} instances for this definition
     */
    default Collection<ExecutableMethod<T, ?>> getExecutableMethods() {
        return Collections.emptyList();
    }

    @Override
    @NonNull
    default Argument<T> asArgument() {
        return Argument.of(
                getBeanType(),
                getTypeParameters()
        );
    }

    /**
     * Whether this bean definition represents a proxy.
     *
     * @return True if it represents a proxy
     */
    default boolean isProxy() {
        return this instanceof ProxyBeanDefinition;
    }

    /**
     * If the bean itself declares any type arguments this method will return the classes that represent those types.
     *
     * @return The type arguments
     */
    default @NonNull List<Argument<?>> getTypeArguments() {
        return getTypeArguments(getBeanType());
    }

    /**
     * Return the type arguments for the given interface or super type for this bean.
     *
     * @param type The super class or interface type
     * @return The type arguments
     */
    default @NonNull List<Argument<?>> getTypeArguments(Class<?> type) {
        if (type == null) {
            return Collections.emptyList();
        }
        return getTypeArguments(type.getName());
    }

    /**
     * Returns the type parameters as a class array for the given type.
     * @param type The type
     * @return The type parameters
     */
    default @NonNull Class<?>[] getTypeParameters(@Nullable Class<?> type) {
        if (type == null) {
            return ReflectionUtils.EMPTY_CLASS_ARRAY;
        } else {
            final List<Argument<?>> typeArguments = getTypeArguments(type);
            if (typeArguments.isEmpty()) {
                return ReflectionUtils.EMPTY_CLASS_ARRAY;
            }
            Class[] params = new Class[typeArguments.size()];
            int i = 0;
            for (Argument<?> argument : typeArguments) {
                params[i++] = argument.getType();
            }
            return params;
        }
    }

    /**
     *
     * Returns the type parameters as a class array for the bean type.
     *
     * @return The type parameters for the bean type as a class array.
     */
    default @NonNull Class<?>[] getTypeParameters() {
        return getTypeParameters(getBeanType());
    }

    /**
     * Return the type arguments for the given interface or super type for this bean.
     *
     * @param type The super class or interface type
     * @return The type arguments
     */
    default @NonNull List<Argument<?>> getTypeArguments(String type) {
        return Collections.emptyList();
    }

    /**
     * Finds a single {@link ExecutableMethod} for the given name and argument types.
     *
     * @param name          The method name
     * @param argumentTypes The argument types
     * @param <R>           The return type
     * @return An optional {@link ExecutableMethod}
     * @throws IllegalStateException If the method cannot be found
     */
    @SuppressWarnings("unchecked")
    default <R> ExecutableMethod<T, R> getRequiredMethod(String name, Class<?>... argumentTypes) {
        return (ExecutableMethod<T, R>) findMethod(name, argumentTypes)
            .orElseThrow(() -> ReflectionUtils.newNoSuchMethodError(getBeanType(), name, argumentTypes));
    }

    /**
     * @return Whether the bean definition is abstract
     */
    default boolean isAbstract() {
        return Modifier.isAbstract(getBeanType().getModifiers());
    }

    /**
     * Resolve the declared qualifier for this bean.
     * @return The qualifier or null if this isn't one
     */
    default @Nullable Qualifier<T> getDeclaredQualifier() {
        final List<String> annotations = getAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER);
        if (CollectionUtils.isNotEmpty(annotations)) {
            if (annotations.size() == 1) {
                final String annotation = annotations.iterator().next();
                if (annotation.equals(Qualifier.PRIMARY)) {
                    // primary is the same as null
                    return null;
                }
                return Qualifiers.byAnnotation(this, annotation);
            } else {
                @SuppressWarnings("rawtypes") final Qualifier[] qualifiers = annotations.stream()
                        .map((name) -> Qualifiers.byAnnotation(this, name))
                        .toArray(Qualifier[]::new);
                //noinspection unchecked
                return Qualifiers.byQualifiers(
                        qualifiers
                );
            }
        } else {
            Qualifier<T> qualifier = resolveDynamicQualifier();
            if (qualifier == null) {
                String name = getAnnotationMetadata().stringValue(AnnotationUtil.NAMED).orElse(null);
                qualifier = name != null ? Qualifiers.byAnnotation(this, name) : null;
            }
            return qualifier;
        }
    }

    /**
     * @return Method that can be overridden to resolve a dynamic qualifier
     */
    default @Nullable Qualifier<T> resolveDynamicQualifier() {
        return null;
    }
}
