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
package io.micronaut.python.processing;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.python.processing.util.AnnotationNames;
import io.micronaut.python.processing.util.AnnotationScalars;
import io.micronaut.python.processing.util.PythonTypeResolver;
import io.micronaut.python.processing.model.DecoratorDef;
import io.micronaut.python.processing.visitor.PythonVisitorContext;
import io.micronaut.python.processing.model.TypeRef;
import io.micronaut.sourcegen.model.AnnotationDef;
import io.micronaut.sourcegen.model.AnnotationObjectDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Modifier;
import org.jspecify.annotations.Nullable;

/**
 * Generates the Java annotation type ({@code @interface}) that stands for a Python-defined decorator, and
 * converts decorator applications into sourcegen annotation definitions for generated stubs.
 *
 * @since 5.2.0
 */
@Internal
final class PythonAnnotationStubGenerator {

    private PythonAnnotationStubGenerator() {
    }

    static boolean shouldGenerateAnnotationStub(DecoratorDef decoratorDef, PythonVisitorContext context) {
        var javaVisitorContext = context.getJavaVisitorContext();
        return javaVisitorContext == null || javaVisitorContext.getClassElement(decoratorDef.annotationName()).isEmpty();
    }

    static AnnotationObjectDef generateAnnotationStub(DecoratorDef decoratorDef, PythonVisitorContext visitorContext) {
        AnnotationObjectDef.AnnotationObjectDefBuilder builder = AnnotationObjectDef.builder(decoratorDef.annotationName())
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationDef.builder(Retention.class)
                .addMember(AnnotationMetadata.VALUE_MEMBER, RetentionPolicy.RUNTIME)
                .build());

        for (DecoratorDef stereotype : decoratorDef.stereotypes()) {
            if (shouldEmitAnnotationReference(stereotype, visitorContext)) {
                builder.addAnnotation(toAnnotationDef(stereotype, visitorContext));
            }
        }

        Set<String> memberNames = new LinkedHashSet<>();
        memberNames.addAll(decoratorDef.memberTypes().keySet());
        memberNames.addAll(decoratorDef.members().keySet());
        for (String memberName : memberNames) {
            if (isDecoratorTargetMember(memberName, decoratorDef)) {
                continue;
            }
            TypeDef memberType = annotationMemberType(memberName, decoratorDef, visitorContext);
            AnnotationObjectDef.AnnotationMemberDefBuilder memberBuilder =
                AnnotationObjectDef.AnnotationMemberDef.builder(memberName, memberType);
            for (DecoratorDef memberDecorator : decoratorDef.memberDecorators().getOrDefault(memberName, List.of())) {
                if (shouldEmitAnnotationReference(memberDecorator, visitorContext)) {
                    memberBuilder.addAnnotation(toAnnotationDef(memberDecorator, visitorContext));
                }
            }
            ExpressionDef defaultValue = annotationDefaultValue(
                decoratorDef.members().get(memberName),
                memberType,
                annotationMemberElement(memberName, decoratorDef, visitorContext));
            if (defaultValue != null) {
                memberBuilder.withDefault(defaultValue);
            }
            builder.addMember(memberBuilder.build());
        }

