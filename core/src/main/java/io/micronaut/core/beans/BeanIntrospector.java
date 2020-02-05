/*
 * Copyright 2017-2019 original authors
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

package io.micronaut.core.beans;

import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.util.ArgumentUtils;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.annotation.concurrent.Immutable;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
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
@Immutable
public interface BeanIntrospector {

    /**
     * The default shared {@link BeanIntrospector}.
     */
    BeanIntrospector SHARED = new DefaultBeanIntrospector();

    /**
     * Finds introspections with the given filter.
     * @param filter A filter that receives a {@link BeanIntrospectionReference}
     * @return A collection of introspections
     */
    @NonNull
    Collection<BeanIntrospection<Object>> findIntrospections(@NonNull Predicate<? super BeanIntrospectionReference> filter);

    /**
     * Find a {@link BeanIntrospection} for the given bean type.
     *
     * @param beanType The bean type
     * @param <T> The bean generic type
     * @return An optional introspection
     */
    @NonNull <T> Optional<BeanIntrospection<T>> findIntrospection(@NonNull Class<T> beanType);

    /**
     * Finds introspections for classes annotated with the given stereotype.
     *
     * @param stereotype The stereotype
     * @return The introspections
     */
    default @NonNull Collection<BeanIntrospection<Object>> findIntrospections(@NonNull Class<? extends Annotation> stereotype) {
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
    default @NonNull Collection<BeanIntrospection<Object>> findIntrospections(@NonNull Class<? extends Annotation> stereotype, @NonNull String... packageNames) {
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
    default @NonNull <T> BeanIntrospection<T> getIntrospection(@NonNull Class<T> beanType) {
        return findIntrospection(beanType).orElseThrow(() -> new IntrospectionException("No bean introspection available for type [" + beanType + "]. Ensure the class is annotated with io.micronaut.core.annotation.Introspected"));
    }
}
