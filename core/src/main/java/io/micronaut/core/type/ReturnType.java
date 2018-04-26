/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.core.type;

import io.micronaut.core.annotation.AnnotationSource;
import io.micronaut.core.annotation.AnnotationUtil;

import java.lang.reflect.AnnotatedElement;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Models a return type of an {@link Executable} method in Micronaut.
 *
 * @param <T> The concrete type
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ReturnType<T> extends TypeVariableResolver, AnnotationSource {

    /**
     * @return The type of the argument
     */
    Class<T> getType();

    /**
     * @return The return type as an argument
     */
    default Argument<T> asArgument() {
        Collection<Argument<?>> values = getTypeVariables().values();
        return Argument.of(getType(), values.toArray(new Argument[values.size()]));
    }

    /**
     * Create a new return type from the given type and arguments.
     *
     * @param type          The type
     * @param typeArguments The type arguments
     * @param <T1>
     * @return A {@link ReturnType}
     */
    static <T1> ReturnType<T1> of(Class<T1> type, Argument<?>... typeArguments) {
        Map<String, Argument<?>> argumentMap = new LinkedHashMap<>(typeArguments.length);
        for (Argument<?> argument : typeArguments) {
            argumentMap.put(argument.getName(), argument);
        }
        return new ReturnType<T1>() {
            @Override
            public Class<T1> getType() {
                return type;
            }

            @Override
            public AnnotatedElement[] getAnnotatedElements() {
                return AnnotationUtil.ZERO_ANNOTATED_ELEMENTS;
            }

            @Override
            public Argument[] getTypeParameters() {
                return typeArguments;
            }

            @Override
            public Map<String, Argument<?>> getTypeVariables() {
                return argumentMap;
            }
        };
    }
}
