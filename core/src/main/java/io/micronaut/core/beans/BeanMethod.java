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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.naming.Named;
import io.micronaut.core.type.Executable;
import io.micronaut.core.type.ReturnType;

/**
 * Represents a method on a {@link BeanIntrospection}.
 *
 * <p>A {@link BeanMethod} is created when an accessible method on a class is annotated with {@code io.micronaut.context.annotation.Executable} or an annotation stereotype annotated with that annotation</p>
 *
 * @param <B> The bean type
 * @param <T> The return type
 * @since 2.3.0
 */
public interface BeanMethod<B, T> extends Executable<B, T>, Named {
    /**
     * @return The declaring bean introspection.
     */
    @NonNull
    BeanIntrospection<B> getDeclaringBean();

    /**
     * @return The return type.
     */
    @NonNull ReturnType<T> getReturnType();
}
