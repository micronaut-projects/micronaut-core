/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.context.expressions.AbstractEvaluatedExpression;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationDefaultValuesProvider;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.expressions.EvaluatedExpressionReference;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.writer.ExpressionsUtils;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;

import javax.lang.model.element.Modifier;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Responsible for writing class files that are instances of {@link AnnotationMetadata}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class AnnotationMetadataGenUtils {

    /**
     * Field name for annotation metadata.
     */
    public static final String FIELD_ANNOTATION_METADATA_NAME = "$ANNOTATION_METADATA";
    public static final ClassTypeDef TYPE_ANNOTATION_METADATA = ClassTypeDef.of(AnnotationMetadata.class);

    public static final FieldDef FIELD_ANNOTATION_METADATA = FieldDef.builder(FIELD_ANNOTATION_METADATA_NAME, TYPE_ANNOTATION_METADATA).build();
    public static final ExpressionDef EMPTY_METADATA = TYPE_ANNOTATION_METADATA.getStaticField(
        FieldDef.builder("EMPTY_METADATA", TYPE_ANNOTATION_METADATA).build()
    );

    private static final ClassTypeDef TYPE_DEFAULT_ANNOTATION_METADATA = ClassTypeDef.of(DefaultAnnotationMetadata.class);
    private static final ClassTypeDef TYPE_DEFAULT_ANNOTATION_METADATA_HIERARCHY = ClassTypeDef.of(AnnotationMetadataHierarchy.class);
    private static final ClassTypeDef TYPE_ANNOTATION_CLASS_VALUE = ClassTypeDef.of(AnnotationClassValue.class);

    private static final String LOAD_CLASS_PREFIX = "$micronaut_load_class_value_";

    private static final Method METHOD_REGISTER_ANNOTATION_DEFAULTS = ReflectionUtils.getRequiredInternalMethod(
        DefaultAnnotationMetadata.class,
        "registerAnnotationDefaults",
        AnnotationClassValue.class,
        Map.class
    );

    private static final Method METHOD_REGISTER_ANNOTATION_TYPE = ReflectionUtils.getRequiredInternalMethod(
        DefaultAnnotationMetadata.class,
        "registerAnnotationType",
        AnnotationClassValue.class
    );

    private static final Method METHOD_REGISTER_REPEATABLE_ANNOTATIONS = ReflectionUtils.getRequiredInternalMethod(
        DefaultAnnotationMetadata.class,
        "registerRepeatableAnnotations",
        Map.class
    );

    private static final Constructor<?> CONSTRUCTOR_ANNOTATION_METADATA = ReflectionUtils.getRequiredInternalConstructor(
        DefaultAnnotationMetadata.class,
        Map.class,
        Map.class,
        Map.class,
        Map.class,
        Map.class,
        boolean.class,
        boolean.class
    );

    private static final Constructor<?> CONSTRUCTOR_ANNOTATION_METADATA_HIERARCHY = ReflectionUtils.getRequiredInternalConstructor(
        AnnotationMetadataHierarchy.class,
        AnnotationMetadata[].class
    );

    private static final Constructor<?> CONSTRUCTOR_ANNOTATION_VALUE_AND_MAP = ReflectionUtils.getRequiredInternalConstructor(
        AnnotationValue.class,
        String.class,
        Map.class,
        AnnotationDefaultValuesProvider.class
    );

    private static final Constructor<?> CONSTRUCTOR_CLASS_VALUE = ReflectionUtils.getRequiredInternalConstructor(
        AnnotationClassValue.class,
        String.class
    );

    private static final Constructor<?> CONSTRUCTOR_CLASS_VALUE_WITH_CLASS = ReflectionUtils.getRequiredInternalConstructor(
        AnnotationClassValue.class,
        Class.class
    );

    private static final Constructor<?> CONSTRUCTOR_CLASS_VALUE_WITH_INSTANCE = ReflectionUtils.getRequiredInternalConstructor(
        AnnotationClassValue.class,
        Object.class
    );

    private static final Constructor<?> CONSTRUCTOR_CONTEXT_EVALUATED_EXPRESSION = ReflectionUtils.getRequiredInternalConstructor(
        AbstractEvaluatedExpression.class,
        Object.class
    );

    private static final Field ANNOTATION_DEFAULT_VALUES_PROVIDER = ReflectionUtils.getRequiredField(
        AnnotationMetadataSupport.class,
        "ANNOTATION_DEFAULT_VALUES_PROVIDER"
    );

    @Internal
    public static ExpressionDef instantiateNewMetadata(ClassTypeDef owningType,
                                                       MutableAnnotationMetadata annotationMetadata,
                                                       Map<String, MethodDef> loadTypeMethods) {
        return instantiateInternal(owningType, annotationMetadata, true, loadTypeMethods);
    }

    @Internal
    public static ExpressionDef instantiateNewMetadataHierarchy(
        ClassTypeDef owningType,
        AnnotationMetadataHierarchy hierarchy,
        Map<String, MethodDef> loadTypeMethods) {

        if (hierarchy.isEmpty()) {
            return emptyMetadata();
        }
        List<AnnotationMetadata> notEmpty = CollectionUtils.iterableToList(hierarchy)
            .stream().filter(h -> !h.isEmpty()).toList();
        if (notEmpty.size() == 1) {
            return pushNewAnnotationMetadataOrReference(owningType, loadTypeMethods, notEmpty.get(0));
        }

        return TYPE_DEFAULT_ANNOTATION_METADATA_HIERARCHY.instantiate(
            CONSTRUCTOR_ANNOTATION_METADATA_HIERARCHY,

            TYPE_ANNOTATION_METADATA.array().instantiate(
                pushNewAnnotationMetadataOrReference(owningType, loadTypeMethods, hierarchy.getRootMetadata()),
                pushNewAnnotationMetadataOrReference(owningType, loadTypeMethods, hierarchy.getDeclaredMetadata())
            )
        );
    }

    @Internal
    public static ExpressionDef annotationMetadataReference(AnnotationMetadataReference annotationMetadata) {
        return ClassTypeDef.of(annotationMetadata.getClassName()).getStaticField(FIELD_ANNOTATION_METADATA);
    }

    @Internal
    private static ExpressionDef pushNewAnnotationMetadataOrReference(
        ClassTypeDef owningType,
        Map<String, MethodDef> loadTypeMethods,
        AnnotationMetadata annotationMetadata) {
        annotationMetadata = annotationMetadata.getTargetAnnotationMetadata();
        if (annotationMetadata instanceof AnnotationMetadataHierarchy annotationMetadataHierarchy) {
            // Synthetic property getters / setters can consist of field + (setter / getter) annotation hierarchy
            annotationMetadata = MutableAnnotationMetadata.of(annotationMetadataHierarchy);
        }
        if (annotationMetadata.isEmpty()) {
            return emptyMetadata();
        } else if (annotationMetadata instanceof MutableAnnotationMetadata mutableAnnotationMetadata) {
            return instantiateNewMetadata(owningType, mutableAnnotationMetadata, loadTypeMethods);
        } else if (annotationMetadata instanceof AnnotationMetadataReference reference) {
            return annotationMetadataReference(reference);
        } else {
            throw new IllegalStateException("Unknown annotation metadata: " + annotationMetadata);
        }
    }

    public static ExpressionDef emptyMetadata() {
        return TYPE_ANNOTATION_METADATA.getStaticField("EMPTY_METADATA", TYPE_ANNOTATION_METADATA);
    }

    @Internal
    public static void writeAnnotationDefaults(
        List<StatementDef> statements,
        ClassTypeDef owningType,
        MutableAnnotationMetadata annotationMetadata,
        Map<String, MethodDef> loadTypeMethods) {
        final Map<String, Map<CharSequence, Object>> annotationDefaultValues = annotationMetadata.annotationDefaultValues;

        if (CollectionUtils.isNotEmpty(annotationDefaultValues)) {
            writeAnnotationDefaultsInternal(statements, owningType, loadTypeMethods, annotationDefaultValues, new HashSet<>());
        }
        if (annotationMetadata.annotationRepeatableContainer != null && !annotationMetadata.annotationRepeatableContainer.isEmpty()) {
            Map<String, String> annotationRepeatableContainer = new LinkedHashMap<>(annotationMetadata.annotationRepeatableContainer);
            AnnotationMetadataSupport.getCoreRepeatableAnnotationsContainers().forEach(annotationRepeatableContainer::remove);
            AnnotationMetadataSupport.registerRepeatableAnnotations(annotationRepeatableContainer);
            if (!annotationRepeatableContainer.isEmpty()) {
                statements.add(
                    TYPE_DEFAULT_ANNOTATION_METADATA.invokeStatic(
                        METHOD_REGISTER_REPEATABLE_ANNOTATIONS,
                        stringMapOf(owningType, annotationRepeatableContainer, loadTypeMethods)
                    )
                );
            }
        }
    }

    private static void writeAnnotationDefaultsInternal(List<StatementDef> statements,
                                                        ClassTypeDef owningType,
                                                        Map<String, MethodDef> loadTypeMethods,
                                                        Map<String, Map<CharSequence, Object>> annotationDefaultValues,
                                                        Set<String> writtenAnnotations) {
        for (Map.Entry<String, Map<CharSequence, Object>> entry : annotationDefaultValues.entrySet()) {
            writeAnnotationDefaultsInternal(statements,
                owningType,
                loadTypeMethods,
                writtenAnnotations,
                entry.getKey(),
                entry.getValue());
        }
    }

    @NonNull
    private static void writeAnnotationDefaultsInternal(List<StatementDef> statements,
                                                        ClassTypeDef owningType,
                                                        Map<String, MethodDef> loadTypeMethods,
                                                        Set<String> writtenAnnotations,
                                                        String annotationName,
                                                        Map<CharSequence, Object> annotationValues) {
        final boolean typeOnly = CollectionUtils.isEmpty(annotationValues);

        // skip already registered
        if (typeOnly && AnnotationMetadataSupport.getRegisteredAnnotationType(annotationName).isPresent() || AnnotationMetadataSupport.getCoreAnnotationDefaults().containsKey(annotationName)) {
            return;
        }

        if (!writtenAnnotations.add(annotationName)) {
            return;
        }

        for (Map.Entry<CharSequence, Object> values : annotationValues.entrySet()) {
            Object value = values.getValue();
            if (value instanceof AnnotationValue<?> annotationValue && CollectionUtils.isNotEmpty(annotationValue.getDefaultValues())) {
                writeAnnotationDefaultsInternal(
                    statements,
                    owningType,
                    loadTypeMethods,
                    writtenAnnotations,
                    annotationValue.getAnnotationName(),
                    annotationValue.getDefaultValues()
                );
            }
        }

        if (!typeOnly) {
            statements.add(
                TYPE_DEFAULT_ANNOTATION_METADATA.invokeStatic(
                    METHOD_REGISTER_ANNOTATION_DEFAULTS,
                    invokeLoadClassValueMethod(owningType, loadTypeMethods, new AnnotationClassValue<>(annotationName)),
                    stringMapOf(owningType, annotationValues, loadTypeMethods)
                )
            );
        } else {
            statements.add(
                TYPE_DEFAULT_ANNOTATION_METADATA.invokeStatic(
                    METHOD_REGISTER_ANNOTATION_TYPE,
                    invokeLoadClassValueMethod(owningType, loadTypeMethods, new AnnotationClassValue<>(annotationName))
                )
            );
        }
        writtenAnnotations.add(annotationName);
    }

    private static ExpressionDef instantiateInternal(
        ClassTypeDef owningType,
        MutableAnnotationMetadata annotationMetadata,
        boolean isNew,
        Map<String, MethodDef> loadTypeMethods) {
        if (!isNew) {
            throw new IllegalStateException();
        }
        Map<String, List<String>> annotationsByStereotype = annotationMetadata.annotationsByStereotype;
        if (annotationMetadata.getSourceRetentionAnnotations() != null && annotationsByStereotype != null) {
            annotationsByStereotype = new LinkedHashMap<>(annotationsByStereotype);
            for (String sourceRetentionAnnotation : annotationMetadata.getSourceRetentionAnnotations()) {
                annotationsByStereotype.remove(sourceRetentionAnnotation);
            }
        }
        return TYPE_DEFAULT_ANNOTATION_METADATA
            .instantiate(
                CONSTRUCTOR_ANNOTATION_METADATA,

                // 1st argument: the declared annotations
                pushCreateAnnotationData(owningType, annotationMetadata.declaredAnnotations, loadTypeMethods, annotationMetadata.getSourceRetentionAnnotations()),
                // 2nd argument: the declared stereotypes
                pushCreateAnnotationData(owningType, annotationMetadata.declaredStereotypes, loadTypeMethods, annotationMetadata.getSourceRetentionAnnotations()),
                // 3rd argument: all stereotypes
                pushCreateAnnotationData(owningType, annotationMetadata.allStereotypes, loadTypeMethods, annotationMetadata.getSourceRetentionAnnotations()),
                // 4th argument: all annotations
                pushCreateAnnotationData(owningType, annotationMetadata.allAnnotations, loadTypeMethods, annotationMetadata.getSourceRetentionAnnotations()),
                // 5th argument: annotations by stereotype,
                ExpressionsUtils.stringMapOf(annotationsByStereotype, false, Collections.emptyList(), ExpressionsUtils::listOfString),
                // 6th argument: has property expressions,
                ExpressionDef.constant(annotationMetadata.hasPropertyExpressions()),
                // 7th argument: has evaluated expressions
                ExpressionDef.constant(annotationMetadata.hasEvaluatedExpressions())
            );
    }

    private static ExpressionDef pushCreateAnnotationData(
        ClassTypeDef declaringType,
        Map<String, Map<CharSequence, Object>> annotationData,
        Map<String, MethodDef> loadTypeMethods,
        Set<String> sourceRetentionAnnotations) {
        if (annotationData != null) {
            annotationData = new LinkedHashMap<>(annotationData);
            for (String sourceRetentionAnnotation : sourceRetentionAnnotations) {
                annotationData.remove(sourceRetentionAnnotation);
            }
        }

        return ExpressionsUtils.stringMapOf(annotationData, false, Collections.emptyMap(),
            attributes -> ExpressionsUtils.stringMapOf(attributes, true, null,
                value -> asValueExpression(declaringType, value, loadTypeMethods)));
    }

    private static ExpressionDef asValueExpression(ClassTypeDef declaringType,
                                                   Object value,
                                                   Map<String, MethodDef> loadTypeMethods) {
        if (value == null) {
            throw new IllegalStateException("Cannot map null value in: " + declaringType.getName());
        }
        if (value instanceof Enum<?> anEnum) {
            return ExpressionDef.constant(anEnum.name());
        }
        if (value instanceof Boolean || value instanceof String || value instanceof Number || value instanceof Character) {
            return ExpressionDef.constant(value);
        }
        if (value instanceof AnnotationClassValue<?> acv) {
            if (acv.isInstantiated()) {
                return TYPE_ANNOTATION_CLASS_VALUE
                    .instantiate(CONSTRUCTOR_CLASS_VALUE_WITH_INSTANCE,
                        ClassTypeDef.of(acv.getName()).instantiate()
                    );
            } else {
                return invokeLoadClassValueMethod(declaringType, loadTypeMethods, acv);
            }
        }
        if (value.getClass().isArray()) {
            Class<?> arrayComponentType = value.getClass().getComponentType();
            if (Enum.class.isAssignableFrom(arrayComponentType)) {
                // Express enums as strings
                arrayComponentType = String.class;
            }
            return TypeDef.of(arrayComponentType).array().instantiate(Arrays.stream(getArray(value))
                .map(v -> asValueExpression(declaringType, v, loadTypeMethods))
                .toList());
        }
        if (value instanceof Collection<?> collection) {
            if (collection.isEmpty()) {
                return ExpressionDef.constant(new Object[0]);
            }
            Class<?> componentType = null;
            for (Object o : collection) {
                if (componentType == null) {
                    componentType = o.getClass();
                } else if (!o.getClass().equals(componentType)) {
                    componentType = Object.class;
                    break;
                }
            }
            if (Enum.class.isAssignableFrom(componentType)) {
                // Express enums as strings
                componentType = String.class;
            }
            return TypeDef.of(componentType).array()
                .instantiate(collection.stream().map(i -> asValueExpression(declaringType, i, loadTypeMethods)).toList());
        }
        if (value instanceof AnnotationValue<?> data) {
            return ClassTypeDef.of(AnnotationValue.class)
                .instantiate(
                    CONSTRUCTOR_ANNOTATION_VALUE_AND_MAP,
                    ExpressionDef.constant(data.getAnnotationName()),
                    stringMapOf(declaringType, data.getValues(), loadTypeMethods),
                    ClassTypeDef.of(AnnotationMetadataSupport.class).getStaticField(ANNOTATION_DEFAULT_VALUES_PROVIDER)
                );
        }
        if (value instanceof EvaluatedExpressionReference expressionReference) {
            Object annotationValue = expressionReference.annotationValue();
            if (annotationValue instanceof String || annotationValue instanceof String[]) {
                return ClassTypeDef.of(expressionReference.expressionClassName())
                    .instantiate(
                        CONSTRUCTOR_CONTEXT_EVALUATED_EXPRESSION,

                        ExpressionDef.constant(annotationValue)
                    );
            } else {
                throw new IllegalStateException();
            }
        }
        throw new IllegalStateException("Unsupported Map value:  " + value + " " + value.getClass().getName());
    }

    private static Object[] getArray(Object val) {
        if (val instanceof Object[]) {
            return (Object[]) val;
        }
        Object[] outputArray = new Object[Array.getLength(val)];
        for (int i = 0; i < outputArray.length; ++i) {
            outputArray[i] = Array.get(val, i);
        }
        return outputArray;
    }

    public static boolean isSupportedMapValue(Object value) {
        if (value == null) {
            return false;
        } else if (value instanceof Boolean) {
            return true;
        } else if (value instanceof String) {
            return true;
        } else if (value instanceof AnnotationClassValue<?>) {
            return true;
        } else if (value instanceof Enum<?>) {
            return true;
        } else if (value.getClass().isArray()) {
            return true;
        } else if (value instanceof Collection<?>) {
            return true;
        } else if (value instanceof Map) {
            return true;
        } else if (value instanceof Long) {
            return true;
        } else if (value instanceof Double) {
            return true;
        } else if (value instanceof Float) {
            return true;
        } else if (value instanceof Byte) {
            return true;
        } else if (value instanceof Short) {
            return true;
        } else if (value instanceof Character) {
            return true;
        } else if (value instanceof Number) {
            return true;
        } else if (value instanceof AnnotationValue<?>) {
            return true;
        } else if (value instanceof EvaluatedExpressionReference) {
            return true;
        } else if (value instanceof Class<?>) {
            // The class should be added as AnnotationClassValue
            return false;
        }
        return false;
    }

    @NonNull
    public static ExpressionDef.InvokeStaticMethod invokeLoadClassValueMethod(ClassTypeDef declaringType,
                                                                              Map<String, MethodDef> loadTypeMethods,
                                                                              AnnotationClassValue<?> acv) {
        final String typeName = acv.getName();

        final MethodDef loadTypeGeneratorMethod = loadTypeMethods.computeIfAbsent(typeName, type -> {

            final String methodName = LOAD_CLASS_PREFIX + loadTypeMethods.size();

            // This logic will generate a method such as the following, allowing non-dynamic classloading:
            //
            // AnnotationClassValue $micronaut_load_class_value_0() {
            //     try {
            //          return new AnnotationClassValue(test.MyClass.class);
            //     } catch(Throwable e) {
            //          return new AnnotationClassValue("test.MyClass");
            //     }
            // }

            return MethodDef.builder(methodName)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                .returns(TYPE_ANNOTATION_CLASS_VALUE)
                .buildStatic(methodParameters -> StatementDef.doTry(
                    TYPE_ANNOTATION_CLASS_VALUE.instantiate(
                        CONSTRUCTOR_CLASS_VALUE_WITH_CLASS,
                        ExpressionDef.constant(TypeDef.of(typeName))
                    ).returning()
                ).doCatch(Throwable.class, exceptionVar -> TYPE_ANNOTATION_CLASS_VALUE.instantiate(
                    CONSTRUCTOR_CLASS_VALUE,
                    ExpressionDef.constant(typeName)
                ).returning()));
        });

        return declaringType.invokeStatic(loadTypeGeneratorMethod);
    }

    public static MethodDef getAnnotationMetadataMethodDef(ClassTypeDef owningType, AnnotationMetadata am) {
        return MethodDef.builder("getAnnotationMetadata").returns(TYPE_ANNOTATION_METADATA)
            .addModifiers(Modifier.PUBLIC)
            .build((aThis, methodParameters) -> {
                // in order to save memory of a method doesn't have any annotations of its own but merely references class metadata
                // then we set up an annotation metadata reference from the method to the class (or inherited method) metadata
                AnnotationMetadata annotationMetadata = am.getTargetAnnotationMetadata();
                if (annotationMetadata.isEmpty()) {
                    return AnnotationMetadataGenUtils.EMPTY_METADATA.returning();
                }
                if (annotationMetadata instanceof AnnotationMetadataReference reference) {
                    return annotationMetadataReference(reference).returning();
                }
                return owningType.getStaticField(FIELD_ANNOTATION_METADATA).returning();
            });
    }

    @Nullable
    public static FieldDef createAnnotationMetadataField(ClassTypeDef targetType,
                                                         AnnotationMetadata annotationMetadata,
                                                         Map<String, MethodDef> loadTypeMethods) {
        if (annotationMetadata instanceof AnnotationMetadataReference) {
            return null;
        }
        FieldDef.FieldDefBuilder fieldDefBuilder = FieldDef.builder(FIELD_ANNOTATION_METADATA_NAME, TYPE_ANNOTATION_METADATA)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC);

        ExpressionDef initializer;
        annotationMetadata = annotationMetadata.getTargetAnnotationMetadata();
        if (annotationMetadata.isEmpty()) {
            initializer = AnnotationMetadataGenUtils.EMPTY_METADATA;
        } else if (annotationMetadata instanceof MutableAnnotationMetadata mutableAnnotationMetadata) {
            initializer = AnnotationMetadataGenUtils.instantiateNewMetadata(
                targetType,
                mutableAnnotationMetadata,
                loadTypeMethods
            );
        } else if (annotationMetadata instanceof AnnotationMetadataHierarchy annotationMetadataHierarchy) {
            initializer = AnnotationMetadataGenUtils.instantiateNewMetadataHierarchy(targetType, annotationMetadataHierarchy, loadTypeMethods);
        } else {
            throw new IllegalStateException("Unknown annotation metadata: " + annotationMetadata);
        }
        fieldDefBuilder.initializer(initializer);

        return fieldDefBuilder.build();
    }

    public static void writeAnnotationDefault(List<StatementDef> statements,
                                              ClassTypeDef targetClassType,
                                              AnnotationMetadata annotationMetadata,
                                              Map<String, MethodDef> loadTypeMethods) {
        annotationMetadata = annotationMetadata.getTargetAnnotationMetadata();
        if (annotationMetadata.isEmpty()) {
            return;
        }
        if (annotationMetadata instanceof AnnotationMetadataHierarchy annotationMetadataHierarchy) {
            annotationMetadata = annotationMetadataHierarchy.merge();
        }
        if (annotationMetadata instanceof MutableAnnotationMetadata mutableAnnotationMetadata) {
            AnnotationMetadataGenUtils.writeAnnotationDefaults(
                statements,
                targetClassType,
                mutableAnnotationMetadata,
                loadTypeMethods
            );
        } else {
            throw new IllegalStateException("Unknown annotation metadata: " + annotationMetadata);
        }
    }

    private static <T> ExpressionDef stringMapOf(ClassTypeDef declaringType,
                                                 Map<? extends CharSequence, T> annotationData,
                                                 Map<String, MethodDef> loadTypeMethods) {
        return ExpressionsUtils.stringMapOf(
            annotationData,
            true,
            null,
            AnnotationMetadataGenUtils::isSupportedMapValue,
            o -> asValueExpression(declaringType, o, loadTypeMethods)
        );
    }

}
