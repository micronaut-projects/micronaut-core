/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.Argument;

import java.util.List;

/**
 * One declaration of a bean method by one level of the hierarchy of a bean type, read from the introspection of
 * that level, see {@link BeanTypeHierarchy#getMethodDeclarations(BeanMethod)}.
 *
 * @param declaringType      The type declaring the method
 * @param annotationMetadata The annotations of the method as that level declares it
 * @param arguments          The parameters as that level declares them
 * @param returnArgument     The return value as that level declares it
 * @param exact              Whether the annotations are those of this declaration only: they are when the
 *                           introspection of the level {@link BeanIntrospection#separatesDeclarations() separates
 *                           the declarations}, else they merge the annotations of the methods it overrides
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public record BeanMethodDeclaration(Class<?> declaringType,
                                    AnnotationMetadata annotationMetadata,
                                    List<Argument<?>> arguments,
                                    Argument<?> returnArgument,
                                    boolean exact) {

    /**
     * Copies the arguments.
     *
     * @param declaringType      The type declaring the method
     * @param annotationMetadata The annotations of the method
     * @param arguments          The parameters
     * @param returnArgument     The return value
     * @param exact              Whether the annotations are those of this declaration only
     */
    public BeanMethodDeclaration {
        arguments = List.copyOf(arguments);
    }

    /**
     * The declaration a bean method reports.
     *
     * @param method The bean method
     * @return The declaration
     */
    public static BeanMethodDeclaration of(BeanMethod<?, ?> method) {
        return new BeanMethodDeclaration(method.getDeclaringType(),
            method.getDeclaredMethodAnnotationMetadata(),
            List.of(method.getArguments()),
            method.getReturnType().asArgument(),
            method.getDeclaringBean().separatesDeclarations());
    }
}
