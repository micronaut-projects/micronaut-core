/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.python.processing.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.UUID;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import io.micronaut.python.processing.model.AttributeDef;
import io.micronaut.python.processing.model.DecoratorDef;
import io.micronaut.python.processing.element.TypeAnnotatedClassElement;
import io.micronaut.python.processing.model.TypeRef;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.python.processing.visitor.PythonVisitorContext;

/**
 * Resolves the Python types the processor records ({@link TypeRef}) to Java class elements: built-in
 * names, collections and their type arguments, {@code Annotated} type-use decorators, nullable unions
 * and generic placeholders bound in the enclosing class or function.
 *
 * @since 5.2.0
 */
@Internal
public final class PythonTypeResolver {

    private final PythonVisitorContext visitorContext;

    public PythonTypeResolver(PythonVisitorContext visitorContext) {
        this.visitorContext = visitorContext;
    }

    /**
     * Resolves a structured Python type to a Java class element.
     *
     * @param typeRef       The Python type
     * @param boundGenerics The generic type variables in scope, by name
     * @return The class element, or {@code Object} when the type is unknown
     */
    public ClassElement resolve(TypeRef typeRef, Map<String, ClassElement> boundGenerics) {
        return resolvePythonTypeToJava(typeRef, visitorContext, boundGenerics);
    }

    /**
     * Resolves a simple (non-generic, non-union) Python type name to a Java class element.
     *
     * @param typeName      The Python type name
     * @param boundGenerics The generic type variables in scope, by name
     * @return The class element, or {@code Object} when the type is unknown
     */
    public ClassElement resolve(String typeName, Map<String, ClassElement> boundGenerics) {
        return resolvePythonTypeToJava(typeName, visitorContext, boundGenerics);
    }

    /**
     * Resolves a Python type annotation to a Java ClassElement.
     * Handles primitive types, collections with generics (list[int], dict[str, int]),
     * and supports recursive resolution for nested generics.
     *
     * @param typeRef        The python type def
     * @param visitorContext the visitor context for class element lookup
     * @param boundGenerics  The bound generics
     * @return the resolved ClassElement, or Object ClassElement if resolution fails
     */
    private static ClassElement resolvePythonTypeToJava(TypeRef typeRef, PythonVisitorContext visitorContext, Map<String, ClassElement> boundGenerics) {
        if (typeRef == null) {
            return ClassElement.of(Object.class);
        }

        String name = typeRef.name();
        List<TypeRef> typeArguments = typeRef.typeArguments();
        List<DecoratorDef> typeUseDecorators = typeRef.typeUseDecorators();
        if (typeRef.isUnion()) {
            return withTypeUseDecorators(resolveUnionType(typeRef, visitorContext, boundGenerics), typeUseDecorators, visitorContext);
        }
        if (isAnnotatedType(name) && !typeArguments.isEmpty()) {
            ClassElement baseType = resolvePythonTypeToJava(typeArguments.getFirst(), visitorContext, boundGenerics);
            return withTypeUseAnnotations(baseType, typeArguments.subList(1, typeArguments.size()), visitorContext);
        }
        if (typeArguments.isEmpty()) {
            ClassElement boundGeneric = boundGenerics.get(name);
            if (boundGeneric != null) {
                return withTypeUseDecorators(boundGeneric, typeUseDecorators, visitorContext);
            }
        }
        ClassElement rawType = resolvePythonTypeToJava(name, visitorContext, boundGenerics);
        if (!typeArguments.isEmpty()) {
            ClassElement collectionType = resolveCollectionTypeArguments(rawType, typeArguments, visitorContext, boundGenerics);
            if (collectionType != null) {
                return withTypeUseDecorators(collectionType, typeUseDecorators, visitorContext);
            }
        }
        List<? extends GenericPlaceholderElement> declaredGenericPlaceholders = rawType.getDeclaredGenericPlaceholders();
        if (!typeArguments.isEmpty() && !declaredGenericPlaceholders.isEmpty() && typeArguments.size() == declaredGenericPlaceholders.size()) {
            Map<String, ClassElement> resolvedTypeArguments = new LinkedHashMap<>(declaredGenericPlaceholders.size());
            for (int i = 0; i < declaredGenericPlaceholders.size(); i++) {
                GenericPlaceholderElement placeHolder = declaredGenericPlaceholders.get(i);
                TypeRef typeParameterDef = typeArguments.get(i);
                ClassElement resolvedType = resolveTypeArgument(typeParameterDef, visitorContext, boundGenerics);
                String variableName = placeHolder.getVariableName();
                resolvedTypeArguments.put(variableName, resolvedType);
            }
            return withTypeUseDecorators(rawType.withTypeArguments(resolvedTypeArguments), typeUseDecorators, visitorContext);
        }
        return withTypeUseDecorators(rawType, typeUseDecorators, visitorContext);
    }

