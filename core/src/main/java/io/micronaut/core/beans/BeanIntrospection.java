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
package io.micronaut.core.beans;

import io.micronaut.core.annotation.AnnotationMetadataDelegate;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;

import io.micronaut.core.annotation.NonNull;
import javax.annotation.concurrent.Immutable;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * A {@link BeanIntrospection} is the result of compile time computation of a beans properties and annotation metadata.
 *
 * <p>This interface allows you to instantiate and read and write to bean properties without using reflection or caching reflective metadata, which is
 * expensive from a memory consumption perspective.</p>
 *
 * <p>{@link BeanIntrospection} instances can be obtained either via {@link #getIntrospection(Class)} or via the {@link BeanIntrospector}.</p>
 *
 * <p>A {@link BeanIntrospection} is only computed at compilation time if the class is annotated with {@link io.micronaut.core.annotation.Introspected}. </p>
 *
 * @param <T> The bean type
 * @see BeanIntrospector
 * @see io.micronaut.core.annotation.Introspected
 * @author graemerocher
 * @since 1.1
 */
@Immutable
public interface BeanIntrospection<T> extends AnnotationMetadataDelegate {

    /**
     * @return A immutable collection of properties.
     */
    @NonNull Collection<BeanProperty<T, Object>> getBeanProperties();

    /**
     * Get all the bean properties annotated for the given annotation type. If the annotation is {@link io.micronaut.core.annotation.Introspected#indexed()} by the given annotation,
     * then it will be included in the resulting list.
     *
     * @param annotationType The annotation type
     * @return A immutable collection of properties.
     * @see io.micronaut.core.annotation.Introspected#indexed()
     */
    @NonNull Collection<BeanProperty<T, Object>> getIndexedProperties(@NonNull Class<? extends Annotation> annotationType);

    /**
     * Instantiates an instance of the bean, throwing an exception is instantiation is not possible.
     *
     * @return An instance
     * @throws InstantiationException If the bean cannot be instantiated or the arguments are not satisfied.
     */
    @NonNull T instantiate() throws InstantiationException;

    /**
     * Instantiates an instance of the bean, throwing an exception is instantiation is not possible.
     *
     * @param arguments The arguments required to instantiate bean. Should match the types returned by {@link #getConstructorArguments()}
     * @return An instance
     * @throws InstantiationException If the bean cannot be instantiated.
     */
    default @NonNull T instantiate(Object... arguments) throws InstantiationException {
        return instantiate(true, arguments);
    }

    /**
     * Instantiates an instance of the bean, throwing an exception is instantiation is not possible.
     *
     * @param strictNullable If true, require null parameters to be annotated with a nullable annotation
     * @param arguments The arguments required to instantiate bean. Should match the types returned by {@link #getConstructorArguments()}
     * @return An instance
     * @throws InstantiationException If the bean cannot be instantiated.
     */
    @NonNull T instantiate(boolean strictNullable, Object... arguments) throws InstantiationException;

    /**
     * The bean type.
     * @return The bean type
     */
    @NonNull Class<T> getBeanType();

    /**
     * Get all the bean properties annotated for the given type.
     *
     * @param annotationType The annotation type
     * @param annotationValue The annotation value
     * @return A immutable collection of properties.
     * @see io.micronaut.core.annotation.Introspected#indexed()
     */
    @NonNull Optional<BeanProperty<T, Object>> getIndexedProperty(
            @NonNull Class<? extends Annotation> annotationType,
            @NonNull String annotationValue);

    /**
     * Returns the {@link BeanMethod} instances for this introspection.
     *
     * <p>The {@link BeanMethod} instances are only those methods annotated with {@code io.micronaut.context.annotation.Executable} and hence represent a subset
     * of the actual methods of the class and do not include any methods that are exposed as {@link BeanProperty} instances.</p>
     *
     * @return A immutable collection of methods.
     *
     * @since 2.3.0
     */
    @NonNull default Collection<BeanMethod<T, Object>> getBeanMethods() {
        return Collections.emptyList();
    }

    /**
     * Get all the bean properties annotated for the given type.
     *
     * @param annotationType The annotation type
     * @return A immutable collection of properties.
     * @see io.micronaut.core.annotation.Introspected#indexed()
     */
    default @NonNull Optional<BeanProperty<T, Object>> getIndexedProperty(
            @NonNull Class<? extends Annotation> annotationType) {
        return getIndexedProperties(annotationType).stream().findFirst();
    }

    /**
     * The constructor arguments needed to instantiate the bean.
     * @return An argument array
     */
    default @NonNull Argument<?>[] getConstructorArguments() {
        return Argument.ZERO_ARGUMENTS;
    }

    /**
     * Obtain a property by name.
     * @param name The name of the property
     * @return A bean property if found
     */
    default @NonNull Optional<BeanProperty<T, Object>> getProperty(@NonNull String name) {
        return Optional.empty();
    }

    /**
     * Gets a property of the given name and type or throws {@link IntrospectionException} if the property is not present.
     * @param name The name
     * @param type The type
     * @param <P> The property generic type
     * @return The property
     */
    default @NonNull <P> BeanProperty<T, P> getRequiredProperty(@NonNull String name, @NonNull Class<P> type) {
        return getProperty(name, type)
                .orElseThrow(() -> new IntrospectionException("No property [" + name + "] of type [" + type + "] present"));
    }

    /**
     * Obtain a property by name and type.
     * @param name The name of the property
     * @param type The property type to search for
     * @return A bean property if found
     * @param <P> The property type
     */
    default @NonNull <P> Optional<BeanProperty<T, P>> getProperty(@NonNull String name, @NonNull Class<P> type) {
        ArgumentUtils.requireNonNull("name", name);
        ArgumentUtils.requireNonNull("type", type);

        final BeanProperty<T, ?> prop = getProperty(name).orElse(null);
        if (prop != null && type.isAssignableFrom(prop.getType())) {
            //noinspection unchecked
            return Optional.of((BeanProperty<T, P>) prop);
        }
        return Optional.empty();
    }

    /**
     * The property names as an array.
     *
     * @return The properties names
     */
    default @NonNull String[] getPropertyNames() {
        return getBeanProperties().stream().map(BeanProperty::getName).toArray(String[]::new);
    }

    /**
     * Obtains an introspection from the default {@link BeanIntrospector}.
     *
     * @param type The type
     * @param <T2> The generic type
     * @return The introspection
     * @throws io.micronaut.core.beans.exceptions.IntrospectionException If the introspection cannot be found or errors when loading
     */
    static <T2> BeanIntrospection<T2> getIntrospection(Class<T2> type) {
        return BeanIntrospector.SHARED.getIntrospection(type);
    }
}
