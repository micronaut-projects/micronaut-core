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

import io.micronaut.core.annotation.Experimental;
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

import io.micronaut.annotation.processing.visitor.JavaVisitorContext;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import io.micronaut.python.processing.visitor.AttributeDef;
import io.micronaut.python.processing.visitor.ClassDef;
import io.micronaut.python.processing.visitor.PythonClassElement;
import io.micronaut.python.processing.visitor.DecoratorDef;
import io.micronaut.python.processing.visitor.TypeAnnotatedClassElement;
import io.micronaut.python.processing.visitor.TypeRef;
import org.graalvm.polyglot.Value;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.python.processing.visitor.PythonVisitorContext;

/**
 * Utility class for GraalPy integration, providing type conversion and resolution utilities
 * for Python AST processing within Micronaut.
 *
 * @author Micronaut Team
 * @since 5.2.0
 */
@Experimental
public final class GraalPyUtil {
    private static final Set<String> JAVA_KEYWORDS = Set.of(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
        "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
        "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
        "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while"
    );

    /**
     * Utility method to convert GraalPy Value objects to Java types.
     * This extracts the common type conversion logic used for both annotations and attribute values.
     *
     * @param value          the GraalPy Value to convert
     * @param visitorContext The visitor context
     * @return the converted Java object, or the original value if conversion is not possible
     */
    public static Object convertValueToJava(Value value, VisitorContext visitorContext) {
        if (value == null || value.isNull()) {
            return null;
        } else if (value.isHostObject()) {
            return value.asHostObject();
        } else if (value.isBoolean()) {
            return value.asBoolean();
        } else if (value.isNumber()) {
            if (value.fitsInByte()) {
                return value.asByte();
            } else if (value.fitsInShort()) {
                return value.asShort();
            } else if (value.fitsInInt()) {
                return value.asInt();
            } else if (value.fitsInLong()) {
                return value.asLong();
            } else if (value.fitsInFloat()) {
                return value.asFloat();
            } else if (value.fitsInDouble()) {
                return value.asDouble();
            } else {
                return value.asString();
            }
        } else if (value.isString()) {
            // Handle single character strings -> char conversion
            String strValue = value.asString();
            if (strValue.isEmpty()) {
                // ignore empty strings
                return null;
            }
            if (strValue.length() == 1) {
                return strValue.charAt(0);
            }
            return strValue;
        } else if (value.isMetaObject()) {
            // Handle Python class references
            try {
                if (value.canInvokeMember("__name__")) {
                    Value nameValue = value.invokeMember("__name__");
                    String className = nameValue.asString();
                    Object classReference = toClassReference(className, visitorContext);
                    return Objects.requireNonNullElse(classReference, value);
                }
            } catch (Exception e) {
                // Fall back to original value
                return value;
            }
        } else if (value.hasIterator()) {
            // Handle iterable values (like Python lists and arrays) -> typed arrays
            try {
                // Try array access first (works for both arrays and lists in some cases)
                long size = -1;
                try {
                    size = value.getArraySize();
                } catch (Exception e) {
                    // Not an array, try to get size another way
                    if (value.canInvokeMember("__len__")) {
                        Value length = value.invokeMember("__len__");
                        size = length.asLong();
                    }
                }

                if (size > 0) {
                    // Use array element access
                    Value firstElement = value.getArrayElement(0);
                    if (firstElement != null) {
                        // Use first element to determine array type
                        Object convertedFirst = convertValueToJava(firstElement, visitorContext);
                        Class<?> componentType = getComponentType(convertedFirst);

                        // Convert all elements
                        java.util.List<Object> elements = new java.util.ArrayList<>();
                        elements.add(convertedFirst);

                        for (long i = 1; i < size; i++) {
                            Value nextElement = value.getArrayElement(i);
                            if (nextElement != null) {
                                elements.add(convertValueToJava(nextElement, visitorContext));
                            }
                        }

                        // Create typed array
                        return createTypedArray(componentType, elements);
                    }
                }
                // Empty iterable
                return null;
            } catch (Exception e) {
                // Fall back to original value if array conversion fails
                return value;
            }
        }
        return value;
    }

    private static @Nullable Object toClassReference(String className, VisitorContext visitorContext) {
        Class<?> builtInType = builtInClassReference(className);
        if (builtInType != null) {
            return builtInType;
        }
        if (visitorContext instanceof PythonVisitorContext pythonVisitorContext) {
            String pythonClassName = resolvePythonClassName(className, pythonVisitorContext.getProcessingEnvironment());
            if (pythonClassName != null) {
                return new AnnotationClassValue<>(pythonClassName);
            }
            JavaVisitorContext javaVisitorContext = pythonVisitorContext.getJavaVisitorContext();
            return javaVisitorContext == null ? null : javaVisitorContext.getClassElement(className).orElse(null);
        }
        return visitorContext.getClassElement(className).orElse(null);
    }

