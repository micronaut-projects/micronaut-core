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

import io.micronaut.core.annotation.AnnotationBuilder;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.naming.Described;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;
import io.micronaut.inject.annotation.RegisteredAnnotationBuilders;

import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>An executable method is a compile time produced invocation of a method call. Avoiding the use of reflection and
 * allowing the JIT to optimize the call</p>
 *
 * @param <T> The declaring type
 * @param <R> The result of the method call
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ExecutableMethod<T, R> extends Executable<T, R>, MethodReference<T, R>, Described {
    /**
     * Defines whether the method is abstract.
     *
     * @return Is the method abstract.
     * @since 1.2.3
     */
    default boolean isAbstract() {
        return false;
    }

    /**
     * Defines whether the method is Kotlin suspend function.
     *
     * @return Is the method Kotlin suspend function.
     * @since 1.3.0
     */
    @Experimental
    default boolean isSuspend() {
        return false;
    }

    /**
     * Get the method description.
     * @param simple If simple type names are to be used
     * @return The method description
     */
    @Override
    default String getDescription(boolean simple) {
        Argument<R> argument = getReturnType().asArgument();
        String typeString = argument.getTypeString(simple);
        String args = Arrays.stream(getArguments()).map(arg -> arg.getTypeString(simple) + " " + arg.getName()).collect(Collectors.joining(","));
        return typeString + " " + getName() + "(" + args + ")";
    }

    @Override
    default String getDescription() {
        return getDescription(true);
    }

    /**
     * A value derived from this method, computed once by the factory and kept for the next calls. Interceptors use it
     * to avoid reading the annotation metadata or rebuilding the same objects on every invocation.
     *
     * <p>The key identifies the value: use a type owned by the caller, such as a record holding what the factory
     * derives. The factory must not have side effects and must return an immutable value: it may be invoked more than
     * once when calls race, and the value of the first call to finish is kept. When the metadata of the method resolves
     * property placeholders, the value is kept until the properties may have changed, and is computed again then: a
     * placeholder whose resolution changes without the properties changing, such as {@code ${random.uuid}}, keeps its
     * first value. The
     * value is computed on every call, without being kept, when the metadata has evaluated expressions, which can
     * depend on the invocation, and by implementations that don't keep values.</p>
     *
     * <p>The methods of a bean definition, and of the proxies a context creates, belong to that context: the values
     * they keep are dropped with it, and are not seen by another context.</p>
     *
     * @param key     The key of the value
     * @param factory Computes the value from the method
     * @param <V>     The value type
     * @return The value
     * @since 5.3.0
     */
    @Experimental
    default <V extends @Nullable Object> V getCachedValue(Class<V> key, Function<? super ExecutableMethod<T, R>, ? extends V> factory) {
        return factory.apply(this);
    }

    /**
     * A value derived only from the unevaluated metadata of this method, computed once by the factory and kept for
     * the next calls as {@link #getCachedValue(Class, Function)} keeps values, but also kept when the metadata has
     * evaluated expressions.
     *
     * <p>The factory may read the annotations, their ordinary members and {@link #getTargetAnnotationMetadata()},
     * but must not read the value of an evaluated {@code #{...}} member: the value is not computed again for the
     * invocation the expression depends on. Property placeholders {@code ${...}} are resolved, and the value is
     * computed again when the properties may have changed, as {@link #getCachedValue(Class, Function)} does. Use it
     * for values such as the interceptors bound to a method or the name of a member, where
     * {@link #getCachedValue(Class, Function)} would compute the value on every call of a method that has an unrelated
     * evaluated expression. The value is kept apart from the one kept by {@link #getCachedValue(Class, Function)}
     * under the same key.</p>
     *
     * @param key     The key of the value
     * @param factory Computes the value from the method, reading only its unevaluated metadata
     * @param <V>     The value type
     * @return The value
     * @since 5.3.0
     */
    @Experimental
    default <V extends @Nullable Object> V getCachedUnevaluatedValue(Class<V> key, Function<? super ExecutableMethod<T, R>, ? extends V> factory) {
        return factory.apply(this);
    }

    /**
     * The value of the given annotation on this method, kept as {@link #getCachedValue(Class, Function)} keeps values.
     *
     * @param annotationType The annotation type
     * @param <A>            The annotation type
     * @return The annotation value or {@code null} if the method doesn't have the annotation
     * @since 5.3.0
     */
    @Experimental
    default <A extends Annotation> @Nullable AnnotationValue<A> getCachedAnnotationValue(Class<A> annotationType) {
        return getAnnotation(annotationType);
    }

    /**
     * The given annotation of this method, built by the {@link AnnotationBuilder} registered for its type and kept as
     * {@link #getCachedValue(Class, Function)} keeps values. The annotation is never synthesized reflectively.
     *
     * @param annotationType The annotation type
     * @param <A>            The annotation type
     * @return The annotation or {@code null} if the method doesn't have the annotation
     * @throws IllegalStateException If no {@link AnnotationBuilder} is registered for the annotation type
     * @since 5.3.0
     */
    @Experimental
    default <A extends Annotation> @Nullable A getCachedAnnotation(Class<A> annotationType) {
        AnnotationValue<A> annotationValue = getCachedAnnotationValue(annotationType);
        return annotationValue != null
            ? RegisteredAnnotationBuilders.build(annotationType, annotationValue, Thread.currentThread().getContextClassLoader())
            : null;
    }

}