    private static boolean isAnnotatedType(String name) {
        return "Annotated".equals(name) || "typing.Annotated".equals(name);
    }

    private static ClassElement withTypeUseAnnotations(
        ClassElement baseType,
        List<TypeRef> annotationTypes,
        PythonVisitorContext visitorContext
    ) {
        if (annotationTypes.isEmpty()) {
            return baseType;
        }
        List<DecoratorDef> decorators = new ArrayList<>(annotationTypes.size());
        for (TypeRef annotationType : annotationTypes) {
            String annotationName = annotationType.name();
            if (annotationName == null || annotationName.isBlank()) {
                continue;
            }
            decorators.add(new DecoratorDef(annotationName, annotationName));
        }
        return withTypeUseDecorators(baseType, decorators, visitorContext);
    }

    private static ClassElement withTypeUseDecorators(
        ClassElement baseType,
        List<DecoratorDef> decorators,
        PythonVisitorContext visitorContext
    ) {
        if (decorators.isEmpty()) {
            return baseType;
        }
        AnnotationMetadata annotationMetadata = visitorContext
            .getAnnotationMetadataBuilder()
            .buildDeclared(new AttributeDef("$typeUse", null, null, null, decorators, null, false, null));
        if (annotationMetadata.isEmpty()) {
            return baseType;
        }
        var metadata = visitorContext.getElementAnnotationMetadataFactory().buildMutable(annotationMetadata);
        return new TypeAnnotatedClassElement(baseType, metadata);
    }

    private static @Nullable ClassElement resolveCollectionTypeArguments(
        ClassElement rawType,
        List<TypeRef> typeArguments,
        PythonVisitorContext visitorContext,
        Map<String, ClassElement> boundGenerics
    ) {
        String rawName = rawType.getName();
        if (Map.class.getName().equals(rawName) && typeArguments.size() >= 2) {
            LinkedHashMap<String, ClassElement> resolvedTypeArguments = new LinkedHashMap<>(2);
            resolvedTypeArguments.put("K", resolveTypeArgument(typeArguments.get(0), visitorContext, boundGenerics));
            resolvedTypeArguments.put("V", resolveTypeArgument(typeArguments.get(1), visitorContext, boundGenerics));
            return parameterizedType(rawType, Map.class, resolvedTypeArguments);
        }
        if (List.class.getName().equals(rawName) && !typeArguments.isEmpty()) {
            return parameterizedType(rawType, List.class, Map.of("E", resolveTypeArgument(typeArguments.get(0), visitorContext, boundGenerics)));
        }
        if (Set.class.getName().equals(rawName) && !typeArguments.isEmpty()) {
            return parameterizedType(rawType, Set.class, Map.of("E", resolveTypeArgument(typeArguments.get(0), visitorContext, boundGenerics)));
        }
        if (Optional.class.getName().equals(rawName) && !typeArguments.isEmpty()) {
            return parameterizedType(rawType, Optional.class, Map.of("T", resolveTypeArgument(typeArguments.get(0), visitorContext, boundGenerics)));
        }
        return null;
    }

    private static ClassElement parameterizedType(
        ClassElement rawType,
        Class<?> rawClass,
        Map<String, ClassElement> typeArguments
    ) {
        try {
            return rawType.withTypeArguments(typeArguments);
        } catch (UnsupportedOperationException e) {
            return ClassElement.of(rawClass, rawType.getAnnotationMetadata(), typeArguments);
        }
    }

