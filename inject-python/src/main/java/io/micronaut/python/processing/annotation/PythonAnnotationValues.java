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
package io.micronaut.python.processing.annotation;

import io.micronaut.annotation.processing.visitor.JavaVisitorContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.python.processing.util.AnnotationNames;
import io.micronaut.python.processing.util.AnnotationScalars;
import io.micronaut.python.processing.model.AnnotationMemberDef;
import io.micronaut.python.processing.model.ClassDef;
import io.micronaut.python.processing.model.DecoratorDef;
import io.micronaut.python.processing.element.PythonClassElement;
import io.micronaut.python.processing.visitor.PythonVisitorContext;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Normalises raw decorator member values (literals, class references, enum names, nested decorators and
 * lists of those) to the values Micronaut annotation metadata stores for the declared member type.
 *
 * @since 5.2.0
 */
@Internal
final class PythonAnnotationValues {

    private final PythonVisitorContext visitorContext;
    private final AnnotationLookups lookups;

    PythonAnnotationValues(PythonVisitorContext visitorContext, AnnotationLookups lookups) {
        this.visitorContext = visitorContext;
        this.lookups = lookups;
    }

    /**
     * Normalises a raw decorator member value to the shape the declared member type expects.
     *
     * @param memberDef       The member
     * @param annotationValue The raw value
     * @return The normalised value
     */
    Object normalize(AnnotationMemberDef memberDef, Object annotationValue) {
        ClassElement memberType = memberDef.memberType();
        if (annotationValue instanceof String stringValue && AnnotationNames.isEnumMember(memberType)) {
            int lastDot = stringValue.lastIndexOf('.');
            if (lastDot > -1) {
                annotationValue = stringValue.substring(lastDot + 1);
            }
        } else if (isEnumArrayMember(memberType)) {
            annotationValue = enumValues(annotationValue);
        } else if (isClassArrayMember(memberType)) {
            annotationValue = classValues(annotationValue);
        } else if (isAnnotationArrayMember(memberType)) {
            annotationValue = annotationValues(memberType.fromArray(), annotationValue);
        } else if (isArrayMember(memberType)) {
            annotationValue = arrayValues(memberType.fromArray(), annotationValue);
        } else if (isClassMember(memberType)) {
            annotationValue = annotationClassValue(annotationValue);
        } else if (isAnnotationMember(memberType) && annotationValue instanceof DecoratorDef decoratorDef) {
            annotationValue = lookups.annotationValue(decoratorDef);
        } else {
            annotationValue = AnnotationScalars.coerce(annotationValue, memberType);
        }
        return annotationValue;
    }

    private boolean isArrayMember(@Nullable ClassElement memberType) {
        return memberType != null && memberType.isArray();
    }

    private boolean isClassMember(@Nullable ClassElement memberType) {
        return memberType != null && !memberType.isArray() && Class.class.getName().equals(memberType.getName());
    }

    private boolean isClassArrayMember(@Nullable ClassElement memberType) {
        return memberType != null && memberType.isArray() && isClassMember(memberType.fromArray());
    }

    private boolean isAnnotationArrayMember(@Nullable ClassElement memberType) {
        return memberType != null && memberType.isArray() && isAnnotationMember(memberType.fromArray());
    }

    private boolean isAnnotationMember(@Nullable ClassElement memberType) {
        return memberType != null && memberType.isAssignable(Annotation.class);
    }

