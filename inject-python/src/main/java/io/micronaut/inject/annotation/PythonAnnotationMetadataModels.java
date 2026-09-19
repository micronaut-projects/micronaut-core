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
package io.micronaut.inject.annotation;

import io.micronaut.context.python.runtime.model.AnnotationMetadataModel;
import io.micronaut.context.python.runtime.model.AnnotationValueModel;
import io.micronaut.context.python.runtime.model.ArrayValueModel;
import io.micronaut.context.python.runtime.model.ClassValueModel;
import io.micronaut.context.python.runtime.model.ValueKind;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.expressions.EvaluatedExpressionReference;
import io.micronaut.core.util.CollectionUtils;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exports resolved annotation metadata into the model, applying the same rules as the bytecode writer of
 * {@code DefaultAnnotationMetadata}: source-retained annotations are dropped from every map, only runtime-retained
 * stereotypes are kept in the reserved stereotypes member, enums are written by name, and the annotation defaults
 * and repeatable containers the runtime must register are recorded without the core ones the runtime knows.
 * Lives in this package for the package-private maps of {@link MutableAnnotationMetadata}, like the writer does.
 *
 * @since 5.3.0
 */
@Internal
public final class PythonAnnotationMetadataModels {

    private PythonAnnotationMetadataModels() {
    }

    /**
     * Exports annotation metadata.
     *
     * @param annotationMetadata The metadata
     * @return The model
     * @throws IllegalArgumentException If the metadata carries a value the model cannot represent
     */
    public static AnnotationMetadataModel export(AnnotationMetadata annotationMetadata) {
        annotationMetadata = annotationMetadata.getTargetAnnotationMetadata();
        if (annotationMetadata instanceof AnnotationMetadataHierarchy hierarchy) {
            annotationMetadata = hierarchy.merge();
        }
        if (annotationMetadata.isEmpty()) {
            return AnnotationMetadataModel.EMPTY;
        }
        if (!(annotationMetadata instanceof MutableAnnotationMetadata mutable)) {
            throw new IllegalArgumentException("Unknown annotation metadata: " + annotationMetadata.getClass().getName());
        }
        Set<String> sourceRetention = mutable.getSourceRetentionAnnotations();
        Map<String, List<String>> byStereotype = new LinkedHashMap<>();
        if (mutable.annotationsByStereotype != null) {
            for (Map.Entry<String, List<String>> entry : mutable.annotationsByStereotype.entrySet()) {
                if (sourceRetention == null || !sourceRetention.contains(entry.getKey())) {
                    byStereotype.put(entry.getKey(), List.copyOf(entry.getValue()));
                }
            }
        }
        return new AnnotationMetadataModel(
            annotations(mutable.declaredAnnotations, sourceRetention),
            annotations(mutable.declaredStereotypes, sourceRetention),
            annotations(mutable.allStereotypes, sourceRetention),
            annotations(mutable.allAnnotations, sourceRetention),
            byStereotype,
            defaults(mutable),
            repeatable(mutable),
            mutable.hasPropertyExpressions()
        );
    }

    private static Map<String, Map<String, Object>> annotations(@Nullable Map<String, Map<CharSequence, Object>> data,
                                                                @Nullable Set<String> sourceRetention) {
        if (data == null || data.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<CharSequence, Object>> entry : data.entrySet()) {
            if (sourceRetention != null && sourceRetention.contains(entry.getKey())) {
                continue;
            }
            result.put(entry.getKey(), values(writableValues(entry.getValue())));
        }
        return result;
    }