    private static @Nullable Class<?> builtInClassReference(String className) {
        return switch (className) {
            case "str" -> String.class;
            case "int" -> Integer.class;
            case "float" -> Double.class;
            case "bool" -> Boolean.class;
            case "boolean" -> boolean.class;
            case "byte" -> byte.class;
            case "char" -> char.class;
            case "double" -> double.class;
            case "long" -> long.class;
            case "short" -> short.class;
            case "void" -> void.class;
            default -> null;
        };
    }

    /**
     * Get the component type for array creation based on the first element.
     */
    private static Class<?> getComponentType(Object firstElement) {
        if (firstElement instanceof Boolean) {
            return boolean.class;
        } else if (firstElement instanceof Byte) {
            return byte.class;
        } else if (firstElement instanceof Character) {
            return char.class;
        } else if (firstElement instanceof Short) {
            return short.class;
        } else if (firstElement instanceof Integer) {
            return int.class;
        } else if (firstElement instanceof Long) {
            return long.class;
        } else if (firstElement instanceof Float) {
            return float.class;
        } else if (firstElement instanceof Double) {
            return double.class;
        } else if (firstElement instanceof String) {
            return String.class;
        } else if (firstElement instanceof Class) {
            return Class.class;
        } else {
            return Object.class;
        }
    }

