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
package io.micronaut.inject.reflection;

import io.micronaut.context.AnnotationReflectionUtils;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.ReflectionAnnotationMetadataBuilder;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What a method hierarchy declares: the method as a caller names it, what its declaring type itself declares,
 * the declarations it overrides or implements, and the three merged into one view.
 *
 * <p>A specification that describes a method — the constraint metadata of Jakarta Validation, the resource
 * metadata of JAX-RS — reads a method together with the methods it overrides, and has rules about which level
 * of the hierarchy may declare what. Answering those rules needs the levels apart, not merged: this type
 * resolves them.</p>
 *
 * <p>The hierarchy is read from the bean introspections of the super types, so it is as complete as the
 * introspections are. A {@link ReflectiveIntrospection} tells which annotations a type itself declares, and its
 * declarations are marked {@link Declaration#exact() exact}; a generated introspection reports the metadata of
 * a method with the annotations of the methods it overrides already merged in, so its declarations are not.</p>
 *
 * @param local              The method as the caller names it
 * @param declared           What the declaring type itself declares, {@code local} when that is not known apart
 * @param inherited          The declarations the method overrides or implements, the nearest first
 * @param annotationMetadata The annotations of every level merged, the local one winning
 * @param arguments          The parameters of every level merged, the local one winning
 * @param returnArgument     The return value of every level merged, the local one winning
 * @author Denis Stepanov
 * @since 5.2
 */
@Experimental
public record MethodHierarchy(Declaration local,
                              Declaration declared,
                              List<Declaration> inherited,
                              AnnotationMetadata annotationMetadata,
                              Argument<?>[] arguments,
                              Argument<?> returnArgument) {

    /**
     * Resolves the hierarchy of a method named by a {@link Method}.
     *
     * @param introspector The introspector of the types of the hierarchy
     * @param method       The method
     * @return The hierarchy
     */
    public static MethodHierarchy resolve(BeanIntrospector introspector, Method method) {
        return resolve(introspector, method.getDeclaringClass(), method.getName(), method.getParameterTypes());
    }

    /**
     * Resolves the hierarchy of a method named by the type reading it, which is the type declaring it or one
     * inheriting it.
     *
     * @param introspector   The introspector of the types of the hierarchy
     * @param type           The type reading the method
     * @param name           The method name
     * @param parameterTypes The parameter types
     * @return The hierarchy
     * @throws IllegalArgumentException When the type has no such method
     */
    public static MethodHierarchy resolve(BeanIntrospector introspector,
                                          Class<?> type,
                                          String name,
                                          Class<?>... parameterTypes) {
        Declaration local = declaredBy(introspector, type, name, parameterTypes)
            .or(() -> findMethod(type, name, parameterTypes).map(Declaration::of))
            .orElseThrow(() -> new IllegalArgumentException(
                "No method " + name + Arrays.toString(parameterTypes) + " on type " + type.getName()));
        return resolve(introspector, local, name);
    }

    /**
     * Resolves the hierarchy of a method already read as a declaration, the walk starting at the type
     * declaring it.
     *
     * @param introspector The introspector of the types of the hierarchy
     * @param local        The method as the caller names it
     * @param name         The method name
     * @return The hierarchy
     */
    public static MethodHierarchy resolve(BeanIntrospector introspector, Declaration local, String name) {
        Class<?>[] parameterTypes = Argument.toClassArray(local.arguments());
        Declaration declared = declaredBy(introspector, local.declaringType(), name, parameterTypes)
            .filter(Declaration::exact)
            .orElse(local);
        List<Declaration> inherited = inherited(introspector, local.declaringType(), name, parameterTypes);
        if (inherited.isEmpty()) {
            return new MethodHierarchy(local, declared, inherited, local.annotationMetadata(), local.arguments(), local.returnArgument());
        }
        // the farthest declaration first, the local one last: it wins where the same annotation is repeated
        List<Declaration> levels = new ArrayList<>(inherited);
        Collections.reverse(levels);
        levels.add(local);
        Argument<?>[] arguments = new Argument[local.arguments().length];
        for (int i = 0; i < arguments.length; i++) {
            int index = i;
            arguments[i] = mergeArgument(levels.stream().map(level -> level.arguments()[index]).toList());
        }
        return new MethodHierarchy(local,
            declared,
            inherited,
            mergeMetadata(levels.stream().map(Declaration::annotationMetadata).toList()),
            arguments,
            mergeArgument(levels.stream().map(Declaration::returnArgument).toList()));
    }

    /**
     * The declaration of a method by a type itself, read from the introspection of that type.
     *
     * <p>A {@link ReflectiveIntrospection} answers whether the type declares the method, and its declaration
     * is {@link Declaration#exact() exact}. A generated introspection is asked for the bean method of that
     * name declared by the type; its metadata merges the annotations of the methods it overrides, so the
     * declaration it yields is not exact. Once a generated introspection reports the annotations a method
     * declares apart from the ones it inherits, that branch answers exactly too, and the declaring type
     * filter it applies here becomes redundant rather than wrong.</p>
     *
     * @param introspector   The introspector
     * @param type           The type
     * @param name           The method name
     * @param parameterTypes The parameter types
     * @return The declaration, empty when the type has no introspection or the introspection does not
     * describe the method
     */
    @SuppressWarnings("unchecked")
    public static Optional<Declaration> declaredBy(BeanIntrospector introspector,
                                                   Class<?> type,
                                                   String name,
                                                   Class<?>... parameterTypes) {
        BeanIntrospection<Object> introspection = introspector.findIntrospection((Class<Object>) type).orElse(null);
        if (introspection == null) {
            return Optional.empty();
        }
        if (introspection instanceof ReflectiveIntrospection<Object> reflective) {
            return reflective.findDeclaredMethod(name, parameterTypes).map(method -> Declaration.of(method, true));
        }
        return introspection.getBeanMethods().stream()
            .filter(method -> method.getName().equals(name)
                && method.getDeclaringType() == type
                && Arrays.equals(Argument.toClassArray(method.getArguments()), parameterTypes))
            .findFirst()
            .map(method -> Declaration.of(method, false));
    }

    /**
     * Merges the annotations of the levels of an argument, its type arguments included, the last level winning.
     *
     * @param levels The levels, the local one last
     * @return The merged argument
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Argument<?> mergeArgument(List<Argument<?>> levels) {
        Argument<?> local = levels.get(levels.size() - 1);
        Argument<?>[] localTypeParameters = local.getTypeParameters();
        Argument<?>[] typeParameters = new Argument[localTypeParameters.length];
        for (int i = 0; i < typeParameters.length; i++) {
            int index = i;
            typeParameters[i] = mergeArgument(levels.stream()
                .filter(level -> level.getTypeParameters().length == localTypeParameters.length)
                .map(level -> level.getTypeParameters()[index])
                .toList());
        }
        return Argument.of((Class) local.getType(),
            local.getName(),
            mergeMetadata(levels.stream().map(Argument::getAnnotationMetadata).toList()),
            typeParameters);
    }

    /**
     * Merges the levels of metadata of a hierarchy into one, every annotation of it declared.
     *
     * @param levels The levels, the one to win last
     * @return The merged metadata
     */
    public static AnnotationMetadata mergeMetadata(List<AnnotationMetadata> levels) {
        List<AnnotationMetadata> present = levels.stream().filter(level -> !level.isEmpty()).toList();
        if (present.isEmpty()) {
            return AnnotationMetadata.EMPTY_METADATA;
        }
        if (present.size() == 1) {
            return present.get(0);
        }
        // a hierarchy reads the levels as they are: the generated metadata of a type is shared, copying it into
        // a mutable metadata would share and then alter its annotation values
        return new AnnotationMetadataHierarchy(true, present.toArray(AnnotationMetadata[]::new));
    }

    /**
     * Whether the method is declared in parallel branches of the hierarchy. Each declaration is read from the
     * introspection of the type declaring it, so a type that merely inherits the method does not count as a
     * declaration of its own.
     *
     * @return Whether more than one type of the hierarchy declares the method
     */
    public boolean parallel() {
        return inherited.size() > 1;
    }

    /**
     * The declarations the method overrides or implements: the ones of the super classes, then of all the
     * interfaces, each interface visited once.
     */
    private static List<Declaration> inherited(BeanIntrospector introspector, Class<?> declaringType, String name, Class<?>[] parameterTypes) {
        List<Declaration> declarations = new ArrayList<>();
        Set<Class<?>> visitedInterfaces = new HashSet<>();
        for (Class<?> current = declaringType.getSuperclass(); current != null && current != Object.class; current = current.getSuperclass()) {
            declaredBy(introspector, current, name, parameterTypes).ifPresent(declarations::add);
            collectInterfaceDeclarations(introspector, current, name, parameterTypes, visitedInterfaces, declarations);
        }
        collectInterfaceDeclarations(introspector, declaringType, name, parameterTypes, visitedInterfaces, declarations);
        return List.copyOf(declarations);
    }

    private static void collectInterfaceDeclarations(BeanIntrospector introspector,
                                                     Class<?> type,
                                                     String name,
                                                     Class<?>[] parameterTypes,
                                                     Set<Class<?>> visitedInterfaces,
                                                     List<Declaration> declarations) {
        for (Class<?> interfaceType : type.getInterfaces()) {
            if (visitedInterfaces.add(interfaceType)) {
                declaredBy(introspector, interfaceType, name, parameterTypes).ifPresent(declarations::add);
                collectInterfaceDeclarations(introspector, interfaceType, name, parameterTypes, visitedInterfaces, declarations);
            }
        }
    }

    /**
     * The method a type reads under a name, whether the type declares it or inherits it from a super class or
     * an interface, of any visibility.
     */
    private static Optional<Method> findMethod(Class<?> type, String name, Class<?>[] parameterTypes) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            Optional<Method> declared = declaredMethod(current, name, parameterTypes);
            if (declared.isPresent()) {
                return declared;
            }
        }
        Set<Class<?>> visitedInterfaces = new HashSet<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            Optional<Method> declared = interfaceMethod(current, name, parameterTypes, visitedInterfaces);
            if (declared.isPresent()) {
                return declared;
            }
        }
        return Optional.empty();
    }

    private static Optional<Method> interfaceMethod(Class<?> type, String name, Class<?>[] parameterTypes, Set<Class<?>> visitedInterfaces) {
        for (Class<?> interfaceType : type.getInterfaces()) {
            if (visitedInterfaces.add(interfaceType)) {
                Optional<Method> declared = declaredMethod(interfaceType, name, parameterTypes)
                    .or(() -> interfaceMethod(interfaceType, name, parameterTypes, visitedInterfaces));
                if (declared.isPresent()) {
                    return declared;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Method> declaredMethod(Class<?> type, String name, Class<?>[] parameterTypes) {
        try {
            return Optional.of(type.getDeclaredMethod(name, parameterTypes));
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof MethodHierarchy other
            && local.equals(other.local)
            && declared.equals(other.declared)
            && inherited.equals(other.inherited)
            && annotationMetadata.equals(other.annotationMetadata)
            && Arrays.equals(arguments, other.arguments)
            && returnArgument.equals(other.returnArgument);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(local, declared, inherited, annotationMetadata, returnArgument) + Arrays.hashCode(arguments);
    }

    @Override
    public String toString() {
        return "MethodHierarchy{local=" + local + ", inherited=" + inherited + "}";
    }

    /**
     * One declaration of a method by one type of a hierarchy.
     *
     * @param declaringType      The type declaring it
     * @param annotationMetadata The annotations of the method, without the ones of its declaring type
     * @param arguments          The parameters
     * @param returnArgument     The return value
     * @param exact              Whether the annotations are the ones of this declaration only: a generated
     *                           introspection merges the annotations of the overridden methods into them
     */
    public record Declaration(Class<?> declaringType,
                              AnnotationMetadata annotationMetadata,
                              Argument<?>[] arguments,
                              Argument<?> returnArgument,
                              boolean exact) {

        /**
         * The declaration an executable method reports. The metadata of an executable method merges the
         * annotations of the methods it overrides, so the declaration is not {@link #exact()}.
         *
         * @param method The method
         * @return The declaration
         */
        public static Declaration of(ExecutableMethod<?, ?> method) {
            return new Declaration(method.getDeclaringType(),
                declaredOf(method.getAnnotationMetadata()),
                method.getArguments(),
                returnArgumentOf(method.getReturnType()),
                false);
        }

        /**
         * The declaration a bean method reports.
         *
         * @param method The method
         * @param exact  Whether the introspection reporting it tells the annotations of this declaration apart
         *               from the ones of the methods it overrides
         * @return The declaration
         */
        public static Declaration of(BeanMethod<?, ?> method, boolean exact) {
            return new Declaration(method.getDeclaringType(),
                declaredOf(method.getAnnotationMetadata()),
                method.getArguments(),
                returnArgumentOf(method.getReturnType()),
                exact);
        }

        /**
         * The declaration read from a {@link Method} itself. Java reflection does not inherit the annotations
         * of a method, so the declaration is {@link #exact()}.
         *
         * @param method The method
         * @return The declaration
         */
        public static Declaration of(Method method) {
            return new Declaration(method.getDeclaringClass(),
                ReflectionAnnotationMetadataBuilder.build(method),
                AnnotationReflectionUtils.argumentsOf(method),
                AnnotationReflectionUtils.returnArgumentOf(method),
                true);
        }

        /**
         * The annotations declared on an executable, without the ones of its class: the executable methods of
         * beans carry both. A metadata that is not a hierarchy is returned as is, {@code getDeclaredMetadata()}
         * would drop the repeated annotations.
         *
         * @param annotationMetadata The metadata
         * @return The metadata of the executable alone
         */
        public static AnnotationMetadata declaredOf(AnnotationMetadata annotationMetadata) {
            if (annotationMetadata instanceof AnnotationMetadataHierarchy hierarchy) {
                AnnotationMetadata declared = hierarchy.getDeclaredMetadata();
                return declared instanceof AnnotationMetadataHierarchy
                    ? new AnnotationMetadataHierarchy(hierarchy.getRootMetadata(), declared.getDeclaredMetadata())
                    : declared;
            }
            return annotationMetadata;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static Argument<?> returnArgumentOf(ReturnType<?> returnType) {
            return Argument.of((Class) returnType.getType(),
                declaredOf(returnType.asArgument().getAnnotationMetadata()),
                returnType.getTypeParameters());
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Declaration other
                && declaringType == other.declaringType
                && exact == other.exact
                && annotationMetadata.equals(other.annotationMetadata)
                && Arrays.equals(arguments, other.arguments)
                && returnArgument.equals(other.returnArgument);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(declaringType, annotationMetadata, returnArgument, exact) + Arrays.hashCode(arguments);
        }

        @Override
        public String toString() {
            return declaringType.getName() + (exact ? " (declared)" : "");
        }
    }
}
