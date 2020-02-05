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
package io.micronaut.inject;

import io.micronaut.core.annotation.AnnotatedElement;
import io.micronaut.core.annotation.AnnotationMetadataDelegate;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * A reference to a method.
 *
 * @param <T> The type
 * @param <R> The result type
 * @author Graeme Rocher
 * @since 1.0
 */
public interface MethodReference<T, R> extends AnnotationMetadataDelegate, AnnotatedElement {

    /**
     * @return The required argument types
     */
    Argument[] getArguments();

    /**
     * @return The target method
     */
    Method getTargetMethod();

    /**
     * @return Return the return type
     */
    ReturnType<R> getReturnType();

    /**
     * @return The bean that declares this injection point
     */
    Class<T> getDeclaringType();

    /**
     * @return The name of the method
     */
    String getMethodName();

    /**
     * @return The argument types
     */
    default Class[] getArgumentTypes() {
        return Arrays
            .stream(getArguments())
            .map(Argument::getType)
            .toArray(Class[]::new);
    }

    /**
     * @return The argument types
     */
    default String[] getArgumentNames() {
        return Arrays
            .stream(getArguments())
            .map(Argument::getName)
            .toArray(String[]::new);
    }

    @NonNull
    @Override
    default String getName() {
        return getMethodName();
    }
}
