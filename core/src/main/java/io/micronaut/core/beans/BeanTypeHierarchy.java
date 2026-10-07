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

import io.micronaut.core.annotation.Experimental;

import java.util.List;
import java.util.Optional;

/**
 * The hierarchy of an introspected type, generated at compile time when the type is introspected with
 * {@link io.micronaut.core.annotation.Introspected#hierarchy()}: every super class and interface of the type,
 * with the super class and the interfaces each of them declares, and the methods the type itself declares.
 *
 * <p>The types are those the compiler saw: the type itself first, then, depth first, its super class and the
 * super classes and interfaces of that, then its interfaces and theirs, each once. {@link Object} is reported
 * as a super class, it is not described itself. A type whose class cannot be loaded at runtime is left out.</p>
 *
 * <p>The {@link BeanIntrospection#getBeanMethods() bean methods} of the introspection are told apart into the
 * ones the type declares itself, overrides included, and the ones it inherits.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface BeanTypeHierarchy {

    /**
     * @return The introspected type
     */
    Class<?> getBeanType();

    /**
     * @return The introspected type and every super class and interface of it, each once, {@link Object} aside
     */
    List<Class<?>> getTypes();

    /**
     * @param type A type
     * @return Whether the type is the introspected type or one of its super types
     */
    default boolean contains(Class<?> type) {
        return getTypes().contains(type);
    }

    /**
     * The super class a type of the hierarchy declares.
     *
     * @param type The introspected type or one of its super types
     * @return The super class, empty for an interface, for {@link Object} and for a type outside the hierarchy
     */
    Optional<Class<?>> getSuperclass(Class<?> type);

    /**
     * The interfaces a type of the hierarchy declares itself, in declaration order.
     *
     * @param type The introspected type or one of its super types
     * @return The interfaces, empty for a type outside the hierarchy
     */
    List<Class<?>> getInterfaces(Class<?> type);

    /**
     * @return The bean methods of the introspection the introspected type declares itself, overrides included
     */
    List<BeanMethod<?, ?>> getDeclaredMethods();

    /**
     * Whether the introspected type declares a bean method itself, rather than inheriting it.
     *
     * @param method A bean method of the introspection
     * @return True if the type declares it, false if it inherits it
     */
    default boolean isDeclared(BeanMethod<?, ?> method) {
        return getDeclaredMethods().contains(method);
    }
}
