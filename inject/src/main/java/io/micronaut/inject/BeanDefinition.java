/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.inject;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.AnnotationMetadataDelegate;
import io.micronaut.core.naming.Named;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
public interface BeanDefinition<T> extends AnnotationMetadataDelegate, Named, BeanType<T> {

    /**
     * @return The scope of the bean
     */
    Optional<Class<? extends Annotation>> getScope();

    /**
     * @return Whether the scope is singleton
     */
    boolean isSingleton();

    /**
     * @return Is this definition provided by another bean
     */
    boolean isProvided();

    /**
     * @return Whether the bean declared with {@link io.micronaut.context.annotation.EachProperty} or
     * {@link io.micronaut.context.annotation.EachBean}
     */
    boolean isIterable();

    /**
     * @return The produced bean type
     */
    @Override
    Class<T> getBeanType();

    /**
     * @return The type that declares this definition, null if not applicable.
     */
    Optional<Class<?>> getDeclaringType();

    /**
     * The single concrete constructor that is an injection point for creating the bean.
     *
     * @return The constructor injection point
     */
    ConstructorInjectionPoint<T> getConstructor();

    /**
     * @return All required components for this entity definition
     */
    Collection<Class> getRequiredComponents();

    /**
     * All methods that require injection. This is a subset of all the methods in the class.
     *
     * @return The required properties
     */
    Collection<MethodInjectionPoint> getInjectedMethods();

    /**
     * All the fields that require injection.
     *
     * @return The required fields
     */
    Collection<FieldInjectionPoint> getInjectedFields();

    /**
     * All the methods that should be called once the bean has been fully initialized and constructed.
     *
     * @return Methods to call post construct
     */
    Collection<MethodInjectionPoint> getPostConstructMethods();

    /**
     * All the methods that should be called when the object is to be destroyed.
     *
     * @return Methods to call pre-destroy
     */
    Collection<MethodInjectionPoint> getPreDestroyMethods();

    /**
     * @return The class name
     */
    String getName();

    /**
     * Finds a single {@link ExecutableMethod} for the given name and argument types.
     *
     * @param name          The method name
     * @param argumentTypes The argument types
     * @param <R>           The return type
     * @return An optional {@link ExecutableMethod}
     */
    <R> Optional<ExecutableMethod<T, R>> findMethod(String name, Class... argumentTypes);

    /**
     * Finds possible methods for the given method name.
     *
     * @param name The method name
     * @param <R>  The return type
     * @return The possible methods
     */
    <R> Stream<ExecutableMethod<T, R>> findPossibleMethods(String name);

    /**
     * Inject the given bean with the context.
     *
     * @param context The context
     * @param bean    The bean
     * @return The injected bean
     */
    T inject(BeanContext context, T bean);

    /**
     * Inject the given bean with the context.
     *
     * @param resolutionContext the resolution context
     * @param context           The context
     * @param bean              The bean
     * @return The injected bean
     */
    T inject(BeanResolutionContext resolutionContext, BeanContext context, T bean);

    /**
     * @return The {@link ExecutableMethod} instances for this definition
     */
    Collection<ExecutableMethod<T, ?>> getExecutableMethods();

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
    default @Nonnull List<Argument<?>> getTypeArguments() {
        return getTypeArguments(getBeanType());
    }

    /**
     * Return the type arguments for the given interface or super type for this bean.
     *
     * @param type The super class or interface type
     * @return The type arguments
     */
    default @Nonnull List<Argument<?>> getTypeArguments(Class<?> type) {
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
    default @Nonnull Class[] getTypeParameters(@Nullable Class<?> type) {
        if (type == null) {
            return ReflectionUtils.EMPTY_CLASS_ARRAY;
        } else {
            final List<Argument<?>> typeArguments = getTypeArguments(type);
            return typeArguments.stream().map(Argument::getType).toArray(Class[]::new);
        }
    }

    /**
     *
     * Returns the type parameters as a class array for the bean type.
     *
     * @return The type parameters for the bean type as a class array.
     */
    default @Nonnull Class[] getTypeParameters() {
        return getTypeParameters(getBeanType());
    }

    /**
     * Return the type arguments for the given interface or super type for this bean.
     *
     * @param type The super class or interface type
     * @return The type arguments
     */
    default @Nonnull List<Argument<?>> getTypeArguments(String type) {
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
    default <R> ExecutableMethod<T, R> getRequiredMethod(String name, Class... argumentTypes) {
        return (ExecutableMethod<T, R>) findMethod(name, argumentTypes)
            .orElseThrow(() -> ReflectionUtils.newNoSuchMethodError(getBeanType(), name, argumentTypes));
    }

    /**
     * @return Whether the bean definition is abstract
     */
    default boolean isAbstract() {
        return Modifier.isAbstract(getBeanType().getModifiers());
    }
}
