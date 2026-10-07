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

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The hierarchy of an introspected type, generated at compile time when the type is introspected with
 * {@link io.micronaut.core.annotation.Introspected#hierarchy()}: every super class and interface of the type,
 * with the super class and the interfaces each of them declares, and the methods the type itself declares.
 *
 * <p>The types are those the compiler saw: the type itself first, then, depth first, its super class and the
 * super classes and interfaces of that, then its interfaces and theirs, each once. {@link Object} is reported
 * as a super class, it is not described itself. A type whose class cannot be loaded at runtime is left out.
 * The classes are loaded when the types are first asked for.</p>
 *
 * <p>The declared methods are the instance methods the type declares that are not private, whether or not the
 * introspection describes them as {@link BeanMethod bean methods}, without the methods it inherits.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class BeanTypeHierarchy {

    private final Class<?> beanType;
    private final TypeRef[] typeRefs;
    private final List<DeclaredMethod> declaredMethods;
    private volatile @Nullable Map<Class<?>, TypeEntry> types;

    /**
     * The hierarchy as the generated introspection describes it.
     *
     * @param beanType The introspected type
     * @param typeRefs The types, the introspected one first
     * @param declaredMethods The methods the introspected type declares
     */
    @Internal
    @UsedByGeneratedCode
    public BeanTypeHierarchy(Class<?> beanType, TypeRef[] typeRefs, DeclaredMethod[] declaredMethods) {
        this.beanType = beanType;
        this.typeRefs = typeRefs;
        this.declaredMethods = List.of(declaredMethods);
    }

    /**
     * @return The introspected type
     */
    public Class<?> getBeanType() {
        return beanType;
    }

    /**
     * @return The introspected type and every super class and interface of it, each once, {@link Object} aside
     */
    public List<Class<?>> getTypes() {
        return List.copyOf(types().keySet());
    }

    /**
     * @param type A type
     * @return Whether the type is the introspected type or one of its super types
     */
    public boolean contains(Class<?> type) {
        return types().containsKey(type);
    }

    /**
     * The super class a type of the hierarchy declares.
     *
     * @param type The introspected type or one of its super types
     * @return The super class, empty for an interface, for {@link Object} and for a type outside the hierarchy
     */
    public Optional<Class<?>> getSuperclass(Class<?> type) {
        TypeEntry entry = types().get(type);
        return entry == null ? Optional.empty() : Optional.ofNullable(entry.superclass());
    }

    /**
     * The interfaces a type of the hierarchy declares itself, in declaration order.
     *
     * @param type The introspected type or one of its super types
     * @return The interfaces, empty for a type outside the hierarchy
     */
    public List<Class<?>> getInterfaces(Class<?> type) {
        TypeEntry entry = types().get(type);
        return entry == null ? List.of() : entry.interfaces();
    }

    /**
     * @return The instance methods the introspected type declares itself that are not private
     */
    public List<DeclaredMethod> getDeclaredMethods() {
        return declaredMethods;
    }

    /**
     * Finds a method the introspected type declares itself.
     *
     * @param name The method name
     * @param parameterTypes The erased parameter types
     * @return The declared method, empty if the type inherits it or has no such method
     */
    public Optional<DeclaredMethod> findDeclaredMethod(String name, Class<?>... parameterTypes) {
        for (DeclaredMethod method : declaredMethods) {
            if (method.matches(name, parameterTypes)) {
                return Optional.of(method);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether the introspected type declares a method itself, rather than inheriting it.
     *
     * @param name The method name
     * @param parameterTypes The erased parameter types
     * @return True if the type declares it
     */
    public boolean declaresMethod(String name, Class<?>... parameterTypes) {
        return findDeclaredMethod(name, parameterTypes).isPresent();
    }

    private Map<Class<?>, TypeEntry> types() {
        Map<Class<?>, TypeEntry> resolved = types;
        if (resolved == null) {
            Map<Class<?>, TypeEntry> map = new LinkedHashMap<>();
            for (TypeRef ref : typeRefs) {
                Class<?> type = ref.type().getType().orElse(null);
                if (type == null) {
                    continue;
                }
                List<Class<?>> interfaces = new ArrayList<>(ref.interfaces().length);
                for (AnnotationClassValue<?> anInterface : ref.interfaces()) {
                    anInterface.getType().ifPresent(interfaces::add);
                }
                AnnotationClassValue<?> superclass = ref.superclass();
                map.put(type, new TypeEntry(
                    superclass == null ? null : superclass.getType().orElse(null),
                    Collections.unmodifiableList(interfaces)));
            }
            resolved = Collections.unmodifiableMap(map);
            types = resolved;
        }
        return resolved;
    }

    @Override
    public String toString() {
        return "BeanTypeHierarchy{" + beanType.getName() + ", types=" + types().keySet() + ", methods=" + declaredMethods + '}';
    }

    private record TypeEntry(@Nullable Class<?> superclass, List<Class<?>> interfaces) {
    }

    /**
     * A type of the hierarchy as the generated introspection names it, resolved when it is first read.
     *
     * @param type The type
     * @param superclass The super class it declares, {@code null} for an interface
     * @param interfaces The interfaces it declares
     */
    @Internal
    @UsedByGeneratedCode
    @SuppressWarnings("java:S6218") // never compared: read once into the hierarchy
    public record TypeRef(AnnotationClassValue<?> type,
                          @Nullable AnnotationClassValue<?> superclass,
                          AnnotationClassValue<?>[] interfaces) {
    }

    /**
     * A method the introspected type declares, described by its name and its erased signature. The types are
     * named as {@link Class#getName()} names them, so a method is matched without loading its parameter types.
     *
     * @param name The method name
     * @param parameterTypeNames The names of the erased parameter types
     * @param returnTypeName The name of the erased return type
     * @since 5.3.0
     */
    @Experimental
    public record DeclaredMethod(String name, List<String> parameterTypeNames, String returnTypeName) {

        /**
         * The method as the generated introspection describes it.
         *
         * @param name The method name
         * @param parameterTypeNames The names of the erased parameter types
         * @param returnTypeName The name of the erased return type
         */
        @Internal
        @UsedByGeneratedCode
        public DeclaredMethod(String name, String[] parameterTypeNames, String returnTypeName) {
            this(name, Arrays.asList(parameterTypeNames), returnTypeName);
        }

        /**
         * @param name The method name
         * @param parameterTypeNames The names of the erased parameter types
         * @param returnTypeName The name of the erased return type
         */
        public DeclaredMethod {
            parameterTypeNames = List.copyOf(parameterTypeNames);
        }

        /**
         * Whether this is the method of the given name and erased parameter types.
         *
         * @param name The method name
         * @param parameterTypes The erased parameter types
         * @return True if it is
         */
        public boolean matches(String name, Class<?>... parameterTypes) {
            if (!this.name.equals(name) || parameterTypes.length != parameterTypeNames.size()) {
                return false;
            }
            for (int i = 0; i < parameterTypes.length; i++) {
                if (!parameterTypes[i].getName().equals(parameterTypeNames.get(i))) {
                    return false;
                }
            }
            return true;
        }
    }
}
