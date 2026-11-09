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
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.TypeHierarchies;
import io.micronaut.core.type.TypeHierarchy;

import java.util.ArrayList;
import java.util.Arrays;
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
 * <p>The types are raw: the type arguments a type passes to its super class and interfaces are not described here.
 * The generic hierarchy of the introspected type is recorded beside the introspection as a {@link TypeHierarchy},
 * see {@link #getGenericHierarchy()} and {@link #getTypeClosure()}.</p>
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

    /**
     * The declarations of a bean method by the levels of the hierarchy, nearest first, each read from the
     * introspection of its level with {@link BeanIntrospector#SHARED}, see
     * {@link #getMethodDeclarations(BeanIntrospector, BeanMethod)}.
     *
     * @param method A bean method instance of the introspection, compared by identity
     * @return The bean methods declaring it, nearest first
     * @since 5.3.0
     */
    default List<BeanMethod<?, ?>> getMethodDeclarations(BeanMethod<?, ?> method) {
        return getMethodDeclarations(BeanIntrospector.SHARED, method);
    }

    /**
     * The declarations of a bean method by the levels of the hierarchy: for every one of its
     * {@link #getDeclaringTypes(BeanMethod) declaring types}, nearest first, what that type declares, read from
     * its introspection. Each declaration is the bean method of the introspection of its level: its
     * {@link BeanMethod#getDeclaredMethodAnnotationMetadata() declared annotations} are those of that level alone when
     * the introspection {@link BeanIntrospection#separatesDeclarations() separates the declarations}. The level of the
     * introspected type is the method itself; another level is the bean method
     * {@link #findOverridden(BeanIntrospector, Class, String, Class[]) it overrides} there, its erasure tolerated.
     *
     * <p>A level whose introspection the introspector does not find, or whose introspection does not list the
     * method as a bean method, is left out: compare the {@link BeanMethod#getDeclaringType() declaring types} with
     * {@link #getDeclaringTypes(BeanMethod)} to tell which levels are missing.</p>
     *
     * @param introspector The introspector of the levels
     * @param method       A bean method instance of the introspection, compared by identity
     * @return The bean methods declaring it, nearest first, empty for a method that is not a bean method of the introspection
     * @since 5.3.0
     */
    default List<BeanMethod<?, ?>> getMethodDeclarations(BeanIntrospector introspector, BeanMethod<?, ?> method) {
        List<Class<?>> levels = getDeclaringTypes(method);
        if (levels.isEmpty()) {
            return List.of();
        }
        Class<?>[] parameterTypes = Argument.toClassArray(method.getArguments());
        List<BeanMethod<?, ?>> declarations = new ArrayList<>(levels.size());
        for (Class<?> level : levels) {
            if (level == getBeanType()) {
                declarations.add(method);
            } else {
                findOverridden(introspector, level, method.getName(), parameterTypes)
                    .ifPresent(declarations::add);
            }
        }
        return List.copyOf(declarations);
    }

    /**
     * The bean method a level of the hierarchy declares that a method of the introspected type with the given
     * parameter types overrides, read with {@link BeanIntrospector#SHARED}, see
     * {@link #findOverridden(BeanIntrospector, Class, String, Class[])}.
     *
     * @param level          The introspected type or one of its super types
     * @param name           The method name
     * @param parameterTypes The parameter types of the overriding method
     * @return The overridden bean method of the introspection of the level
     * @since 5.3.0
     */
    default Optional<BeanMethod<?, ?>> findOverridden(Class<?> level, String name, Class<?>... parameterTypes) {
        return findOverridden(BeanIntrospector.SHARED, level, name, parameterTypes);
    }

    /**
     * The bean method a level of the hierarchy declares that a method of the introspected type with the given
     * parameter types overrides, read from the introspection of the level.
     *
     * <p>A declaration of the very same parameter types wins. Else a generic declaration is matched by its
     * erasure: {@code interface Repo<T> { void save(T t); }} declares {@code save(Object)}, which
     * {@code class StringRepo implements Repo<String>} overrides with {@code save(String)}. A parameter that is a
     * type variable of the level matches the type the introspected type gives that variable, read from the
     * {@link #getTypeClosure() type closure}; when the closure leaves it open, or is not recorded, it matches any
     * parameter type its erasure accepts. Two declarations cannot both match that way, that would be a name
     * clash in the introspected type.</p>
     *
     * <p>When the introspection of the level describes its own {@link BeanIntrospection#getTypeHierarchy()
     * hierarchy}, a bean method the level inherits rather than declares is skipped.</p>
     *
     * @param introspector   The introspector of the level
     * @param level          The introspected type or one of its super types
     * @param name           The method name
     * @param parameterTypes The parameter types of the overriding method
     * @return The overridden bean method of the introspection of the level, empty when the level is outside the
     * hierarchy, has no introspection or the introspection does not list such a method
     * @since 5.3.0
     */
    @SuppressWarnings("unchecked")
    default Optional<BeanMethod<?, ?>> findOverridden(BeanIntrospector introspector,
                                                      Class<?> level,
                                                      String name,
                                                      Class<?>... parameterTypes) {
        if (!contains(level)) {
            return Optional.empty();
        }
        BeanIntrospection<Object> introspection = introspector.findIntrospection((Class<Object>) level).orElse(null);
        if (introspection == null) {
            return Optional.empty();
        }
        BeanTypeHierarchy levelHierarchy = level == getBeanType() ? this : introspection.getTypeHierarchy().orElse(null);
        BeanMethod<?, ?> resolved = null;
        for (BeanMethod<Object, Object> candidate : introspection.getBeanMethods()) {
            Argument<?>[] arguments = candidate.getArguments();
            if (!candidate.getName().equals(name)
                || arguments.length != parameterTypes.length
                || (levelHierarchy != null && !levelHierarchy.isDeclared(candidate))) {
                continue;
            }
            if (Arrays.equals(Argument.toClassArray(arguments), parameterTypes)) {
                return Optional.of(candidate);
            }
            if (resolved == null && BeanTypeHierarchies.erasesTo(this, level, arguments, parameterTypes)) {
                resolved = candidate;
            }
        }
        return Optional.ofNullable(resolved);
    }

    /**
     * The generic hierarchy of the introspected type: its type variables with their bounds, its generic super class and
     * its generic interfaces as declared, recorded at compile time with the introspection. It is empty when the
     * introspection was compiled without the record, or the record is not on the class path.
     *
     * @return The generic hierarchy of the introspected type
     * @since 5.3.0
     */
    default Optional<TypeHierarchy> getGenericHierarchy() {
        return TypeHierarchies.find(getBeanType());
    }

    /**
     * The type closure of the introspected type with its type arguments: the type parameterized by its own variables,
     * then every super type with the arguments substituted along the path, each once, {@link Object} last, see
     * {@link TypeHierarchy#getTypeClosure(Argument)}. Without a {@link #getGenericHierarchy() generic hierarchy} the
     * closure is {@link #getTypes() the raw types} as arguments, followed by {@link Object}.
     *
     * @return The type closure
     * @since 5.3.0
     */
    default List<Argument<?>> getTypeClosure() {
        Optional<TypeHierarchy> hierarchy = getGenericHierarchy();
        if (hierarchy.isPresent()) {
            return hierarchy.get().getTypeClosure(hierarchy.get().getDeclaration());
        }
        List<Argument<?>> closure = new ArrayList<>(getTypes().size() + 1);
        for (Class<?> type : getTypes()) {
            closure.add(Argument.of(type));
        }
        closure.add(Argument.OBJECT_ARGUMENT);
        return closure;
    }
}