    private static ClassElement resolveTypeArgument(
        TypeRef typeParameterDef,
        PythonVisitorContext visitorContext,
        Map<String, ClassElement> boundGenerics
    ) {
        ClassElement resolvedType = resolvePythonTypeToJava(typeParameterDef, visitorContext, boundGenerics);
        if (resolvedType.isPrimitive()) {
            ClassElement boxedClassElement = boxPrimitiveTypeIfNeeded(resolvedType, visitorContext);
            AnnotationMetadata annotationMetadata = resolvedType.getTypeAnnotationMetadata();
            if (annotationMetadata.isEmpty()) {
                return boxedClassElement;
            }
            if (annotationMetadata instanceof ElementAnnotationMetadata elementAnnotationMetadata) {
                return new TypeAnnotatedClassElement(boxedClassElement, elementAnnotationMetadata);
            }
            var metadata = visitorContext.getElementAnnotationMetadataFactory().buildMutable(annotationMetadata);
            return new TypeAnnotatedClassElement(boxedClassElement, metadata);
        }
        return resolvedType;
    }

    /**
     * Resolves a simple (non-generic, non-union) Python type name to a Java ClassElement. Structured
     * types go through {@link #resolvePythonTypeToJava(TypeRef, PythonVisitorContext, Map)}.
     *
     * @param typeAnnotation the Python type name (e.g., "int", "str", "bool", "float", "uuid.UUID")
     * @param visitorContext the visitor context for class element lookup
     * @param boundGenerics  The bound generic types
     * @return the resolved ClassElement, or Object ClassElement if resolution fails
     */
    private static ClassElement resolvePythonTypeToJava(String typeAnnotation, PythonVisitorContext visitorContext, Map<String, ClassElement> boundGenerics) {
        if (typeAnnotation == null || typeAnnotation.isBlank()) {
            return visitorContext.getClassElement(Object.class).orElse(ClassElement.of(Object.class));
        }
        typeAnnotation = typeAnnotation.trim();
        if (typeAnnotation.indexOf('[') > -1 || typeAnnotation.indexOf('|') > -1) {
            // Generics and unions arrive structured as TypeRef from the processor; a raw source string
            // with either only reaches here through the unparsed-annotation fallback.
            return visitorContext.getClassElement(Object.class).orElse(ClassElement.of(Object.class));
        }

        // Try to map Python primitive types to Java primitives
        return switch (typeAnnotation) {
            case "object", "typing.Any", "Any" ->
                visitorContext.getClassElement(Object.class).orElse(ClassElement.of(Object.class));
            case "int" -> PrimitiveElement.INT;
            case "float" -> PrimitiveElement.DOUBLE;
            case "bool" -> PrimitiveElement.BOOLEAN;
            case "None" -> PrimitiveElement.VOID;
            case "bytes", "bytearray" -> PrimitiveElement.BYTE.toArray();
            case "str" ->
                visitorContext.getClassElement(String.class).orElse(ClassElement.of(String.class));
            case "date", "datetime.date" ->
                visitorContext.getClassElement(LocalDate.class).orElse(ClassElement.of(LocalDate.class));
            case "time", "datetime.time" ->
                visitorContext.getClassElement(LocalTime.class).orElse(ClassElement.of(LocalTime.class));
            case "datetime", "datetime.datetime" ->
                visitorContext.getClassElement(LocalDateTime.class).orElse(ClassElement.of(LocalDateTime.class));
            case "timedelta", "datetime.timedelta" ->
                visitorContext.getClassElement(Duration.class).orElse(ClassElement.of(Duration.class));
            case "tzinfo", "timezone", "datetime.tzinfo", "datetime.timezone" ->
                visitorContext.getClassElement(ZoneOffset.class).orElse(ClassElement.of(ZoneOffset.class));
            case "UUID", "uuid.UUID" ->
                visitorContext.getClassElement(UUID.class).orElse(ClassElement.of(UUID.class));
            case "dict", "typing.Dict" ->
                visitorContext.getClassElement(Map.class).orElse(ClassElement.of(Map.class));
            case "list", "typing.List" ->
                visitorContext.getClassElement(List.class).orElse(ClassElement.of(List.class));
            case "set", "typing.Set", "frozenset", "typing.FrozenSet" ->
                visitorContext.getClassElement(Set.class).orElse(ClassElement.of(Set.class));
            case "typing.Optional" ->
                visitorContext.getClassElement(Optional.class).orElse(ClassElement.of(Optional.class));
            default -> {
                String finalTypeAnnotation = typeAnnotation;
                // Fall back to visitor context lookup
                yield resolveClassElement(finalTypeAnnotation, visitorContext).orElseGet(() -> {
                    ClassElement classElement = boundGenerics.get(finalTypeAnnotation);
                    return Objects.requireNonNullElseGet(classElement, () -> ClassElement.of(Object.class));
                });
            }
        };
    }

