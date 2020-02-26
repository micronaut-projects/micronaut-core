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
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.type.Argument;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * An interface for wrapping a {@link BeanDefinition} with another that delegates and potentially decorates the
 * {@link BeanDefinition} instance.
 *
 * @param <T> The bean definition type
 * @author Graeme Rocher
 * @since 1.0
 */
public interface DelegatingBeanDefinition<T> extends BeanDefinition<T> {

    /**
     * @return The target definition
     */
    BeanDefinition<T> getTarget();

    @Override
    default boolean requiresMethodProcessing() {
        return getTarget().requiresMethodProcessing();
    }

    @Override
    default Optional<Class<? extends Annotation>> getScope() {
        return getTarget().getScope();
    }

    @Override
    default AnnotationMetadata getAnnotationMetadata() {
        return getTarget().getAnnotationMetadata();
    }

    @Override
    default <R> ExecutableMethod<T, R> getRequiredMethod(String name, Class... argumentTypes) {
        return getTarget().getRequiredMethod(name, argumentTypes);
    }

    @Override
    default boolean isAbstract() {
        return getTarget().isAbstract();
    }

    @Override
    default boolean isSingleton() {
        return getTarget().isSingleton();
    }

    @Override
    default boolean isProvided() {
        return getTarget().isProvided();
    }

    @Override
    default boolean isIterable() {
        return getTarget().isIterable();
    }

    @Override
    default Class<T> getBeanType() {
        return getTarget().getBeanType();
    }

    @Override
    default ConstructorInjectionPoint<T> getConstructor() {
        return getTarget().getConstructor();
    }

    @Override
    default Collection<Class> getRequiredComponents() {
        return getTarget().getRequiredComponents();
    }

    @Override
    default Collection<MethodInjectionPoint> getInjectedMethods() {
        return getTarget().getInjectedMethods();
    }

    @Override
    default Collection<FieldInjectionPoint> getInjectedFields() {
        return getTarget().getInjectedFields();
    }

    @Override
    default Collection<MethodInjectionPoint> getPostConstructMethods() {
        return getTarget().getPostConstructMethods();
    }

    @Override
    default Collection<MethodInjectionPoint> getPreDestroyMethods() {
        return getTarget().getPreDestroyMethods();
    }

    @Override
    default String getName() {
        return getTarget().getName();
    }

    @Override
    default <R> Optional<ExecutableMethod<T, R>> findMethod(String name, Class... argumentTypes) {
        return getTarget().findMethod(name, argumentTypes);
    }

    @Override
    default <R> Stream<ExecutableMethod<T, R>> findPossibleMethods(String name) {
        return getTarget().findPossibleMethods(name);
    }

    @Override
    default T inject(BeanContext context, T bean) {
        return getTarget().inject(context, bean);
    }

    @Override
    default T inject(BeanResolutionContext resolutionContext, BeanContext context, T bean) {
        return getTarget().inject(resolutionContext, context, bean);
    }

    @Override
    default Collection<ExecutableMethod<T, ?>> getExecutableMethods() {
        return getTarget().getExecutableMethods();
    }

    @Override
    default boolean isPrimary() {
        return getTarget().isPrimary();
    }

    @Override
    default boolean isEnabled(BeanContext context) {
        return getTarget().isEnabled(context);
    }

    @Override
    default Optional<Class<?>> getDeclaringType() {
        return getTarget().getDeclaringType();
    }

    @Override
    default @NonNull List<Argument<?>> getTypeArguments(String type) {
        return getTarget().getTypeArguments(type);
    }
}