        return builder.build();
    }

    private static boolean shouldEmitAnnotationReference(DecoratorDef decoratorDef, PythonVisitorContext visitorContext) {
        String annotationName = decoratorDef.annotationName();
        var javaVisitorContext = visitorContext.getJavaVisitorContext();
        if (javaVisitorContext != null && javaVisitorContext.getClassElement(annotationName).isPresent()) {
            return true;
        }
        return visitorContext.getProcessingEnvironment().environment().decorators().containsKey(annotationName);
    }

    private static boolean isDecoratorTargetMember(String memberName, DecoratorDef decoratorDef) {
        Object memberValue = decoratorDef.members().get(memberName);
        return decoratorDef.memberTypes().size() <= 1
            && decoratorDef.members().size() <= 1
            && isNullAnnotationMemberValue(memberValue)
            && decoratorDef.memberTypes().get(memberName) == null
            && decoratorDef.memberDecorators().getOrDefault(memberName, List.of()).isEmpty()
            && Set.of("func", "cls", "bean").contains(memberName);
    }

    private static boolean isNullAnnotationMemberValue(@Nullable Object value) {
        return value == null;
    }

    /**
     * Resolves the declared type of an annotation member as a {@link ClassElement}, or {@code null} when the decorator
     * parameter carries no type annotation. The element tells apart the kinds of default that need more than a plain
     * constant &mdash; an enum constant becomes a static field reference and a class reference a class literal.
     *
     * @param memberName     The member name
     * @param decoratorDef   The decorator
     * @param visitorContext The visitor context
     * @return The member element, or {@code null} if the member is untyped
     */
    @Nullable
    private static ClassElement annotationMemberElement(String memberName,
                                                        DecoratorDef decoratorDef,
                                                        PythonVisitorContext visitorContext) {
        TypeRef typeRef = decoratorDef.memberTypes().get(memberName);
        if (typeRef == null) {
            return null;
        }
        if (isPythonListType(typeRef.name()) && typeRef.typeArguments().size() == 1) {
            TypeRef componentType = typeRef.typeArguments().getFirst();
            if (isClassLiteralType(componentType)) {
                return null;
            }
            return visitorContext.getTypeResolver().resolve(componentType, Map.of());
        }
        if (isClassLiteralType(typeRef)) {
            return null;
        }
        return visitorContext.getTypeResolver().resolve(typeRef, Map.of());
    }

    private static TypeDef annotationMemberType(String memberName, DecoratorDef decoratorDef, PythonVisitorContext visitorContext) {
        TypeRef typeRef = decoratorDef.memberTypes().get(memberName);
        if (typeRef == null) {
            // No type annotation on the decorator parameter: infer the member type from the default
            // value, which arrives as a polyglot value from the processor.
            Object defaultValue = decoratorDef.members().get(memberName);
            if (defaultValue instanceof Boolean) {
                return TypeDef.Primitive.BOOLEAN;
            }
            if (defaultValue instanceof Integer) {
                return TypeDef.Primitive.INT;
            }
            if (defaultValue instanceof Long) {
                return TypeDef.Primitive.LONG;
            }
            if (defaultValue instanceof Double || defaultValue instanceof Float) {
                return TypeDef.Primitive.DOUBLE;
            }
            return TypeDef.STRING;
        }
        TypeDef annotationArrayType = annotationArrayMemberType(typeRef, visitorContext);
        if (annotationArrayType != null) {
            return annotationArrayType;
        }
        ClassElement classElement = visitorContext.getTypeResolver().resolve(typeRef, Map.of());
        if (classElement.getName().equals(Object.class.getName()) && decoratorDef.members().get(memberName) instanceof String) {
            return TypeDef.STRING;
        }
        return TypeDef.of(classElement);
    }

    private static @Nullable TypeDef annotationArrayMemberType(TypeRef typeRef, PythonVisitorContext visitorContext) {
        if (!isPythonListType(typeRef.name()) || typeRef.typeArguments().size() != 1) {
            return null;
        }
        TypeRef componentType = typeRef.typeArguments().getFirst();
        if (isClassLiteralType(componentType)) {
            return ClassTypeDef.of(Class.class).array();
        }
        ClassElement componentElement = visitorContext.getTypeResolver().resolve(componentType, Map.of());
        return TypeDef.of(componentElement).array();
    }

    private static boolean isPythonListType(String typeName) {
        return PythonTypeResolver.isPythonListType(typeName);
    }

    private static boolean isClassLiteralType(TypeRef typeRef) {
        return PythonTypeResolver.isClassLiteralType(typeRef);
    }

    private static @Nullable ExpressionDef annotationDefaultValue(Object defaultValue,
                                                                  TypeDef memberType,
                                                                  @Nullable ClassElement memberElement) {
        if (defaultValue == null) {
            return null;
        }
        if (defaultValue instanceof String stringValue && isPythonAstDump(stringValue)) {
            // a default expression the Python processor could not convert; better no default than a broken stub
            return null;
        }
        if (memberType instanceof TypeDef.Array arrayType) {
            Object[] elements = arrayElements(defaultValue);
            if (elements.length == 0) {
                return new ExpressionDef.Constant(arrayType, new Object[0]);
            }
            if (arrayType.componentType().equals(TypeDef.STRING)) {
                return ExpressionDef.constant(Arrays.stream(elements).map(String::valueOf).toArray(String[]::new));
            }
            if (memberElement != null && memberElement.isEnum()) {
                // an array of enum constants: each element is a static field reference, not a constant
                return new ExpressionDef.NewArrayInitialized(
                    arrayType,
                    Arrays.stream(elements)
                        .map(element -> ExpressionDef.constant(
                            memberElement,
                            arrayType.componentType(),
                            enumConstantName(element, memberElement)))
                        .toList());
            }
            // each element follows the component type's rules, as the scalar defaults do
            Object[] converted = new Object[elements.length];
            for (int i = 0; i < elements.length; i++) {
                converted[i] = convertDefaultValue(elements[i], arrayType.componentType());
            }
            return ExpressionDef.constant(converted);
        }
        if (memberElement != null && memberElement.isEnum()) {
            // Colour.GREEN, not "GREEN": the member type is the enum, so the default is a static field reference
            return ExpressionDef.constant(memberElement, memberType, enumConstantName(defaultValue, memberElement));
        }
        if (memberType.equals(TypeDef.CLASS) || TypeDef.of(Class.class).equals(memberType)) {
            return ExpressionDef.constant(ClassTypeDef.of(String.valueOf(defaultValue)));
        }
        Object converted = convertDefaultValue(defaultValue, memberType);
        if (memberType instanceof TypeDef.Primitive) {
            return ExpressionDef.primitiveConstant(converted);
        }
        return ExpressionDef.constant(converted);
    }

    /**
     * The simple name of an enum constant. The Python processor may report it qualified by the enum type, in which
     * case only the trailing segment names the constant.
     *
     * @param value         The reported default
     * @param memberElement The enum type
     * @return The constant name
     */
    private static String enumConstantName(Object value, ClassElement memberElement) {
        String name = String.valueOf(value);
        int lastDot = name.lastIndexOf('.');
        if (lastDot < 0) {
            return name;
        }
        String qualifier = name.substring(0, lastDot);
        if (qualifier.equals(memberElement.getName()) || qualifier.equals(memberElement.getSimpleName())) {
            return name.substring(lastDot + 1);
        }
        return name;
    }

    /**
     * Whether the value is a Python AST repr rather than a converted value. {@code extract_arg_defaults} converts
     * defaults through the same path as usage site values, so this should not happen; it stays as a guard so that an
     * expression shape the converter cannot read drops the default instead of emitting an uncompilable stub.
     *
     * @param value The reported default
     * @return Whether the value is an AST repr
     */
    private static boolean isPythonAstDump(String value) {
        return value.startsWith("Name(") || value.startsWith("Attribute(") || value.startsWith("Call(");
    }

    private static Object[] arrayElements(Object defaultValue) {
        if (defaultValue instanceof Collection<?> collection) {
            return collection.toArray();
        }
        if (defaultValue.getClass().isArray()) {
            return (Object[]) defaultValue;
        }
        return new Object[] {defaultValue};
    }

    /**
     * Narrows a literal default from the Python processor to the annotation member's declared type.
     */
    private static Object convertDefaultValue(Object value, TypeDef memberType) {
        if (memberType.equals(TypeDef.STRING)) {
            return String.valueOf(value);
        }
        if (value instanceof Number number) {
            // the same rules as annotation values: integral and in range, no silent narrowing
            if (memberType.equals(TypeDef.Primitive.INT)) {
                return (int) AnnotationScalars.integral(number, Integer.MIN_VALUE, Integer.MAX_VALUE, "int");
            }
            if (memberType.equals(TypeDef.Primitive.LONG)) {
                return AnnotationScalars.integral(number, Long.MIN_VALUE, Long.MAX_VALUE, "long");
            }
            if (memberType.equals(TypeDef.Primitive.SHORT)) {
                return (short) AnnotationScalars.integral(number, Short.MIN_VALUE, Short.MAX_VALUE, "short");
            }
            if (memberType.equals(TypeDef.Primitive.BYTE)) {
                return (byte) AnnotationScalars.integral(number, Byte.MIN_VALUE, Byte.MAX_VALUE, "byte");
            }
            if (memberType.equals(TypeDef.Primitive.FLOAT)) {
                return AnnotationScalars.floating(number);
            }
            if (memberType.equals(TypeDef.Primitive.DOUBLE)) {
                return number.doubleValue();
            }
        }
        return value;
    }

    private static AnnotationDef toAnnotationDef(DecoratorDef decoratorDef, PythonVisitorContext visitorContext) {
        Map<CharSequence, Object> members = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : decoratorDef.members().entrySet()) {
            String memberName = AnnotationNames.memberName(entry.getKey());
            Object value = convertAnnotationMemberValue(decoratorDef.annotationName(), memberName, entry.getValue(), visitorContext);
            if (value != null) {
                members.put(memberName, value);
            }
        }
        if (members.values().stream().anyMatch(PythonAnnotationStubGenerator::requiresLiteralAnnotationValue)) {
            return buildAnnotationDef(decoratorDef.annotationName(), members);
        }
        AnnotationValue<?> annotationValue = new AnnotationValue<>(decoratorDef.annotationName(), members);
        try {
            return AnnotationDef.of(annotationValue, visitorContext);
        } catch (RuntimeException e) {
            return buildAnnotationDef(decoratorDef.annotationName(), members);
        }
    }

    static AnnotationDef buildAnnotationDef(String annotationName, Map<CharSequence, Object> members) {
        AnnotationDef.AnnotationDefBuilder builder = AnnotationDef.builder(ClassTypeDef.of(annotationName));
        members.forEach((memberName, value) -> addAnnotationDefMember(builder, memberName.toString(), value));
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private static void addAnnotationDefMember(AnnotationDef.AnnotationDefBuilder builder, String memberName, Object value) {
        Object normalized = normalizeAnnotationDefMember(value);
        if (normalized instanceof Collection<?> collection) {
            builder.addMember(memberName, (Collection<Object>) collection);
        } else {
            builder.addMember(memberName, normalized);
        }
    }

    private static Object normalizeAnnotationDefMember(Object value) {
        if (value instanceof Object[] array) {
            if (array.length == 0) {
                return EmptyAnnotationArray.INSTANCE;
            }
            List<Object> values = new ArrayList<>(array.length);
            for (Object element : array) {
                values.add(normalizeAnnotationDefMember(element));
            }
            return values;
        }
        if (value instanceof Collection<?> collection) {
            if (collection.isEmpty()) {
                return EmptyAnnotationArray.INSTANCE;
            }
            List<Object> values = new ArrayList<>(collection.size());
            for (Object element : collection) {
                values.add(normalizeAnnotationDefMember(element));
            }
            return values;
        }
        return value;
    }

    private static boolean containsSourcegenAnnotationValue(Object value) {
        if (value instanceof VariableDef || value instanceof ClassTypeDef) {
            return true;
        }
        if (value instanceof Object[] array) {
            for (Object element : array) {
                if (containsSourcegenAnnotationValue(element)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof Collection<?> collection) {
            for (Object element : collection) {
                if (containsSourcegenAnnotationValue(element)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean requiresLiteralAnnotationValue(Object value) {
        return containsSourcegenAnnotationValue(value) || isEmptyArrayOrCollection(value);
    }

    private static boolean isEmptyArrayOrCollection(Object value) {
        if (value instanceof Object[] array) {
            return array.length == 0;
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        return false;
    }

    private static @Nullable Object convertAnnotationMemberValue(
        String annotationName,
        String memberName,
        Object value,
        PythonVisitorContext visitorContext
    ) {
        ClassElement memberType = resolveAnnotationMemberType(annotationName, memberName, visitorContext);
        if (memberType != null) {
            return convertAnnotationMemberValue(value, memberType, visitorContext);
        }
        if (value instanceof DecoratorDef nestedDecorator) {
            return toAnnotationDef(nestedDecorator, visitorContext);
        }
        return value;
    }

    private static Object convertAnnotationMemberValue(
        Object value,
        ClassElement memberType,
        PythonVisitorContext visitorContext
    ) {
        if (memberType.isArray()) {
            return convertAnnotationArrayMemberValue(value, memberType.fromArray(), visitorContext);
        }
        if (AnnotationNames.isEnumMember(memberType)) {
            return enumConstantValue(value, memberType);
        }
        if (Class.class.getName().equals(memberType.getName())) {
            return annotationClassLiteralValue(value, visitorContext);
        }
        if (value instanceof DecoratorDef nestedDecorator) {
            return toAnnotationDef(nestedDecorator, visitorContext);
        }
        return AnnotationScalars.coerce(value, memberType);
    }

    private static Object[] convertAnnotationArrayMemberValue(
        Object value,
        ClassElement componentType,
        PythonVisitorContext visitorContext
    ) {
        if (Class.class.getName().equals(componentType.getName())) {
            return convertAnnotationClassArrayMemberValue(value, visitorContext);
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                .map(element -> convertAnnotationMemberValue(element, componentType, visitorContext))
                .toArray();
        }
        if (value instanceof Object[] array) {
            Object[] converted = new Object[array.length];
            for (int i = 0; i < array.length; i++) {
                converted[i] = convertAnnotationMemberValue(array[i], componentType, visitorContext);
            }
            return converted;
        }
        return new Object[] { convertAnnotationMemberValue(value, componentType, visitorContext) };
    }

    private static Object[] convertAnnotationClassArrayMemberValue(
        Object value,
        PythonVisitorContext visitorContext
    ) {
        List<Object> converted = new ArrayList<>();
        collectAnnotationClassValues(value, visitorContext, converted);
        if (converted.stream().allMatch(Class.class::isInstance)) {
            return converted.toArray(Class[]::new);
        }
        if (converted.stream().allMatch(String.class::isInstance)) {
            return convertedAnnotationClassStrings(converted, visitorContext);
        }
        return converted.toArray();
    }

    private static Object[] convertedAnnotationClassStrings(
        List<Object> converted,
        PythonVisitorContext visitorContext
    ) {
        List<String> classNames = new ArrayList<>(converted.size());
        for (Object value : converted) {
            classNames.add(rawClassName((String) value, visitorContext));
        }
        List<VariableDef.StaticField> classLiterals = new ArrayList<>(converted.size());
        for (String className : classNames) {
            ClassElement classElement = visitorContext.getClassElement(className).orElse(null);
            if (classElement == null) {
                return classNames.toArray(String[]::new);
            }
            classLiterals.add(rawClassLiteral(classElement));
        }
        return classLiterals.toArray();
    }

    private static void collectAnnotationClassValues(
        Object value,
        PythonVisitorContext visitorContext,
        List<Object> converted
    ) {
        if (value instanceof Collection<?> collection) {
            for (Object element : collection) {
                collectAnnotationClassValues(element, visitorContext, converted);
            }
            return;
        }
        if (value instanceof Object[] array) {
            for (Object element : array) {
                collectAnnotationClassValues(element, visitorContext, converted);
            }
            return;
        }
        Object classValue = annotationClassLiteralValue(value, visitorContext);
        if (classValue != null) {
            converted.add(classValue);
        }
    }

    private static VariableDef.StaticField enumConstantValue(Object value, ClassElement memberType) {
        ClassTypeDef enumType = rawClassType(memberType);
        return enumType.getStaticField(enumConstantName(value), enumType);
    }

    private static String enumConstantName(Object value) {
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        String stringValue = value.toString();
        int lastDot = stringValue.lastIndexOf('.');
        return lastDot > -1 ? stringValue.substring(lastDot + 1) : stringValue;
    }

    private static @Nullable Object annotationClassLiteralValue(Object value, PythonVisitorContext visitorContext) {
        if (value instanceof String stringValue) {
            return AnnotationNames.rawTypeName(stringValue);
        }
        String className = annotationClassName(value, visitorContext);
        if (className == null) {
            return null;
        }
        String resolvedClassName = rawClassName(className, visitorContext);
        return switch (resolvedClassName) {
            case "boolean" -> boolean.class;
            case "byte" -> byte.class;
            case "char" -> char.class;
            case "double" -> double.class;
            case "float" -> float.class;
            case "int" -> int.class;
            case "long" -> long.class;
            case "short" -> short.class;
            case "void" -> void.class;
            default -> {
                ClassElement classElement = visitorContext.getClassElement(resolvedClassName).orElse(null);
                yield classElement == null ? resolvedClassName : rawClassLiteral(classElement);
            }
        };
    }

    private static VariableDef.StaticField rawClassLiteral(ClassElement classElement) {
        return rawClassType(classElement).getStaticField("class", TypeDef.of(Class.class));
    }

    private static ClassTypeDef rawClassType(ClassElement classElement) {
        ClassElement rawClassElement = classElement.getRawClassElement();
        return ClassTypeDef.of(AnnotationNames.rawTypeName(PythonStubGenerator.javaTypeName(rawClassElement)), rawClassElement.isInner());
    }

    private static @Nullable String annotationClassName(Object value, PythonVisitorContext visitorContext) {
        if (value == null) {
            return null;
        }
        if (value instanceof AnnotationClassValue<?> annotationClassValue) {
            return rawClassName(annotationClassValue.getName(), visitorContext);
        }
        if (value instanceof Class<?> classValue) {
            return classValue.getName();
        }
        if (value instanceof ClassElement classElement) {
            return rawClassName(classElement.getRawClassElement().getName(), visitorContext);
        }
        return rawClassName(value.toString(), visitorContext);
    }

    private static String rawClassName(String typeName, PythonVisitorContext visitorContext) {
        String rawTypeName = AnnotationNames.rawTypeName(typeName);
        return visitorContext.getClassElement(rawTypeName)
            .map(classElement -> AnnotationNames.rawTypeName(classElement.getRawClassElement().getName()))
            .orElse(rawTypeName);
    }

    private static @Nullable ClassElement resolveAnnotationMemberType(
        String annotationName,
        String memberName,
        PythonVisitorContext visitorContext
    ) {
        ClassElement annotationType = visitorContext.getClassElement(annotationName).orElse(null);
        if (annotationType == null) {
            return null;
        }
        MethodElement annotationMember = annotationType
            .getEnclosedElement(ElementQuery.ALL_METHODS.onlyInstance().named(memberName))
            .orElse(null);
        return annotationMember == null ? null : annotationMember.getReturnType();
    }

    private static final class EmptyAnnotationArray {
        private static final EmptyAnnotationArray INSTANCE = new EmptyAnnotationArray();

        private EmptyAnnotationArray() {
        }

        @Override
        public String toString() {
            return "{}";
        }
    }
}
