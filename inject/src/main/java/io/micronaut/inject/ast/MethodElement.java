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
package io.micronaut.inject.ast;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.util.ArrayUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Stores data about an element that references a method.
 *
 * @author James Kleeh
 * @since 1.0
 */
public interface MethodElement extends MemberElement {

    /**
     * @return The return type of the method
     */
    @NonNull
    ClassElement getReturnType();

    /**
     * @return The method parameters
     */
    @NonNull ParameterElement[] getParameters();

    /**
     * Takes this method element and transforms into a new method element with the given parameters appended to the existing parameters.
     * @param newParameters The new parameters
     * @return A new method element
     * @since 2.3.0
     */
    @NonNull MethodElement withNewParameters(@NonNull ParameterElement...newParameters);

    /**
     * If {@link #isSuspend()} returns true this method exposes the continuation parameter in addition to the other parameters of the method.
     * @return The suspend parameters
     * @since 2.3.0
     */
    default @NonNull ParameterElement[] getSuspendParameters() {
        return getParameters();
    }

    /**
     * Returns true if the method has parameters.
     *
     * @return True if it does
     */
    default boolean hasParameters() {
        return getParameters().length > 0;
    }

    /**
     * Is the method a Kotlin suspend function.
     * @return True if it is.
     * @since 2.3.0
     */
    default boolean isSuspend() {
        return false;
    }

    /**
     * Is the method a default method on an interfaces.
     * @return True if it is.
     * @since 2.3.0
     */
    default boolean isDefault() {
        return false;
    }

    /**
     * The generic return type of the method.
     *
     * @return The return type of the method
     * @since 1.1.1
     */
    default @NonNull ClassElement getGenericReturnType() {
        return getReturnType();
    }

    /**
     * Get the method description.
     * @param simple If simple type names are to be used
     * @return The method description
     */
    default @NonNull String getDescription(boolean simple) {
        String typeString = simple ? getReturnType().getSimpleName() : getReturnType().getName();
        String args = Arrays.stream(getParameters()).map(arg -> simple ? arg.getType().getSimpleName() : arg.getType().getName() + " " + arg.getName()).collect(Collectors.joining(","));
        return typeString + " " + getName() + "(" + args + ")";
    }

    /**
     * Creates a {@link MethodElement} for the given parameters.
     * @param declaredType The declaring type
     * @param annotationMetadata The annotation metadata
     * @param returnType The return type
     * @param genericReturnType The generic return type
     * @param name The name
     * @param parameterElements The parameter elements
     * @return The method element
     */
    static @NonNull MethodElement of(
            @NonNull ClassElement declaredType,
            @NonNull AnnotationMetadata annotationMetadata,
            @NonNull ClassElement returnType,
            @NonNull ClassElement genericReturnType,
            @NonNull String name,
            ParameterElement...parameterElements) {
        return new MethodElement() {
            @NotNull
            @Override
            public ClassElement getReturnType() {
                return returnType;
            }

            @NotNull
            @Override
            public ClassElement getGenericReturnType() {
                return genericReturnType;
            }

            @Override
            public ParameterElement[] getParameters() {
                return parameterElements;
            }

            @Override
            public MethodElement withNewParameters(ParameterElement... newParameters) {
                return MethodElement.of(
                        declaredType,
                        annotationMetadata,
                        returnType,
                        genericReturnType,
                        name,
                        ArrayUtils.concat(parameterElements, newParameters)
                );
            }

            @NotNull
            @Override
            public AnnotationMetadata getAnnotationMetadata() {
                return annotationMetadata;
            }

            @Override
            public ClassElement getDeclaringType() {
                return declaredType;
            }

            @NotNull
            @Override
            public String getName() {
                return name;
            }

            @Override
            public boolean isPackagePrivate() {
                return false;
            }

            @Override
            public boolean isProtected() {
                return false;
            }

            @Override
            public boolean isPublic() {
                return true;
            }

            @NotNull
            @Override
            public Object getNativeType() {
                throw new UnsupportedOperationException("No native method type present");
            }
        };
    }
}
