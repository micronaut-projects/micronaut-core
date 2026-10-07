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
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The hierarchy of an introspected type, described at compile time when the type is introspected with
 * {@link io.micronaut.core.annotation.Introspected#hierarchy()}: every super class and interface of the type,
 * with the super class and the interfaces each of them declares, and the methods the type itself declares.
 *
 * <p>The types are those the compiler saw: the type itself first, then, depth first, its super class and the
 * super classes and interfaces of that, then its interfaces and theirs, each once. {@link Object} is reported
 * as a super class, it is not described itself. A type whose class cannot be loaded at runtime is left out.</p>
 *
 * <p>The declared methods are the instance methods the type declares that are not private, whether or not the
 * introspection describes them as {@link BeanMethod bean methods}, without the methods it inherits.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class BeanTypeHierarchy {

    /**
     * The annotation the annotation processor records the hierarchy in. It names no class: a processor resolving an
     * annotation by its name finds none and keeps the recorded values as they are.
     */
    @Internal
    public static final String ANNOTATION_NAME = "io.micronaut.core.beans.internal.TypeHierarchy";
    /**
     * The member of {@link #ANNOTATION_NAME} listing the types, each an annotation of {@link #TYPE_NAME}.
     */
    @Internal
    public static final String MEMBER_TYPES = "types";
    /**
     * The member of {@link #ANNOTATION_NAME} listing the declared methods, each an annotation of
     * {@link #METHOD_NAME}.
     */
    @Internal
    public static final String MEMBER_METHODS = "methods";
    /**
     * The annotation describing one type: {@link #MEMBER_TYPE}, {@link #MEMBER_SUPERCLASS} and
     * {@link #MEMBER_INTERFACES}.
     */
    @Internal
    public static final String TYPE_NAME = "io.micronaut.core.beans.internal.TypeHierarchyType";
    /**
     * The annotation describing one declared method: {@link #MEMBER_NAME}, {@link #MEMBER_PARAMETERS} and
     * {@link #MEMBER_RETURN_TYPE}.
     */
    @Internal
    public static final String METHOD_NAME = "io.micronaut.core.beans.internal.TypeHierarchyMethod";
    /** The described type. */
    @Internal
    public static final String MEMBER_TYPE = "type";
    /** The super class of a described type, absent for an interface. */
    @Internal
    public static final String MEMBER_SUPERCLASS = "superclass";
    /** The interfaces a described type declares. */
    @Internal
    public static final String MEMBER_INTERFACES = "interfaces";
    /** The name of a declared method. */
    @Internal
    public static final String MEMBER_NAME = "name";
    /** The erased parameter types of a declared method. */
    @Internal
    public static final String MEMBER_PARAMETERS = "parameters";
    /** The erased return type of a declared method. */
    @Internal
    public static final String MEMBER_RETURN_TYPE = "returnType";

    private final Class<?> beanType;
    private final Map<Class<?>, TypeEntry> types;
    private final List<DeclaredMethod> declaredMethods;

    private BeanTypeHierarchy(Class<?> beanType, Map<Class<?>, TypeEntry> types, List<DeclaredMethod> declaredMethods) {
        this.beanType = beanType;
        this.types = types;
        this.declaredMethods = declaredMethods;
    }

    /**
     * Reads the hierarchy the annotation processor recorded in the annotation metadata of an introspection.
     *
     * @param beanType The introspected type
     * @param metadata The annotation metadata of its introspection
     * @return The hierarchy, or empty if none was recorded
     */
    public static Optional<BeanTypeHierarchy> of(Class<?> beanType, AnnotationMetadata metadata) {
        AnnotationValue<Annotation> recorded = metadata.getAnnotation(ANNOTATION_NAME);
        if (recorded == null) {
            return Optional.empty();
        }
        Map<Class<?>, TypeEntry> types = new LinkedHashMap<>();
        for (AnnotationValue<Annotation> entry : recorded.getAnnotations(MEMBER_TYPES)) {
            Class<?> type = entry.annotationClassValue(MEMBER_TYPE).flatMap(AnnotationClassValue::getType).orElse(null);
            if (type == null) {
                continue;
            }
            Class<?> superclass = entry.annotationClassValue(MEMBER_SUPERCLASS)
                .flatMap(AnnotationClassValue::getType).orElse(null);
            List<Class<?>> interfaces = new ArrayList<>();
            for (AnnotationClassValue<?> anInterface : entry.annotationClassValues(MEMBER_INTERFACES)) {
                anInterface.getType().ifPresent(interfaces::add);
            }
            types.put(type, new TypeEntry(superclass, Collections.unmodifiableList(interfaces)));
        }
        List<DeclaredMethod> methods = new ArrayList<>();
        for (AnnotationValue<Annotation> method : recorded.getAnnotations(MEMBER_METHODS)) {
            methods.add(new DeclaredMethod(
                method.stringValue(MEMBER_NAME).orElse(""),
                Arrays.stream(method.annotationClassValues(MEMBER_PARAMETERS)).map(AnnotationClassValue::getName).toList(),
                method.annotationClassValue(MEMBER_RETURN_TYPE).map(AnnotationClassValue::getName).orElse(void.class.getName())
            ));
        }
        return Optional.of(new BeanTypeHierarchy(beanType, Collections.unmodifiableMap(types), Collections.unmodifiableList(methods)));
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
        return List.copyOf(types.keySet());
    }

    /**
     * @param type A type
     * @return Whether the type is the introspected type or one of its super types
     */
    public boolean contains(Class<?> type) {
        return types.containsKey(type);
    }

    /**
     * The super class a type of the hierarchy declares.
     *
     * @param type The introspected type or one of its super types
     * @return The super class, empty for an interface, for {@link Object} and for a type outside the hierarchy
     */
    public Optional<Class<?>> getSuperclass(Class<?> type) {
        TypeEntry entry = types.get(type);
        return entry == null ? Optional.empty() : Optional.ofNullable(entry.superclass());
    }

    /**
     * The interfaces a type of the hierarchy declares itself, in declaration order.
     *
     * @param type The introspected type or one of its super types
     * @return The interfaces, empty for a type outside the hierarchy
     */
    public List<Class<?>> getInterfaces(Class<?> type) {
        TypeEntry entry = types.get(type);
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

    @Override
    public String toString() {
        return "BeanTypeHierarchy{" + beanType.getName() + ", types=" + types.keySet() + ", methods=" + declaredMethods + '}';
    }

    private record TypeEntry(@Nullable Class<?> superclass, List<Class<?>> interfaces) {
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
