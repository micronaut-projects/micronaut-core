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
package io.micronaut.context.watch;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Objects;

/**
 * An executable method of a bean, as a method watch delivers it: the definition of the bean the method is
 * invoked on, and the method.
 *
 * @param definition The bean's definition
 * @param method The method
 * @param <A> The annotation type watched
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public record BeanExecutableMethod<A extends Annotation>(BeanDefinition<?> definition, ExecutableMethod<?, ?> method) {

    /**
     * Validating constructor.
     *
     * @param definition The definition
     * @param method The method
     */
    public BeanExecutableMethod {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(method, "method");
    }

    /**
     * Whether another method is a generation of this one: the same bean, name and parameter types.
     *
     * @param other The other method
     * @return True if it is
     */
    public boolean sameMethod(BeanExecutableMethod<?> other) {
        return BeanDefinitionChange.sameBean(definition, other.definition)
            && method.getMethodName().equals(other.method.getMethodName())
            && Arrays.equals(typeNames(method), typeNames(other.method));
    }

    private static String[] typeNames(ExecutableMethod<?, ?> method) {
        Class<?>[] types = method.getArgumentTypes();
        String[] names = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            names[i] = types[i].getName();
        }
        return names;
    }
}