    /**
     * Create a typed array from the component type and element list.
     */
    private static Object createTypedArray(Class<?> componentType, java.util.List<Object> elements) {
        if (componentType == boolean.class) {
            boolean[] array = new boolean[elements.size()];
            for (int i = 0; i < elements.size(); i++) {
                array[i] = (Boolean) elements.get(i);
            }
            return array;
        } else if (componentType == byte.class) {
            byte[] array = new byte[elements.size()];
            for (int i = 0; i < elements.size(); i++) {
                array[i] = ((Number) elements.get(i)).byteValue();
            }
            return array;
        } else if (componentType == char.class) {
            char[] array = new char[elements.size()];
            for (int i = 0; i < elements.size(); i++) {
                array[i] = (Character) elements.get(i);
            }
            return array;
        } else if (componentType == short.class) {
            short[] array = new short[elements.size()];
            for (int i = 0; i < elements.size(); i++) {
                array[i] = ((Number) elements.get(i)).shortValue();
            }
            return array;
        } else if (componentType == int.class) {
            int[] array = new int[elements.size()];
            for (int i = 0; i < elements.size(); i++) {
                array[i] = ((Number) elements.get(i)).intValue();
            }
            return array;
        } else if (componentType == long.class) {
            long[] array = new long[elements.size()];
            for (int i = 0; i < elements.size(); i++) {
                array[i] = ((Number) elements.get(i)).longValue();
            }
            return array;
        } else if (componentType == float.class) {
            float[] array = new float[elements.size()];
            for (int i = 0; i < elements.size(); i++) {
                array[i] = ((Number) elements.get(i)).floatValue();
            }
            return array;
        } else if (componentType == double.class) {
            double[] array = new double[elements.size()];
            for (int i = 0; i < elements.size(); i++) {
                array[i] = ((Number) elements.get(i)).doubleValue();
            }
            return array;
        } else if (componentType == String.class) {
            return elements.toArray(new String[0]);
        } else if (componentType == Class.class) {
            return elements.toArray(new Class[0]);
        } else {
            return elements.toArray();
        }
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
    public static ClassElement resolvePythonTypeToJava(TypeRef typeRef, PythonVisitorContext visitorContext, Map<String, ClassElement> boundGenerics) {
        if (typeRef == null) {
            return ClassElement.of(Object.class);
        }

        String name = typeRef.name();
        List<TypeRef> typeArguments = typeRef.typeArguments();
        List<DecoratorDef> typeUseDecorators = typeRef.typeUseDecorators();
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
        if (!typeArguments.isEmpty() && declaredGenericPlaceholders != null && !declaredGenericPlaceholders.isEmpty() && typeArguments.size() == declaredGenericPlaceholders.size()) {
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
     * Resolves a Python type annotation to a Java ClassElement.
     * Handles primitive types, collections with generics (list[int], dict[str, int]),
     * and supports recursive resolution for nested generics.
     *
     * @param typeAnnotation the Python type annotation string (e.g., "int", "str", "bool", "float", "list[int]", "dict[str, int]")
     * @param visitorContext the visitor context for class element lookup
     * @param boundGenerics  The bound generic types
     * @return the resolved ClassElement, or Object ClassElement if resolution fails
     */
    public static ClassElement resolvePythonTypeToJava(String typeAnnotation, PythonVisitorContext visitorContext, Map<String, ClassElement> boundGenerics) {
        if (typeAnnotation == null || typeAnnotation.isBlank()) {
            return visitorContext.getClassElement(Object.class).orElse(ClassElement.of(Object.class));
        }
        typeAnnotation = typeAnnotation.trim();

        // Handle Annotated types by extracting the base type
        if (typeAnnotation.startsWith("Annotated[")) {
            int bracketStart = typeAnnotation.indexOf('[');
            int firstComma = typeAnnotation.indexOf(',', bracketStart);
            if (firstComma != -1) {
                String baseType = typeAnnotation.substring(bracketStart + 1, firstComma).trim();
                return resolvePythonTypeToJava(baseType, visitorContext, boundGenerics);
            }
        }

        ClassElement nullableUnionType = resolveNullableUnionType(typeAnnotation, visitorContext, boundGenerics);
        if (nullableUnionType != null) {
            return nullableUnionType;
        }

        // Handle generic types like list[int], dict[str, int], etc.
        if (typeAnnotation.contains("[")) {
            return resolveGenericPythonType(typeAnnotation, visitorContext);
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

    private static @Nullable ClassElement resolveNullableUnionType(
        String typeAnnotation,
        PythonVisitorContext visitorContext,
        Map<String, ClassElement> boundGenerics
    ) {
        if (!typeAnnotation.contains("|")) {
            return null;
        }
        List<String> unionTypes = parseUnionTypes(typeAnnotation);
        List<String> nonNoneTypes = new ArrayList<>(unionTypes.size());
        boolean nullable = false;
        for (String unionType : unionTypes) {
            String type = unionType.trim();
            if ("None".equals(type)) {
                nullable = true;
            } else if (!type.isEmpty()) {
                nonNoneTypes.add(type);
            }
        }
        if (!nullable || nonNoneTypes.size() != 1) {
            return null;
        }
        ClassElement resolvedType = resolvePythonTypeToJava(nonNoneTypes.get(0), visitorContext, boundGenerics);
        ClassElement boxedType = boxPrimitiveTypeIfNeeded(resolvedType, visitorContext);
        AnnotationMetadata annotationMetadata = visitorContext
            .getAnnotationMetadataBuilder()
            .buildDeclared(new AttributeDef("$typeUse", typeAnnotation, new TypeRef(typeAnnotation), null, List.of(), null, false, null));
        if (annotationMetadata.isEmpty()) {
            return boxedType;
        }
        var metadata = visitorContext.getElementAnnotationMetadataFactory().buildMutable(annotationMetadata);
        return new TypeAnnotatedClassElement(boxedType, metadata);
    }

    private static List<String> parseUnionTypes(String typeAnnotation) {
        List<String> types = new ArrayList<>();
        int start = 0;
        int bracketCount = 0;
        for (int i = 0; i < typeAnnotation.length(); i++) {
            char c = typeAnnotation.charAt(i);
            if (c == '[') {
                bracketCount++;
            } else if (c == ']') {
                bracketCount--;
            } else if (c == '|' && bracketCount == 0) {
                String type = typeAnnotation.substring(start, i).trim();
                if (!type.isEmpty()) {
                    types.add(type);
                }
                start = i + 1;
            }
        }
        String lastType = typeAnnotation.substring(start).trim();
        if (!lastType.isEmpty()) {
            types.add(lastType);
        }
        return types;
    }

    /**
     * Resolves generic Python types like list[int], dict[str, int], etc.
     * Supports recursive resolution for nested generics.
     */
    private static ClassElement resolveGenericPythonType(String typeAnnotation, PythonVisitorContext visitorContext) {
        // Parse the generic type structure
        GenericTypeInfo genericInfo = parseGenericType(typeAnnotation);
        if (genericInfo == null) {
            return visitorContext.getClassElement(Object.class).orElse(ClassElement.of(Object.class));
        }

        // Resolve based on the base type
        return switch (genericInfo.baseType) {
            case "list", "List", "typing.List" -> {
                // list[T] -> List<T>
                ClassElement listElement = visitorContext.getClassElement(java.util.List.class)
                    .orElse(ClassElement.of(java.util.List.class));

                if (!genericInfo.typeParameters.isEmpty()) {
                    ClassElement elementType = resolvePythonTypeToJava(genericInfo.typeParameters.get(0), visitorContext, Map.of());
                    // For generics, use boxed types instead of primitives
                    elementType = boxPrimitiveTypeIfNeeded(elementType, visitorContext);
                    // Create parameterized type List<ElementType>
                    yield parameterizedType(listElement, List.class, java.util.Map.of("E", elementType));
                }
                yield listElement;
            }
            case "dict", "Dict", "typing.Dict" -> {
                // dict[K, V] -> Map<K, V>
                ClassElement mapElement = visitorContext.getClassElement(java.util.Map.class)
                    .orElse(ClassElement.of(java.util.Map.class));

                if (genericInfo.typeParameters.size() >= 2) {
                    ClassElement keyType = resolvePythonTypeToJava(genericInfo.typeParameters.get(0), visitorContext, Map.of());
                    ClassElement valueType = resolvePythonTypeToJava(genericInfo.typeParameters.get(1), visitorContext, Map.of());

                    // For generics, use boxed types instead of primitives
                    keyType = boxPrimitiveTypeIfNeeded(keyType, visitorContext);
                    valueType = boxPrimitiveTypeIfNeeded(valueType, visitorContext);

                    // Create parameterized type Map<KeyType, ValueType>
                    LinkedHashMap<String, ClassElement> map = new LinkedHashMap<>();
                    map.put("K", keyType);
                    map.put("V", valueType);
                    yield parameterizedType(mapElement, Map.class, map);
                }
                yield mapElement;
            }
            case "set", "Set", "typing.Set" -> {
                // set[T] -> Set<T>
                ClassElement setElement = visitorContext.getClassElement(java.util.Set.class)
                    .orElse(ClassElement.of(java.util.Set.class));

                if (!genericInfo.typeParameters.isEmpty()) {
                    ClassElement elementType = resolvePythonTypeToJava(genericInfo.typeParameters.get(0), visitorContext, Map.of());
                    // For generics, use boxed types instead of primitives
                    elementType = boxPrimitiveTypeIfNeeded(elementType, visitorContext);
                    yield parameterizedType(setElement, Set.class, java.util.Map.of("E", elementType));
                }
                yield setElement;
            }
            case "Optional", "typing.Optional" -> {
                // Optional[T] -> Optional<T>
                ClassElement optionalElement = visitorContext.getClassElement(java.util.Optional.class)
                    .orElse(ClassElement.of(java.util.Optional.class));

                if (!genericInfo.typeParameters.isEmpty()) {
                    ClassElement elementType = resolvePythonTypeToJava(genericInfo.typeParameters.get(0), visitorContext, Map.of());
                    // For generics, use boxed types instead of primitives
                    elementType = boxPrimitiveTypeIfNeeded(elementType, visitorContext);
                    yield parameterizedType(optionalElement, Optional.class, java.util.Map.of("T", elementType));
                }
                yield optionalElement;
            }
            default -> {
                // Unknown generic type, fall back to Object
                ClassElement classElement = visitorContext.getClassElement(genericInfo.baseType).orElse(null);
                if (classElement != null) {
                    List<? extends GenericPlaceholderElement> declaredGenericPlaceholders = classElement.getDeclaredGenericPlaceholders();
                    if (!declaredGenericPlaceholders.isEmpty()
                        && declaredGenericPlaceholders.size() == genericInfo.typeParameters.size()) {
                        // Generic remapping is handled by the concrete collection branches above.
                        yield visitorContext.getClassElement(Object.class).orElse(ClassElement.of(Object.class));
                    }
                }
                yield visitorContext.getClassElement(Object.class).orElse(ClassElement.of(Object.class));
            }
        };
    }

    /**
     * Parses a generic type annotation like "list[int]" or "dict[str, int]".
     */
    private static GenericTypeInfo parseGenericType(String typeAnnotation) {
        int bracketStart = typeAnnotation.indexOf('[');
        if (bracketStart == -1) {
            return null;
        }

        String baseType = typeAnnotation.substring(0, bracketStart).trim();
        String paramsStr = typeAnnotation.substring(bracketStart + 1);

        // Find matching closing bracket (handle nested brackets)
        int bracketCount = 0;
        int endIndex = -1;
        for (int i = 0; i < paramsStr.length(); i++) {
            char c = paramsStr.charAt(i);
            if (c == '[') {
                bracketCount++;
            } else if (c == ']') {
                bracketCount--;
                if (bracketCount == -1) {
                    endIndex = i;
                    break;
                }
            }
        }

        if (endIndex == -1) {
            return null; // Malformed
        }

        String typeParamsStr = paramsStr.substring(0, endIndex);
        java.util.List<String> typeParameters = parseTypeParameters(typeParamsStr);

        return new GenericTypeInfo(baseType, typeParameters);
    }

    /**
     * Parses type parameters separated by commas, handling nested generics.
     */
    private static java.util.List<String> parseTypeParameters(String typeParamsStr) {
        java.util.List<String> parameters = new java.util.ArrayList<>();
        int start = 0;
        int bracketCount = 0;

        for (int i = 0; i < typeParamsStr.length(); i++) {
            char c = typeParamsStr.charAt(i);
            if (c == '[') {
                bracketCount++;
            } else if (c == ']') {
                bracketCount--;
            } else if (c == ',' && bracketCount == 0) {
                // Found a parameter separator
                String param = typeParamsStr.substring(start, i).trim();
                if (!param.isEmpty()) {
                    parameters.add(param);
                }
                start = i + 1;
            }
        }

        // Add the last parameter
        String lastParam = typeParamsStr.substring(start).trim();
        if (!lastParam.isEmpty()) {
            parameters.add(lastParam);
        }

        return parameters;
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

    /**
     * Converts GraalPy Value objects to Java types based on the provided ClassElement type information.
     * This method handles type-specific conversion for annotation members and other typed values.
     *
     * @param value          the GraalPy Value to convert
     * @param classElement   the ClassElement representing the target Java type
     * @param visitorContext The visitor context
     * @return the converted Java object, or the original value if conversion is not possible
     */
    public static Object convertValueToJava(Value value, ClassElement classElement, PythonVisitorContext visitorContext) {
        Objects.requireNonNull(classElement, "ClassElement cannot be null");

        if (value == null || value.isNull()) {
            return null;
        }
        Object hostObject = hostObject(value);
        if (hostObject != null) {
            if (hostObject instanceof AnnotationValue<?> annotationValue) {
                return annotationValue;
            }
            if (hostObject instanceof DecoratorDef decoratorDef) {
                return toAnnotationValue(decoratorDef, classElement, visitorContext);
            }
        }

        // Handle primitive types
        if (isAnnotationPrimitive(classElement)
            && !classElement.isArray()) {
            if (classElement.getName().equals(String.class.getName())) {
                if (value.isString()) {
                    return value.asString();
                } else {
                    return value.as(Object.class).toString();
                }
            } else if (classElement.equals(PrimitiveElement.BOOLEAN)) {
                return value.asBoolean();
            } else if (classElement == PrimitiveElement.BYTE) {
                if (value.fitsInByte()) {
                    return value.asByte();
                } else {
                    return ((Number) convertValueToJava(value, visitorContext)).byteValue();
                }
            } else if (classElement.equals(PrimitiveElement.CHAR)) {
                String str = value.asString();
                return !str.isEmpty() ? str.charAt(0) : '\0';
            } else if (classElement.equals(PrimitiveElement.DOUBLE)) {
                return value.asDouble();
            } else if (classElement.equals(PrimitiveElement.FLOAT)) {
                if (value.fitsInFloat()) {
                    return value.asFloat();
                } else {
                    return ((Number) convertValueToJava(value, visitorContext)).floatValue();
                }
            } else if (classElement.equals(PrimitiveElement.INT)) {
                if (value.fitsInInt()) {
                    return value.asInt();
                } else {
                    return ((Number) convertValueToJava(value, visitorContext)).intValue();
                }
            } else if (classElement.equals(PrimitiveElement.LONG)) {
                if (value.fitsInLong()) {
                    return value.asLong();
                } else {
                    return ((Number) convertValueToJava(value, visitorContext)).longValue();
                }
            } else if (classElement.equals(PrimitiveElement.SHORT)) {
                if (value.fitsInShort()) {
                    return value.asShort();
                } else {
                    return ((Number) convertValueToJava(value, visitorContext)).shortValue();
                }
            }
        }

        // Handle arrays
        if (classElement.isArray()) {
            ClassElement componentType = classElement.fromArray();
            if (value.hasIterator()) {
                long size = value.getArraySize();
                if (size > 0) {
                    // Convert array elements using the component type
                    if (componentType.equals(PrimitiveElement.BOOLEAN)) {
                        boolean[] array = new boolean[(int) size];
                        for (int i = 0; i < size; i++) {
                            Value element = value.getArrayElement(i);
                            if (element != null) {
                                array[i] = element.asBoolean();
                            }
                        }
                        return array;
                    } else if (componentType.equals(PrimitiveElement.BYTE)) {
                        byte[] array = new byte[(int) size];
                        for (int i = 0; i < size; i++) {
                            Value element = value.getArrayElement(i);
                            if (element != null) {
                                array[i] = element.asByte();
                            }
                        }
                        return array;
                    } else if (componentType.equals(PrimitiveElement.CHAR)) {
                        char[] array = new char[(int) size];
                        for (int i = 0; i < size; i++) {
                            Value element = value.getArrayElement(i);
                            if (element != null) {
                                String str = element.asString();
                                array[i] = !str.isEmpty() ? str.charAt(0) : '\0';
                            }
                        }
                        return array;
                    } else if (componentType.equals(PrimitiveElement.DOUBLE)) {
                        double[] array = new double[(int) size];
                        for (int i = 0; i < size; i++) {
                            Value element = value.getArrayElement(i);
                            if (element != null) {
                                array[i] = element.asDouble();
                            }
                        }
                        return array;
                    } else if (componentType.equals(PrimitiveElement.FLOAT)) {
                        float[] array = new float[(int) size];
                        for (int i = 0; i < size; i++) {
                            Value element = value.getArrayElement(i);
                            if (element != null) {
                                array[i] = (float) element.asDouble();
                            }
                        }
                        return array;
                    } else if (componentType.equals(PrimitiveElement.INT)) {
                        int[] array = new int[(int) size];
                        for (int i = 0; i < size; i++) {
                            Value element = value.getArrayElement(i);
                            if (element != null) {
                                array[i] = element.asInt();
                            }
                        }
                        return array;
                    } else if (componentType.equals(PrimitiveElement.LONG)) {
                        long[] array = new long[(int) size];
                        for (int i = 0; i < size; i++) {
                            Value element = value.getArrayElement(i);
                            if (element != null) {
                                array[i] = element.asLong();
                            }
                        }
                        return array;
                    } else if (componentType.equals(PrimitiveElement.SHORT)) {
                        short[] array = new short[(int) size];
                        for (int i = 0; i < size; i++) {
                            Value element = value.getArrayElement(i);
                            if (element != null) {
                                array[i] = element.asShort();
                            }
                        }
                        return array;
                    } else if ("java.lang.String".equals(componentType.getName())) {
                        String[] array = new String[(int) size];
                        for (int i = 0; i < size; i++) {
                            Value element = value.getArrayElement(i);
                            if (element != null) {
                                array[i] = element.asString();
                            }
                        }
                        return array;
                    } else if ("java.lang.Class".equals(componentType.getName())) {
                        // Handle Class arrays
                        List<AnnotationClassValue<?>> list = new ArrayList<>();
                        for (int i = 0; i < size; i++) {
                            Value element = value.getArrayElement(i);
                            if (element != null) {
                                AnnotationClassValue<?> classValue = toClassValue(element, visitorContext);
                                if (classValue != null) {
                                    list.add(classValue);
                                }
                            }
                        }
                        return list.toArray(AnnotationClassValue[]::new);
                    } else if (componentType.isAssignable(java.lang.annotation.Annotation.class)) {
                        List<AnnotationValue<?>> list = new ArrayList<>();
                        for (int i = 0; i < size; i++) {
                            Value element = value.getArrayElement(i);
                            if (element != null) {
                                AnnotationValue<?> annotationValue = toAnnotationValue(element, componentType, visitorContext);
                                if (annotationValue != null) {
                                    list.add(annotationValue);
                                }
                            }
                        }
                        return list.toArray(AnnotationValue[]::new);
                    } else {
                        // Handle object arrays
                        Object[] array = new Object[(int) size];
                        for (int i = 0; i < size; i++) {
                            Value element = value.getArrayElement(i);
                            if (element != null) {
                                array[i] = convertValueToJava(element, componentType, visitorContext);
                            }
                        }
                        return array;
                    }
                }
            }
        }

        // Handle java.lang.Class type
        if ("java.lang.Class".equals(classElement.getName())) {
            return toClassValue(value, visitorContext);
        }

        // Handle annotations - check if value has members that look like annotation members
        if (value.hasMembers()) {
            try {
                // Try to convert to AnnotationValue
                Map<String, Object> annotationValues = new java.util.HashMap<>();
                for (String memberName : value.getMemberKeys()) {
                    Value memberValue = value.getMember(memberName);
                    if (memberValue != null) {
                        // For annotation members, we don't have type information, so use the existing conversion
                        annotationValues.put(memberName, convertValueToJava(memberValue, visitorContext));
                    }
                }
                if (!annotationValues.isEmpty()) {
                    return new io.micronaut.core.annotation.AnnotationValue(classElement.getName(), annotationValues);
                }
            } catch (Exception e) {
                // Fall back to original conversion logic
            }
        }

        // Fall back to the original conversion logic
        return convertValueToJava(value, visitorContext);
    }

    private static AnnotationValue<?> toAnnotationValue(DecoratorDef decoratorDef,
                                                        ClassElement fallbackAnnotationType,
                                                        PythonVisitorContext visitorContext) {
        ClassElement annotationType = resolveAnnotationType(decoratorDef, fallbackAnnotationType, visitorContext);
        Map<CharSequence, Object> annotationValues = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : decoratorDef.members().entrySet()) {
            String memberName = annotationMemberName(entry.getKey());
            Object memberValue = entry.getValue();
            if (memberValue instanceof Value value) {
                ClassElement memberType = resolveAnnotationMemberType(annotationType, memberName);
                annotationValues.put(
                    memberName,
                    memberType == null
                        ? convertValueToJava(value, visitorContext)
                        : convertValueToJava(value, memberType, visitorContext)
                );
            } else {
                annotationValues.put(memberName, memberValue);
            }
        }
        return new AnnotationValue<>(decoratorDef.annotationName(), annotationValues);
    }

    private static @Nullable AnnotationValue<?> toAnnotationValue(Value value,
                                                                  ClassElement fallbackAnnotationType,
                                                                  PythonVisitorContext visitorContext) {
        if (value == null || value.isNull()) {
            return null;
        }
        Object hostObject = hostObject(value);
        if (hostObject instanceof AnnotationValue<?> annotationValue) {
            return annotationValue;
        }
        if (hostObject instanceof DecoratorDef decoratorDef) {
            return toAnnotationValue(decoratorDef, fallbackAnnotationType, visitorContext);
        }
        Object converted = convertValueToJava(value, visitorContext);
        if (converted instanceof AnnotationValue<?> annotationValue) {
            return annotationValue;
        }
        if (converted instanceof DecoratorDef decoratorDef) {
            return toAnnotationValue(decoratorDef, fallbackAnnotationType, visitorContext);
        }
        return null;
    }

    private static ClassElement resolveAnnotationType(DecoratorDef decoratorDef,
                                                      ClassElement fallbackAnnotationType,
                                                      PythonVisitorContext visitorContext) {
        JavaVisitorContext javaVisitorContext = visitorContext.getJavaVisitorContext();
        if (javaVisitorContext == null) {
            return fallbackAnnotationType;
        }
        return javaVisitorContext
            .getClassElement(decoratorDef.annotationName())
            .orElse(fallbackAnnotationType);
    }

    private static @Nullable Object hostObject(Value value) {
        if (value.isHostObject()) {
            return value.asHostObject();
        }
        try {
            Object object = value.as(Object.class);
            if (object instanceof AnnotationValue<?> || object instanceof DecoratorDef) {
                return object;
            }
        } catch (Exception ignored) {
            // Not a host-backed object.
        }
        return null;
    }

    private static String annotationMemberName(Object memberName) {
        if (memberName instanceof Number number) {
            int index = number.intValue();
            return index == 0 ? "value" : "arg" + index;
        }
        return memberName.toString();
    }

    private static @Nullable ClassElement resolveAnnotationMemberType(ClassElement annotationType, String memberName) {
        MethodElement annotationMember = annotationType
            .getEnclosedElement(ElementQuery.ALL_METHODS.onlyInstance().named(memberName))
            .orElse(null);
        return annotationMember == null ? null : annotationMember.getReturnType();
    }

    private static boolean isAnnotationPrimitive(ClassElement classElement) {
        // should enums be primitives?
        return classElement.isPrimitive() || classElement.getName().equals(String.class.getName());
    }

    private static @Nullable AnnotationClassValue<?> toClassValue(Value value, PythonVisitorContext visitorContext) {
        String typeName = value.asString();
        Class<?> classReference = builtInClassReference(typeName);
        if (classReference == null) {
            PythonProcessingEnvironment environment = visitorContext.getProcessingEnvironment();
            String pythonClassName = resolvePythonClassName(typeName, environment);
            if (pythonClassName != null) {
                return new AnnotationClassValue<>(pythonClassName);
            }
            JavaVisitorContext javaVisitorContext = visitorContext.getJavaVisitorContext();
            ClassElement classElement = javaVisitorContext == null ? null : javaVisitorContext.getClassElement(typeName).orElse(null);
            if (classElement != null) {
                return new AnnotationClassValue<>(classElement.getCanonicalName());
            } else if (isValidClassName(typeName) || isValidClassDescriptor(typeName)) {
                return new AnnotationClassValue<>(typeName);
            } else {
                return null;
            }
        } else {
            return new AnnotationClassValue<>(classReference);
        }
    }

    private static @Nullable String resolvePythonClassName(String typeName, PythonProcessingEnvironment environment) {
        Map<String, ClassDef> classes = environment.environment().classes();
        ClassDef classDef = classes.get(typeName);
        if (classDef != null) {
            return classDef.qualifiedName();
        }
        String defaultPackage = PythonClassElement.PYTHON_DEFAULT_PACKAGE + '.';
        if (!typeName.contains(".")) {
            classDef = classes.get(defaultPackage + typeName);
            if (classDef != null) {
                return classDef.qualifiedName();
            }
        }
        for (ClassDef candidate : classes.values()) {
            if (candidate.qualifiedName().equals(typeName)) {
                return typeName;
            }
            if (!typeName.contains(".") && candidate.name().equals(typeName)) {
                return candidate.qualifiedName();
            }
        }
        return null;
    }

    public static boolean isValidClassName(String className) {
        if (className == null || className.isEmpty()) {
            return false;
        }
        String[] parts = className.split("\\.");
        for (String part : parts) {
            if (part.isEmpty()) {
                return false;
            }
            if (!Character.isJavaIdentifierStart(part.charAt(0))) {
                return false;
            }
            for (int i = 1; i < part.length(); i++) {
                if (!Character.isJavaIdentifierPart(part.charAt(i))) {
                    return false;
                }
            }
            if (isJavaKeyword(part)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidClassDescriptor(String className) {
        if (className == null || className.isEmpty() || className.charAt(0) != '[') {
            return false;
        }
        int componentStart = 0;
        while (componentStart < className.length() && className.charAt(componentStart) == '[') {
            componentStart++;
        }
        if (componentStart >= className.length()) {
            return false;
        }
        char componentType = className.charAt(componentStart);
        if ("BCDFIJSZ".indexOf(componentType) > -1) {
            return componentStart == className.length() - 1;
        }
        if (componentType == 'L' && className.endsWith(";")) {
            String componentName = className.substring(componentStart + 1, className.length() - 1)
                .replace('/', '.');
            return isValidClassName(componentName);
        }
        return false;
    }

    private static boolean isJavaKeyword(String s) {
        return JAVA_KEYWORDS.contains(s);
    }

    /**
     * Parses a Python docstring to extract the main description.
     * Removes opening/closing quotes and stops at structured sections like Args:, Returns:, etc.
     *
     * @param docstring the raw Python docstring
     * @return the parsed main description, or empty string if docstring is null/empty
     */
    public static String parsePythonDocstring(String docstring) {
        if (docstring == null || docstring.trim().isEmpty()) {
            return "";
        }

        String[] lines = docstring.split("\\n");
        StringBuilder result = new StringBuilder();

        // Skip the first line if it's just the opening quotes or empty
        int startIndex = 0;
        if (lines.length > 0 && (lines[0].trim().isEmpty() || lines[0].trim().startsWith("\"\"\"") || lines[0].trim().startsWith("'''"))) {
            startIndex = 1;
        }

        // Process lines until we hit structured sections
        for (int i = startIndex; i < lines.length; i++) {
            String line = lines[i];

            // Stop at common section headers (case-insensitive)
            String trimmed = line.trim().toLowerCase();
            if (trimmed.startsWith("args:") || trimmed.startsWith("arguments:") ||
                trimmed.startsWith("parameters:") || trimmed.startsWith("param:") ||
                trimmed.startsWith("returns:") || trimmed.startsWith("return:") ||
                trimmed.startsWith("raises:") || trimmed.startsWith("exceptions:") ||
                trimmed.startsWith("note:") || trimmed.startsWith("notes:") ||
                trimmed.startsWith("example:") || trimmed.startsWith("examples:") ||
                trimmed.startsWith("see also:")) {
                break;
            }

            // Stop at closing docstring markers
            if (line.trim().endsWith("\"\"\"") || line.trim().endsWith("'''")) {
                line = line.replaceAll("\"\"\"$", "").replaceAll("'''$", "");
            }

            result.append(line);
            if (i < lines.length - 1) {
                result.append("\n");
            }
        }

        return result.toString().trim();
    }

    /**
     * Simple data class to hold generic type information.
     */
    private static class GenericTypeInfo {
        final String baseType;
        final java.util.List<String> typeParameters;

        GenericTypeInfo(String baseType, java.util.List<String> typeParameters) {
            this.baseType = baseType;
            this.typeParameters = typeParameters;
        }
    }
}
