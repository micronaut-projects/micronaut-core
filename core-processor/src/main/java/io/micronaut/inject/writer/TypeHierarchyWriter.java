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
package io.micronaut.inject.writer;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.GeneratedTypeHierarchy;
import io.micronaut.core.type.TypeHierarchies;
import io.micronaut.core.type.TypeHierarchy;
import io.micronaut.inject.annotation.AnnotationMetadataGenUtils;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.PackageElement;
import io.micronaut.inject.ast.WildcardElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.TypeDef;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Writes the record of the generic hierarchy of a type, a {@link TypeHierarchy} found at run time with
 * {@link TypeHierarchies}. See {@link VisitorContext#importTypeHierarchy(ClassElement, Element...)}.
 *
 * <p>A record is a class with a constructor and nothing else, which passes the type, its variables, its direct super
 * types and {@link ClassElement#getAllSuperTypes() all its super types}, built with the argument factories from class
 * constants, to {@link GeneratedTypeHierarchy}. It is written into the package of the first originating element, or
 * into the package of the type when the type, a bound or a super type of it is a non-public class of that package,
 * so that the class constants of the record resolve. A super type that is not public and of another package, which no
 * record could name, is left out of the record; a type whose own declaration names such a class is refused.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class TypeHierarchyWriter {

    private static final Constructor<?> SUPER_CONSTRUCTOR = ReflectionUtils.getRequiredInternalConstructor(
        GeneratedTypeHierarchy.class, Class.class, Argument[].class, Argument.class, Argument[].class, Argument[].class);
    // the exceptions of javac and of KSP for a class written twice, by name: neither compiler is a dependency here
    private static final Set<String> ALREADY_WRITTEN = Set.of("javax.annotation.processing.FilerException", "kotlin.io.FileAlreadyExistsException");
    private static final int MAX_DEPTH = 16;

    private TypeHierarchyWriter() {
    }

    /**
     * The name of the record of a type, see {@link TypeHierarchies#recordClassName(String, String)}.
     *
     * @param type                The type
     * @param originatingElements The originating elements, at least one
     * @return The name of the record
     */
    public static String recordClassName(ClassElement type, Element... originatingElements) {
        if (originatingElements == null || originatingElements.length == 0) {
            throw new IllegalArgumentException("The hierarchy of [" + type.getName() + "] cannot be recorded without an originating element");
        }
        return TypeHierarchies.recordClassName(type.getName(), recordPackage(type, originatingElements[0]));
    }

    /**
     * Writes the record of a type. A type recorded several times in a compilation is written once: a compiler that
     * refuses a second write of the same class is taken at its word.
     *
     * @param type                The type
     * @param recordName          The name of the record, from {@link #recordClassName(ClassElement, Element...)}
     * @param context             The visitor context
     * @param originatingElements The originating elements, at least one
     */
    public static void write(ClassElement type, String recordName, VisitorContext context, Element... originatingElements) {
        Element origin = originatingElements[0];
        ClassDef classDef = build(type, recordName, origin);
        // the class first: a compiler that refuses to write a class twice says so here, before the service entry
        try (OutputStream outputStream = context.visitClass(recordName, originatingElements)) {
            outputStream.write(ByteCodeWriterUtils.writeByteCode(classDef, context));
        } catch (IOException e) {
            if (e instanceof FileAlreadyExistsException || ALREADY_WRITTEN.contains(e.getClass().getName())) {
                // recorded before in this compilation, from another origin or in an earlier round: the record is the
                // same, and a compiler that does not refuse the second write has overwritten it with the same bytes
                return;
            }
            throw new ProcessingException(origin, "Failed to write the type hierarchy record of [" + type.getName() + "]: " + e.getMessage(), e);
        }
        context.visitServiceDescriptor(TypeHierarchy.class, recordName, origin);
    }

    private static ClassDef build(ClassElement type, String recordName, Element origin) {
        String recordPackage = recordName.substring(0, Math.max(0, recordName.lastIndexOf('.')));
        ClassTypeDef recordType = ClassTypeDef.of(recordName);
        Map<String, MethodDef> loadTypeMethods = new LinkedHashMap<>();
        Function<String, ExpressionDef> loadClassValueExpressionFn = AnnotationMetadataGenUtils.createLoadClassValueExpressionFn(recordType, loadTypeMethods);
        MutableAnnotationMetadata defaults = new MutableAnnotationMetadata();

        List<ExpressionDef> variables = new ArrayList<>();
        for (GenericPlaceholderElement variable : type.getDeclaredGenericPlaceholders()) {
            if (!isNameableFrom(variable, recordPackage)) {
                throw new ProcessingException(origin, "The hierarchy of [" + type.getName() + "] cannot be recorded: the bounds of its variable "
                    + variable.getVariableName() + " name a type that is not public and not of the package " + recordPackage);
            }
            variables.add(argument(defaults, type, recordType, variable.getVariableName(), variable, loadClassValueExpressionFn));
        }
        ExpressionDef superclass = type.getSuperType()
            .filter(superType -> !type.isInterface() && !superType.getName().equals(Object.class.getName()) && isNameableFrom(superType, recordPackage))
            .map(superType -> argument(defaults, type, recordType, superType.getSimpleName(), superType, loadClassValueExpressionFn))
            .orElseGet(ExpressionDef::nullValue);
        List<ExpressionDef> interfaces = new ArrayList<>();
        for (ClassElement anInterface : type.getInterfaces()) {
            if (isNameableFrom(anInterface, recordPackage)) {
                interfaces.add(argument(defaults, type, recordType, anInterface.getSimpleName(), anInterface, loadClassValueExpressionFn));
            }
        }
        // the closure the compiler walked, the type itself left out: the record is of the type
        List<ExpressionDef> superTypes = new ArrayList<>();
        for (ClassElement superType : type.getAllSuperTypes()) {
            if (!superType.getName().equals(type.getName()) && isNameableFrom(superType, recordPackage)) {
                superTypes.add(argument(defaults, type, recordType, superType.getSimpleName(), superType, loadClassValueExpressionFn));
            }
        }

        ClassDef.ClassDefBuilder builder = ClassDef.builder(recordName)
            .synthetic()
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .superclass(ClassTypeDef.of(GeneratedTypeHierarchy.class))
            .addMethod(MethodDef.constructor()
                .addModifiers(Modifier.PUBLIC)
                .build((aThis, parameters) -> aThis.superRef().invokeSuperConstructor(SUPER_CONSTRUCTOR, List.of(
                    ExpressionDef.constant(TypeDef.erasure(type)),
                    arrayOrNull(variables),
                    superclass,
                    arrayOrNull(interfaces),
                    arrayOrNull(superTypes)
                ))));
        loadTypeMethods.values().forEach(builder::addMethod);
        return builder.build();
    }

    private static ExpressionDef argument(MutableAnnotationMetadata defaults,
                                          ClassElement type,
                                          ClassTypeDef recordType,
                                          String name,
                                          ClassElement argument,
                                          Function<String, ExpressionDef> loadClassValueExpressionFn) {
        // the annotations written on the type use (extends @A Base<@B T>) are kept; those of the declaration of the type are not
        return ArgumentExpUtils.pushCreateArgument(defaults, type, recordType, name, argument,
            argument.getTypeAnnotationMetadata(), argument.getTypeArguments(), loadClassValueExpressionFn);
    }

    private static ExpressionDef arrayOrNull(List<ExpressionDef> arguments) {
        return arguments.isEmpty() ? ExpressionDef.nullValue() : ArgumentExpUtils.TYPE_ARGUMENT_ARRAY.instantiate(arguments);
    }

    /**
     * The package the record is written into: that of the origin, unless the record names a class of the package of
     * the type that is not public, which only a class of that package can name. A type of a package of the JDK that
     * is itself not public has no package a record can be written into.
     */
    private static String recordPackage(ClassElement type, Element origin) {
        String typePackage = type.getPackageName();
        String originPackage = packageOf(origin);
        if (typePackage.equals(originPackage)) {
            return typePackage;
        }
        Set<String> visited = new HashSet<>();
        if (isPublic(type, visited)) {
            if (!namesNonPublicTypeOf(type, typePackage)) {
                return originPackage;
            }
        } else if (typePackage.startsWith("java.")) {
            throw new ProcessingException(origin, "The hierarchy of [" + type.getName() + "] cannot be recorded: the type is not public, and a record cannot be written into a package of the JDK");
        }
        return typePackage;
    }

    private static String packageOf(Element origin) {
        if (origin instanceof ClassElement classElement) {
            return classElement.getPackageName();
        }
        if (origin instanceof MemberElement memberElement) {
            return memberElement.getDeclaringType().getPackageName();
        }
        if (origin instanceof PackageElement packageElement) {
            return packageElement.getName();
        }
        throw new IllegalArgumentException("A type hierarchy cannot be recorded with the originating element [" + origin
            + "]: the first originating element must be a class, a member or a package");
    }

    /**
     * Whether the declaration of a type names a class of the given package that is not public: in the bounds of its
     * variables, in its direct super types or anywhere in its closure.
     */
    private static boolean namesNonPublicTypeOf(ClassElement type, String packageName) {
        Set<String> visited = new HashSet<>();
        for (GenericPlaceholderElement variable : type.getDeclaredGenericPlaceholders()) {
            if (namesNonPublicTypeOf(variable, packageName, visited, 0)) {
                return true;
            }
        }
        for (ClassElement superType : type.getAllSuperTypes()) {
            if (!superType.getName().equals(type.getName()) && namesNonPublicTypeOf(superType, packageName, visited, 0)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether every class a type names, itself, its type arguments and the bounds of its variables and wildcards, can
     * be named from a package: it is public, or of that package.
     */
    private static boolean isNameableFrom(ClassElement element, String packageName) {
        // a non-public class is nameable from its own package only
        return !namesNonPublicTypeMatching(element, aClass -> !aClass.getPackageName().equals(packageName), new HashSet<>(), 0);
    }

    private static boolean namesNonPublicTypeOf(ClassElement element, String packageName, Set<String> visited, int depth) {
        return namesNonPublicTypeMatching(element, aClass -> aClass.getPackageName().equals(packageName), visited, depth);
    }

    private static boolean namesNonPublicTypeMatching(ClassElement element, Predicate<ClassElement> matches, Set<String> visited, int depth) {
        if (depth > MAX_DEPTH) {
            return false;
        }
        if (element instanceof WildcardElement wildcard) {
            return namesNonPublicTypeMatching(wildcard.getUpperBounds(), matches, visited, depth) || namesNonPublicTypeMatching(wildcard.getLowerBounds(), matches, visited, depth);
        }
        if (element instanceof GenericPlaceholderElement placeholder) {
            // the bounds of a variable within its own bounds are not visited again
            return visited.add("<" + placeholder.getVariableName() + ">") && namesNonPublicTypeMatching(placeholder.getBounds(), matches, visited, depth);
        }
        ClassElement type = element;
        while (type.isArray()) {
            type = type.fromArray();
        }
        if (!type.isPrimitive() && !isPublic(type, new HashSet<>()) && matches.test(type)) {
            return true;
        }
        if (!visited.add(type.getName())) {
            return false;
        }
        return namesNonPublicTypeMatching(type.getTypeArguments().values(), matches, visited, depth + 1);
    }

    private static boolean namesNonPublicTypeMatching(Collection<? extends ClassElement> elements, Predicate<ClassElement> matches, Set<String> visited, int depth) {
        for (ClassElement element : elements) {
            if (namesNonPublicTypeMatching(element, matches, visited, depth)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a type can be named from any package: it and every type enclosing it are public.
     */
    private static boolean isPublic(ClassElement type, Set<String> visited) {
        for (ClassElement current = type; current != null && visited.add(current.getName()); current = current.getEnclosingType().orElse(null)) {
            if (!current.isPublic()) {
                return false;
            }
        }
        return true;
    }
}
