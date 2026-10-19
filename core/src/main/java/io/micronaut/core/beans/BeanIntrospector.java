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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.util.ArgumentUtils;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Primary interface for obtaining {@link BeanIntrospection} instances that are computed at compilation time.
 *
 * @author graemerocher
 * @since 1.0
 * @see io.micronaut.core.annotation.Introspected
 * @see BeanIntrospection
 */
//@Immutable
public interface BeanIntrospector {

    /**
     * The default shared {@link BeanIntrospector}.
     */
    BeanIntrospector SHARED = new DefaultBeanIntrospector();

    /**
     * Creates a new default bean introspector that uses the given
     * classloader.
     *
     * @since 3.0.1
     * @param classLoader The class loader to load introspections
     * @return A new bean introspector
     */
    static BeanIntrospector forClassLoader(ClassLoader classLoader) {
        return new DefaultBeanIntrospector(classLoader);
    }

    /**
     * The shared bean introspector that serves the registered introspection references only, those generated at
     * compilation time: a type without one is a miss, and no {@link BeanIntrospectionFallback} is loaded or asked,
     * so no introspection is ever built by reflection.
     *
     * <p>It selects the class loader as {@link #SHARED} does, keeps its own index and is invalidated with it.
     * "Generated" means registered as a reference: an introspection a library registers by hand is served as well,
     * since nothing tells the two apart.</p>
     *
     * @return The generated-only bean introspector
     * @since 5.3.0
     */
    @Experimental
    static BeanIntrospector generatedOnly() {
        return DefaultBeanIntrospector.sharedGeneratedOnly();
    }

    /**
     * Creates a bean introspector that uses the given class loader and serves the registered introspection
     * references only, as {@link #generatedOnly()} does: a type without one is a miss, and no
     * {@link BeanIntrospectionFallback} is loaded or asked.
     *
     * @param classLoader The class loader to load introspections
     * @return A new generated-only bean introspector
     * @since 5.3.0
     */
    @Experimental
    static BeanIntrospector generatedOnly(ClassLoader classLoader) {
        ArgumentUtils.requireNonNull("classLoader", classLoader);
        return DefaultBeanIntrospector.generatedOnly(classLoader);
    }

    /**
     * Finds introspections with the given filter.
     * @param filter A filter that receives a {@link BeanIntrospectionReference}
     * @return A collection of introspections
     */
    Collection<BeanIntrospection<Object>> findIntrospections(Predicate<? super BeanIntrospectionReference<?>> filter);

    /**
     * Finds introspections with the given filter.
     * @param filter A filter that receives a {@link BeanIntrospectionReference}
     * @return A collection of introspections
     * @since 3.0
     */
    Collection<Class<?>> findIntrospectedTypes(Predicate<? super BeanIntrospectionReference<?>> filter);

    /**
     * Find a {@link BeanIntrospection} for the given bean type.
     *
     * @param beanType The bean type
     * @param <T> The bean generic type
     * @return An optional introspection
     */
 <T> Optional<BeanIntrospection<T>> findIntrospection(Class<T> beanType);

    /**
     * Finds the {@link BeanIntrospection} registered for the class of the given name, without loading the class by name
     * and without consulting a {@link BeanIntrospectionFallback}, which needs the class.
     *
     * <p>The name is the binary name, as {@link Class#getName()} returns it: {@code com.example.Outer$Inner} for a
     * nested class. The default implementation filters the references through
     * {@link #findIntrospections(Predicate)}; the default introspector looks the name up in its index and loads only
     * the matching reference.</p>
     *
     * @param className The binary name of the bean type
     * @return An optional introspection
     * @since 5.3.0
     */
    @Experimental
    default Optional<BeanIntrospection<?>> findIntrospection(String className) {
        ArgumentUtils.requireNonNull("className", className);
        Iterator<BeanIntrospection<Object>> introspections = findIntrospections(ref -> className.equals(ref.getName())).iterator();
        return introspections.hasNext() ? Optional.of(introspections.next()) : Optional.empty();
    }

    /**
     * Finds introspections for classes annotated with the given stereotype.
     *
     * @param stereotype The stereotype
     * @return The introspections
     */
    default Collection<BeanIntrospection<Object>> findIntrospections(Class<? extends Annotation> stereotype) {
        ArgumentUtils.requireNonNull("stereotype", stereotype);
        return findIntrospections(ref -> ref.getAnnotationMetadata().hasStereotype(stereotype));
    }

    /**
     * Finds introspections for classes annotated with the given stereotype.
     *
     * @param stereotype The stereotype
     * @param packageNames The package names to include in the search
     * @return The introspections
     */
    default Collection<BeanIntrospection<Object>> findIntrospections(Class<? extends Annotation> stereotype, String... packageNames) {
        ArgumentUtils.requireNonNull("stereotype", stereotype);
        ArgumentUtils.requireNonNull("packageNames", packageNames);
        return findIntrospections(ref ->
                ref.getAnnotationMetadata().hasStereotype(stereotype) && Arrays.stream(packageNames).anyMatch(s -> ref.getName().startsWith(s + "."))
        );
    }

    /**
     * Retrieves an introspection for the given type.
     * @param beanType The bean type
     * @param <T> The bean generic type
     * @return The introspection
     * @throws IntrospectionException If no introspection data is found and the bean is not annotated with {@link io.micronaut.core.annotation.Introspected}
     */
    default <T> BeanIntrospection<T> getIntrospection(Class<T> beanType) {
        return findIntrospection(beanType).orElseThrow(() -> new IntrospectionException("No bean introspection available for type [" + beanType + "]. Ensure the class is annotated with io.micronaut.core.annotation.Introspected"));
    }
}