    private @Nullable AnnotationClassValue<?> annotationClassValue(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof AnnotationClassValue<?> annotationClassValue) {
            return annotationClassValue;
        }
        if (value instanceof Class<?> classValue) {
            return new AnnotationClassValue<>(ReflectionUtils.getWrapperType(classValue));
        }
        if (value instanceof ClassElement classElement) {
            return new AnnotationClassValue<>(classElement.getRawClassElement().getName());
        }
        String typeName = AnnotationNames.rawTypeName(value.toString());
        String pythonClassName = pythonClassName(typeName);
        if (pythonClassName != null) {
            return new AnnotationClassValue<>(pythonClassName);
        }
        String decoratorAnnotationName = decoratorAnnotationName(typeName);
        if (decoratorAnnotationName != null) {
            return new AnnotationClassValue<>(decoratorAnnotationName);
        }
        return new AnnotationClassValue<>(annotationClassName(typeName));
    }

    private @Nullable String pythonClassName(String typeName) {
        Map<String, ClassDef> classes = visitorContext.getProcessingEnvironment().environment().classes();
        ClassDef classDef = classes.get(typeName);
        String defaultPackage = PythonClassElement.PYTHON_DEFAULT_PACKAGE + '.';
        if (classDef == null && typeName.startsWith(defaultPackage)) {
            classDef = classes.get(typeName.substring(defaultPackage.length()));
        }
        if (classDef == null) {
            classDef = classes.get(defaultPackage + typeName);
        }
        return classDef == null ? null : PythonAnnotationMetadataBuilder.toQualifiedPythonName(classDef);
    }

    private String annotationClassName(String typeName) {
        String builtinTypeName = builtinAnnotationClassName(typeName);
        if (builtinTypeName != null) {
            return builtinTypeName;
        }
        if (typeName.indexOf('.') > -1) {
            JavaVisitorContext javaVisitorContext = visitorContext.getJavaVisitorContext();
            if (javaVisitorContext != null) {
                ClassElement classElement = javaVisitorContext.getClassElement(typeName).orElse(null);
                if (classElement != null) {
                    return classElement.getName();
                }
            }
        }
        return typeName;
    }

    private static @Nullable String builtinAnnotationClassName(String typeName) {
        return switch (typeName) {
            case "object", "typing.Any", "Any" -> Object.class.getName();
            case "int" -> Integer.class.getName();
            case "float" -> Double.class.getName();
            case "bool" -> Boolean.class.getName();
            case "str" -> String.class.getName();
            default -> null;
        };
    }

    private @Nullable String decoratorAnnotationName(String typeName) {
        Map<String, DecoratorDef> decorators = visitorContext.getProcessingEnvironment().environment().decorators();
        DecoratorDef decoratorDef = decorators.get(typeName);
        String defaultPackage = PythonClassElement.PYTHON_DEFAULT_PACKAGE + '.';
        if (decoratorDef == null && typeName.startsWith(defaultPackage)) {
            decoratorDef = decorators.get(typeName.substring(defaultPackage.length()));
        }
        if (decoratorDef == null) {
            for (DecoratorDef candidate : decorators.values()) {
                if (candidate.annotationName().equals(typeName)
                    || candidate.name().equals(typeName)
                    || (typeName.startsWith(defaultPackage) && candidate.name().equals(typeName.substring(defaultPackage.length())))) {
                    decoratorDef = candidate;
                    break;
                }
            }
        }
        return decoratorDef == null ? null : decoratorDef.annotationName();
    }

    AnnotationClassValue<?>[] classValues(@Nullable Object value) {
        List<AnnotationClassValue<?>> values = new ArrayList<>();
        collectAnnotationClassValues(value, values);
        return values.toArray(AnnotationClassValue[]::new);
    }

    private void collectAnnotationClassValues(@Nullable Object value, List<AnnotationClassValue<?>> values) {
        if (value == null) {
            return;
        }
        if (value.getClass().isArray()) {
            int size = Array.getLength(value);
            for (int i = 0; i < size; i++) {
                collectAnnotationClassValues(Array.get(value, i), values);
            }
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                collectAnnotationClassValues(element, values);
            }
            return;
        }
        addAnnotationClassValue(value, values);
    }

    private void addAnnotationClassValue(@Nullable Object value, List<AnnotationClassValue<?>> values) {
        AnnotationClassValue<?> classValue = annotationClassValue(value);
        if (classValue != null) {
            values.add(classValue);
        }
    }

    private Object arrayValues(ClassElement componentType, @Nullable Object value) {
        List<Object> values = new ArrayList<>();
        collectArrayValues(componentType, value, values);
        return toArray(componentType, values);
    }

    private AnnotationValue<?>[] annotationValues(ClassElement componentType, @Nullable Object value) {
        List<AnnotationValue<?>> values = new ArrayList<>();
        collectAnnotationValues(componentType, value, values);
        return values.toArray(AnnotationValue[]::new);
    }

    private void collectAnnotationValues(ClassElement componentType, @Nullable Object value, List<AnnotationValue<?>> values) {
        if (value == null) {
            return;
        }
        if (value.getClass().isArray()) {
            int size = Array.getLength(value);
            for (int i = 0; i < size; i++) {
                collectAnnotationValues(componentType, Array.get(value, i), values);
            }
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                collectAnnotationValues(componentType, element, values);
            }
            return;
        }
        addAnnotationValue(value, values);
    }

    @SuppressWarnings("unchecked")
    private void addAnnotationValue(Object value, List<AnnotationValue<?>> values) {
        if (value instanceof AnnotationValue<?> annotationValue) {
            values.add(annotationValue);
            return;
        }
        if (value instanceof DecoratorDef decoratorDef) {
            values.add(lookups.annotationValue(decoratorDef));
            return;
        }
    }

    private void collectArrayValues(ClassElement componentType, @Nullable Object value, List<Object> values) {
        if (value == null) {
            return;
        }
        if (value.getClass().isArray()) {
            int size = Array.getLength(value);
            for (int i = 0; i < size; i++) {
                collectArrayValues(componentType, Array.get(value, i), values);
            }
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                collectArrayValues(componentType, element, values);
            }
            return;
        }
        values.add(value);
    }

    private Object toArray(ClassElement componentType, List<Object> values) {
        return switch (componentType.getName()) {
            case "boolean" -> {
                boolean[] array = new boolean[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    array[i] = toBoolean(values.get(i));
                }
                yield array;
            }
            case "byte" -> {
                byte[] array = new byte[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    array[i] = (byte) AnnotationScalars.integral(toNumber(values.get(i)), Byte.MIN_VALUE, Byte.MAX_VALUE, "byte");
                }
                yield array;
            }
            case "char" -> {
                char[] array = new char[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    array[i] = AnnotationScalars.character(values.get(i));
                }
                yield array;
            }
            case "double" -> {
                double[] array = new double[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    array[i] = AnnotationScalars.number(values.get(i), "double").doubleValue();
                }
                yield array;
            }
            case "float" -> {
                float[] array = new float[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    array[i] = AnnotationScalars.floating(AnnotationScalars.number(values.get(i), "float"));
                }
                yield array;
            }
            case "int" -> {
                int[] array = new int[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    array[i] = (int) AnnotationScalars.integral(toNumber(values.get(i)), Integer.MIN_VALUE, Integer.MAX_VALUE, "int");
                }
                yield array;
            }
            case "long" -> {
                long[] array = new long[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    array[i] = AnnotationScalars.integral(toNumber(values.get(i)), Long.MIN_VALUE, Long.MAX_VALUE, "long");
                }
                yield array;
            }
            case "short" -> {
                short[] array = new short[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    array[i] = (short) AnnotationScalars.integral(toNumber(values.get(i)), Short.MIN_VALUE, Short.MAX_VALUE, "short");
                }
                yield array;
            }
            case "java.lang.String" -> {
                String[] array = new String[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    array[i] = values.get(i).toString();
                }
                yield array;
            }
            default -> values.toArray(Object[]::new);
        };
    }

    private Number toNumber(Object value) {
        // a numeric array element must be a number, as in Java source
        return AnnotationScalars.number(value, "number");
    }

    private boolean toBoolean(Object value) {
        return AnnotationScalars.bool(value);
    }

    private static boolean isEnumArrayMember(@Nullable ClassElement memberType) {
        return memberType != null && memberType.isArray() && AnnotationNames.isEnumMember(memberType.fromArray());
    }

    private String[] enumValues(@Nullable Object value) {
        List<String> values = new ArrayList<>();
        collectEnumValues(value, values);
        return values.toArray(String[]::new);
    }

    private void collectEnumValues(@Nullable Object value, List<String> values) {
        if (value == null) {
            return;
        }
        if (value.getClass().isArray()) {
            int size = Array.getLength(value);
            for (int i = 0; i < size; i++) {
                collectEnumValues(Array.get(value, i), values);
            }
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                collectEnumValues(element, values);
            }
            return;
        }
        addEnumValue(value, values);
    }

    private void addEnumValue(Object value, List<String> values) {
        String enumValue = enumValue(value);
        if (enumValue != null) {
            values.add(enumValue);
        }
    }

    private @Nullable String enumValue(Object value) {
        String stringValue = value instanceof Enum<?> enumValue ? enumValue.name() : value.toString();
        int lastDot = stringValue.lastIndexOf('.');
        return lastDot > -1 ? stringValue.substring(lastDot + 1) : stringValue;
    }
}
