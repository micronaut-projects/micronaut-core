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
 * as a super class, it is not described itself.</p>
 *
 * <p>The types are raw: the type arguments a type passes to its super class and interfaces are not described.</p>
 *
 * <p>Each {@link BeanIntrospection#getBeanMethods() bean method} of the introspection knows its declaring levels:
 * the type declaring it and every type of the hierarchy declaring a method it overrides, whether or not that
 * method is a bean method. A bean method the introspected type does not declare is inherited. The methods are
 * looked up by identity: pass the {@link BeanMethod} instances of the introspection the hierarchy belongs to.</p>
 *
 * <p>Only bean methods are described here. The declarations of a property, the field and the accessor of every
 * type declaring one, are the {@link BeanProperty#getMembers() members} of the property, each reporting its
 * {@link BeanPropertyMember#getDeclaringType() declaring type}, when the introspection is generated with
 * {@link io.micronaut.core.annotation.Introspected#members()}. A bean method has no such per-declaration
 * element, so its declaring levels are listed here.</p>
 *
 * <p>This interface is implemented by generated code only. Methods may be added to it in later versions without
 * a default implementation.</p>
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
     * The declaring levels of a bean method: the type declaring it, then every type of the hierarchy declaring a
     * method it overrides, each once, nearest first: in the breadth first order of the hierarchy of the
     * introspected type, the super class before the interfaces, so a type the introspected type extends or
     * implements directly comes before a type that one extends or implements in turn. An inherited method
     * overrides the methods of the interfaces the introspected type introduces as well, those are levels too.
     * {@link Class#isInterface()} tells which levels are interfaces.
     *
     * @param method A bean method instance of the introspection, compared by identity
     * @return The declaring types, empty for a method that is not a bean method of the introspection
     */
    List<Class<?>> getDeclaringTypes(BeanMethod<?, ?> method);

    /**
     * @return The bean methods of the introspection the introspected type declares itself, overrides included
     */
    List<BeanMethod<?, ?>> getDeclaredMethods();

    /**
     * Whether the introspected type declares a bean method itself, rather than inheriting it.
     *
     * @param method A bean method instance of the introspection, compared by identity
     * @return True if the type declares it, false if it inherits it or it is not a bean method of the introspection
     */
    default boolean isDeclared(BeanMethod<?, ?> method) {
        List<Class<?>> levels = getDeclaringTypes(method);
        return !levels.isEmpty() && levels.get(0) == getBeanType();
    }
}