    private static Optional<ClassElement> resolveClassElement(String typeName, PythonVisitorContext visitorContext) {
        Optional<ClassElement> classElement = visitorContext.getClassElement(typeName);
        if (classElement.isPresent()) {
            return classElement;
        }
        String candidate = typeName;
        int dotIndex = candidate.lastIndexOf('.');
        while (dotIndex > 0) {
            candidate = candidate.substring(0, dotIndex) + "$" + candidate.substring(dotIndex + 1);
            classElement = visitorContext.getClassElement(candidate);
            if (classElement.isPresent()) {
                return classElement;
            }
            dotIndex = candidate.lastIndexOf('.', dotIndex - 1);
        }
        return Optional.empty();
    }

    /**
     * A union with exactly one member besides {@code None} is that member, boxed and marked nullable;
     * any other union has no Java counterpart and resolves to {@code Object}.
     */
    private static ClassElement resolveUnionType(TypeRef union, PythonVisitorContext visitorContext, Map<String, ClassElement> boundGenerics) {
        List<TypeRef> members = union.nonNoneMembers();
        if (members.size() != 1 || !union.isNullableUnion()) {
            return visitorContext.getClassElement(Object.class).orElse(ClassElement.of(Object.class));
        }
        ClassElement resolvedType = resolvePythonTypeToJava(members.getFirst(), visitorContext, boundGenerics);
        ClassElement boxedType = boxPrimitiveTypeIfNeeded(resolvedType, visitorContext);
        AnnotationMetadata annotationMetadata = visitorContext
            .getAnnotationMetadataBuilder()
            .buildDeclared(new AttributeDef("$typeUse", union.toString(), union, null, List.of(), null, false, null));
        if (annotationMetadata.isEmpty()) {
            return boxedType;
        }
        var metadata = visitorContext.getElementAnnotationMetadataFactory().buildMutable(annotationMetadata);
        return new TypeAnnotatedClassElement(boxedType, metadata);
    }

    /**
     * Utility method to box primitive types for use in generics.
     * Java generics require boxed types, so this converts primitives to their boxed equivalents.
     */
    private static ClassElement boxPrimitiveTypeIfNeeded(ClassElement elementType, PythonVisitorContext visitorContext) {
        if (elementType.isPrimitive()) {
            String primitiveName = elementType.getName();
            return switch (primitiveName) {
                case "int" ->
                    visitorContext.getClassElement(Integer.class).orElse(ClassElement.of(Integer.class));
                case "boolean" ->
                    visitorContext.getClassElement(Boolean.class).orElse(ClassElement.of(Boolean.class));
                case "double" ->
                    visitorContext.getClassElement(Double.class).orElse(ClassElement.of(Double.class));
                case "float" ->
                    visitorContext.getClassElement(Float.class).orElse(ClassElement.of(Float.class));
                case "long" ->
                    visitorContext.getClassElement(Long.class).orElse(ClassElement.of(Long.class));
                case "short" ->
                    visitorContext.getClassElement(Short.class).orElse(ClassElement.of(Short.class));
                case "byte" ->
                    visitorContext.getClassElement(Byte.class).orElse(ClassElement.of(Byte.class));
                case "char" ->
                    visitorContext.getClassElement(Character.class).orElse(ClassElement.of(Character.class));
                case "void" ->
                    visitorContext.getClassElement(Void.class).orElse(ClassElement.of(Void.class));
                default -> elementType;
            };
        }
        return elementType;
    }

}
