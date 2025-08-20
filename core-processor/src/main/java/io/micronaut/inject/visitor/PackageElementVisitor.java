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
package io.micronaut.inject.visitor;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.core.util.Toggleable;
import io.micronaut.inject.ast.PackageElement;
import io.micronaut.inject.processing.ProcessingException;

import java.util.Optional;
import java.util.Set;

/**
 * The visitor of the package elements.
 *
 * @param <A> The annotation required on the package or {@link Object} for any package.
 * @author Denis Stepanov
 * @since 4.10
 */
@Experimental
public interface PackageElementVisitor<A> extends Ordered, Toggleable {

    /**
     * Executed when a package is encountered that matches the {@literal <}E{@literal >} generic.
     *
     * @param element The package element
     * @param context The visitor context
     */
    default void visitPackage(@NonNull PackageElement element, @NonNull VisitorContext context) throws ProcessingException {
    }

    /**
     * @return The supported default annotation names.
     */
    @NonNull
    default Set<String> getSupportedAnnotationNames() {
        Optional<Class<?>> clazz = GenericTypeUtils.resolveInterfaceTypeArgument(getClass(), PackageElementVisitor.class);
        if (clazz.isPresent()) {
            Class<?> classType = clazz.get();
            String classTypeName = classType.getName();
            if (classType == Object.class) {
                classTypeName = getPackageAnnotationName();
            }
            if (classTypeName.equals(Object.class.getName())) {
                return Set.of("*");
            } else {
                return Set.of(classTypeName);
            }
        }
        return Set.of("*");
    }

    /**
     * @return The visitor kind.
     */
    default @NonNull TypeElementVisitor.VisitorKind getVisitorKind() {
        return TypeElementVisitor.VisitorKind.AGGREGATING;
    }

    /**
     * Retrieves the annotation name associated with the package.
     *
     * @return The fully qualified name of the annotation class as a string.
     */
    @NonNull
    default String getPackageAnnotationName() {
        return Object.class.getName();
    }

}