    private static Map<String, Map<String, Object>> defaults(MutableAnnotationMetadata metadata) {
        Map<String, Map<CharSequence, Object>> defaults = metadata.annotationDefaultValues;
        if (CollectionUtils.isEmpty(defaults)) {
            return Map.of();
        }
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        Set<String> written = new LinkedHashSet<>();
        for (Map.Entry<String, Map<CharSequence, Object>> entry : defaults.entrySet()) {
            addDefaults(result, written, entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static void addDefaults(Map<String, Map<String, Object>> result, Set<String> written, String annotationName,
                                    @Nullable Map<CharSequence, Object> values) {
        boolean typeOnly = values == null || values.isEmpty();
        if ((typeOnly && AnnotationMetadataSupport.getRegisteredAnnotationType(annotationName).isPresent())
            || AnnotationMetadataSupport.getCoreAnnotationDefaults().containsKey(annotationName)) {
            return;
        }
        if (!written.add(annotationName)) {
            return;
        }
        if (values != null) {
            for (Object value : values.values()) {
                if (value instanceof AnnotationValue<?> nested && CollectionUtils.isNotEmpty(nested.getDefaultValues())) {
                    addDefaults(result, written, nested.getAnnotationName(), nested.getDefaultValues());
                }
            }
        }
        result.put(annotationName, values == null || typeOnly ? Map.of() : values(values));
    }

    private static Map<String, String> repeatable(MutableAnnotationMetadata metadata) {
        if (metadata.annotationRepeatableContainer == null || metadata.annotationRepeatableContainer.isEmpty()) {
            return Map.of();
        }
        Map<String, String> containers = new LinkedHashMap<>(metadata.annotationRepeatableContainer);
        AnnotationMetadataSupport.getCoreRepeatableAnnotationsContainers().forEach(containers::remove);
        return containers;
    }

    private static Map<CharSequence, Object> writableValues(Map<CharSequence, Object> values) {
        Object retained = values.get(AnnotationUtil.STEREOTYPES_MEMBER);
        if (retained == null) {
            return values;
        }
        Object[] stereotypes = retained instanceof Collection<?> collection ? collection.toArray() : (Object[]) retained;
        List<AnnotationValue<?>> runtimeStereotypes = new ArrayList<>(stereotypes.length);
        for (Object stereotype : stereotypes) {
            if (stereotype instanceof AnnotationValue<?> annotationValue && annotationValue.getRetentionPolicy() == RetentionPolicy.RUNTIME) {
                runtimeStereotypes.add(annotationValue);
            }
        }
        Map<CharSequence, Object> writable = new LinkedHashMap<>(values);
        if (runtimeStereotypes.isEmpty()) {
            writable.remove(AnnotationUtil.STEREOTYPES_MEMBER);
        } else {
            writable.put(AnnotationUtil.STEREOTYPES_MEMBER, runtimeStereotypes.toArray(AnnotationValue[]::new));
        }
        return writable;
    }

    private static Map<CharSequence, Object> writableValues(AnnotationValue<?> annotationValue) {
        if (!annotationValue.contains(AnnotationUtil.STEREOTYPES_MEMBER)) {
            return annotationValue.getValues();
        }
        Map<CharSequence, Object> values = new LinkedHashMap<>(annotationValue.getValues());
        values.put(AnnotationUtil.STEREOTYPES_MEMBER, annotationValue.getAnnotations(AnnotationUtil.STEREOTYPES_MEMBER).toArray(AnnotationValue[]::new));
        return writableValues(values);
    }

    private static Map<String, Object> values(Map<CharSequence, Object> values) {
        if (values.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<CharSequence, Object> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            result.put(entry.getKey().toString(), value(value));
        }
        return result;
    }

    private static Object value(Object value) {
        if (value instanceof Enum<?> anEnum) {
            return anEnum.name();
        }
        if (value instanceof Boolean || value instanceof String || value instanceof Character
            || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
            || value instanceof Float || value instanceof Double) {
            return value;
        }
        if (value instanceof AnnotationClassValue<?> classValue) {
            if (classValue.isInstantiated()) {
                throw new IllegalArgumentException("Instantiated class values are not supported by the model: " + classValue.getName());
            }
            return new ClassValueModel(classValue.getName());
        }
        if (value.getClass().isArray()) {
            Class<?> componentType = value.getClass().getComponentType();
            int length = Array.getLength(value);
            List<Object> elements = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                elements.add(value(Array.get(value, i)));
            }
            return new ArrayValueModel(kindOf(componentType), componentType.isPrimitive(), elements);
        }
        if (value instanceof Collection<?> collection) {
            Class<?> componentType = null;
            for (Object element : collection) {
                if (componentType == null) {
                    componentType = element.getClass();
                } else if (!element.getClass().equals(componentType)) {
                    componentType = Object.class;
                    break;
                }
            }
            List<Object> elements = new ArrayList<>(collection.size());
            for (Object element : collection) {
                elements.add(value(element));
            }
            return new ArrayValueModel(componentType == null ? ValueKind.OBJECT : kindOf(componentType), false, elements);
        }
        if (value instanceof AnnotationValue<?> annotationValue) {
            return new AnnotationValueModel(annotationValue.getAnnotationName(), values(writableValues(annotationValue)));
        }
        if (value instanceof EvaluatedExpressionReference) {
            throw new IllegalArgumentException("Evaluated expressions are not supported by the model: " + value);
        }
        if (value instanceof Number) {
            return value;
        }
        throw new IllegalArgumentException("Unsupported annotation value " + value.getClass().getName() + ": " + value);
    }

    private static ValueKind kindOf(Class<?> componentType) {
        if (Enum.class.isAssignableFrom(componentType) || componentType == String.class) {
            return ValueKind.STRING;
        }
        if (componentType == boolean.class || componentType == Boolean.class) {
            return ValueKind.BOOLEAN;
        }
        if (componentType == byte.class || componentType == Byte.class) {
            return ValueKind.BYTE;
        }
        if (componentType == short.class || componentType == Short.class) {
            return ValueKind.SHORT;
        }
        if (componentType == int.class || componentType == Integer.class) {
            return ValueKind.INT;
        }
        if (componentType == long.class || componentType == Long.class) {
            return ValueKind.LONG;
        }
        if (componentType == float.class || componentType == Float.class) {
            return ValueKind.FLOAT;
        }
        if (componentType == double.class || componentType == Double.class) {
            return ValueKind.DOUBLE;
        }
        if (componentType == char.class || componentType == Character.class) {
            return ValueKind.CHAR;
        }
        if (AnnotationClassValue.class.isAssignableFrom(componentType)) {
            return ValueKind.CLASS;
        }
        if (AnnotationValue.class.isAssignableFrom(componentType)) {
            return ValueKind.ANNOTATION;
        }
        if (componentType.isArray()) {
            return ValueKind.ARRAY;
        }
        return ValueKind.OBJECT;
    }
}
