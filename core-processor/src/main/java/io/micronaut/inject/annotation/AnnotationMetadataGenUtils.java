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
import io.micronaut.inject.writer.GenUtils;
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
import java.util.function.Function;

/**
 * Responsible for writing class files that are instances of {@link AnnotationMetadata}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public final class AnnotationMetadataGenUtils {

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

    private AnnotationMetadataGenUtils() {
    }

    /**
     * Instantiate new metadata expression.
     *
     * @param annotationMetadata         The annotation metadata
     * @param loadClassValueExpressionFn The load type expression fn
     * @return The expression
     */
    @NonNull
    public static ExpressionDef instantiateNewMetadata(MutableAnnotationMetadata annotationMetadata,
                                                       Function<String, ExpressionDef> loadClassValueExpressionFn) {
        return instantiateInternal(annotationMetadata, loadClassValueExpressionFn);
    }

    /**
     * Instantiate new metadata hierarchy expression.
     *
     * @param hierarchy                  The annotation metadata hierarchy
     * @param loadClassValueExpressionFn The load type expression fn
     * @return The expression
     */
    @NonNull
    public static ExpressionDef instantiateNewMetadataHierarchy(AnnotationMetadataHierarchy hierarchy,
                                                                Function<String, ExpressionDef> loadClassValueExpressionFn) {

        if (hierarchy.isEmpty()) {
            return emptyMetadata();
        }
        List<AnnotationMetadata> notEmpty = CollectionUtils.iterableToList(hierarchy)
            .stream().filter(h -> !h.isEmpty()).toList();
        if (notEmpty.size() == 1) {
            return pushNewAnnotationMetadataOrReference(notEmpty.get(0), loadClassValueExpressionFn);
        }

        return TYPE_DEFAULT_ANNOTATION_METADATA_HIERARCHY.instantiate(
            CONSTRUCTOR_ANNOTATION_METADATA_HIERARCHY,

            TYPE_ANNOTATION_METADATA.array().instantiate(
                pushNewAnnotationMetadataOrReference(hierarchy.getRootMetadata(), loadClassValueExpressionFn),
                pushNewAnnotationMetadataOrReference(hierarchy.getDeclaredMetadata(), loadClassValueExpressionFn)
            )
        );
    }

    /**
     * The annotation metadata reference expression.
     *
     * @param annotationMetadata The annotation metadata
     * @return The expression
     */
    @NonNull
    public static ExpressionDef annotationMetadataReference(AnnotationMetadataReference annotationMetadata) {
        return ClassTypeDef.of(annotationMetadata.getClassName()).getStaticField(FIELD_ANNOTATION_METADATA);
    }

    /**
     * The empty annotation metadata expression.
     *
     * @return The expression
     */
    @NonNull
    public static ExpressionDef emptyMetadata() {
        return TYPE_ANNOTATION_METADATA.getStaticField("EMPTY_METADATA", TYPE_ANNOTATION_METADATA);
    }

    /**
     * Create a new load class value expression function.
     *
     * @param declaringType   The declaring type
     * @param loadTypeMethods The load type methods
     * @return The function
     */
    @NonNull
    public static Function<String, ExpressionDef> createLoadClassValueExpressionFn(ClassTypeDef declaringType,
                                                                                   Map<String, MethodDef> loadTypeMethods) {
        return typeName -> invokeLoadClassValueMethod(declaringType, loadTypeMethods, typeName);
    }

    /**
     * Creates a `getAnnotationMetadata` method.
     *
     * @param owningType         The owning type
     * @param annotationMetadata The annotation metadata
     * @return The new method
     */
    @NonNull
    public static MethodDef createGetAnnotationMetadataMethodDef(ClassTypeDef owningType, AnnotationMetadata annotationMetadata) {
        return MethodDef.builder("getAnnotationMetadata").returns(TYPE_ANNOTATION_METADATA)
            .addModifiers(Modifier.PUBLIC)
            .build((aThis, methodParameters) -> {
                // in order to save memory of a method doesn't have any annotations of its own but merely references class metadata
                // then we set up an annotation metadata reference from the method to the class (or inherited method) metadata
                AnnotationMetadata targetAnnotationMetadata = annotationMetadata.getTargetAnnotationMetadata();
                if (targetAnnotationMetadata.isEmpty()) {
                    return AnnotationMetadataGenUtils.EMPTY_METADATA.returning();
                }
                if (targetAnnotationMetadata instanceof AnnotationMetadataReference reference) {
                    return annotationMetadataReference(reference).returning();
                }
                return owningType.getStaticField(FIELD_ANNOTATION_METADATA).returning();
            });
    }

    /**
     * Create annotation metadata field and initialize it to the metadata provided.
     *
     * @param annotationMetadata         The annotation metadata
     * @param loadClassValueExpressionFn The function to get the class value
     * @return The new field
     */
    @Nullable
    public static FieldDef createAnnotationMetadataFieldAndInitialize(AnnotationMetadata annotationMetadata,
                                                                      Function<String, ExpressionDef> loadClassValueExpressionFn) {
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
            initializer = AnnotationMetadataGenUtils.instantiateNewMetadata(mutableAnnotationMetadata, loadClassValueExpressionFn);
        } else if (annotationMetadata instanceof AnnotationMetadataHierarchy annotationMetadataHierarchy) {
            initializer = AnnotationMetadataGenUtils.instantiateNewMetadataHierarchy(annotationMetadataHierarchy, loadClassValueExpressionFn);
        } else {
            throw new IllegalStateException("Unknown annotation metadata: " + annotationMetadata);
        }
        fieldDefBuilder.initializer(initializer);

        return fieldDefBuilder.build();
    }

    /**
     * Adds the annotation metadata defaults statement/s.
     *
     * @param statements                 The statements
     * @param annotationMetadata         The annotation metadata
     * @param loadClassValueExpressionFn The load type expression fn
     */
    public static void addAnnotationDefaults(List<StatementDef> statements,
                                             AnnotationMetadata annotationMetadata,
                                             Function<String, ExpressionDef> loadClassValueExpressionFn) {
        annotationMetadata = annotationMetadata.getTargetAnnotationMetadata();
        if (annotationMetadata.isEmpty()) {
            return;
        }
        if (annotationMetadata instanceof AnnotationMetadataHierarchy annotationMetadataHierarchy) {
            annotationMetadata = annotationMetadataHierarchy.merge();
        }
        if (annotationMetadata instanceof MutableAnnotationMetadata mutableAnnotationMetadata) {
            AnnotationMetadataGenUtils.addAnnotationDefaults(
                statements,
                mutableAnnotationMetadata,
                loadClassValueExpressionFn
            );
        } else {
            throw new IllegalStateException("Unknown annotation metadata: " + annotationMetadata);
        }
    }

    @NonNull
    private static ExpressionDef.InvokeStaticMethod invokeLoadClassValueMethod(ClassTypeDef declaringType,
                                                                               Map<String, MethodDef> loadTypeMethods,
                                                                               String typeName) {
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

    private static void addAnnotationDefaults(List<StatementDef> statements,
                                              MutableAnnotationMetadata annotationMetadata,
                                              Function<String, ExpressionDef> loadClassValueExpressionFn) {
        final Map<String, Map<CharSequence, Object>> annotationDefaultValues = annotationMetadata.annotationDefaultValues;

        if (CollectionUtils.isNotEmpty(annotationDefaultValues)) {
            addAnnotationDefaultsInternal(statements, annotationDefaultValues, new HashSet<>(), loadClassValueExpressionFn);
        }
        if (annotationMetadata.annotationRepeatableContainer != null && !annotationMetadata.annotationRepeatableContainer.isEmpty()) {
            Map<String, String> annotationRepeatableContainer = new LinkedHashMap<>(annotationMetadata.annotationRepeatableContainer);
            AnnotationMetadataSupport.getCoreRepeatableAnnotationsContainers().forEach(annotationRepeatableContainer::remove);
            AnnotationMetadataSupport.registerRepeatableAnnotations(annotationRepeatableContainer);
            if (!annotationRepeatableContainer.isEmpty()) {
                statements.add(
                    TYPE_DEFAULT_ANNOTATION_METADATA.invokeStatic(
                        METHOD_REGISTER_REPEATABLE_ANNOTATIONS,
                        stringMapOf(annotationRepeatableContainer, loadClassValueExpressionFn)
                    )
                );
            }
        }
    }

    private static void addAnnotationDefaultsInternal(List<StatementDef> statements,
                                                      Map<String, Map<CharSequence, Object>> annotationDefaultValues,
                                                      Set<String> writtenAnnotations,
                                                      Function<String, ExpressionDef> loadClassValueExpressionFn) {
        for (Map.Entry<String, Map<CharSequence, Object>> entry : annotationDefaultValues.entrySet()) {
            addAnnotationDefaultsInternal(statements,
                writtenAnnotations,
                entry.getKey(),
                entry.getValue(),
                loadClassValueExpressionFn);
        }
    }

    @NonNull
    private static void addAnnotationDefaultsInternal(List<StatementDef> statements,
                                                      Set<String> writtenAnnotations,
                                                      String annotationName,
                                                      Map<CharSequence, Object> annotationValues,
                                                      Function<String, ExpressionDef> loadClassValueExpressionFn) {
        final boolean typeOnly = CollectionUtils.isEmpty(annotationValues);

        // skip already registered
        if (typeOnly && AnnotationMetadataSupport.getRegisteredAnnotationType(annotationName).isPresent()
            || AnnotationMetadataSupport.getCoreAnnotationDefaults().containsKey(annotationName)) {
            return;
        }

        if (!writtenAnnotations.add(annotationName)) {
            return;
        }

        for (Map.Entry<CharSequence, Object> values : annotationValues.entrySet()) {
            Object value = values.getValue();
            if (value instanceof AnnotationValue<?> annotationValue && CollectionUtils.isNotEmpty(annotationValue.getDefaultValues())) {
                addAnnotationDefaultsInternal(
                    statements,
                    writtenAnnotations,
                    annotationValue.getAnnotationName(),
                    annotationValue.getDefaultValues(),
                    loadClassValueExpressionFn
                );
            }
        }

        if (!typeOnly) {
            statements.add(
                TYPE_DEFAULT_ANNOTATION_METADATA.invokeStatic(
                    METHOD_REGISTER_ANNOTATION_DEFAULTS,
                    loadClassValueExpressionFn.apply(annotationName),
                    stringMapOf(annotationValues, loadClassValueExpressionFn)
                )
            );
        } else {
            statements.add(
                TYPE_DEFAULT_ANNOTATION_METADATA.invokeStatic(
                    METHOD_REGISTER_ANNOTATION_TYPE,
                    loadClassValueExpressionFn.apply(annotationName)
                )
            );
        }
        writtenAnnotations.add(annotationName);
    }

    @NonNull
    private static ExpressionDef instantiateInternal(MutableAnnotationMetadata annotationMetadata,
                                                     Function<String, ExpressionDef> loadClassValueExpressionFn) {
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
                pushCreateAnnotationData(annotationMetadata.declaredAnnotations, annotationMetadata.getSourceRetentionAnnotations(), loadClassValueExpressionFn),
                // 2nd argument: the declared stereotypes
                pushCreateAnnotationData(annotationMetadata.declaredStereotypes, annotationMetadata.getSourceRetentionAnnotations(), loadClassValueExpressionFn),
                // 3rd argument: all stereotypes
                pushCreateAnnotationData(annotationMetadata.allStereotypes, annotationMetadata.getSourceRetentionAnnotations(), loadClassValueExpressionFn),
                // 4th argument: all annotations
                pushCreateAnnotationData(annotationMetadata.allAnnotations, annotationMetadata.getSourceRetentionAnnotations(), loadClassValueExpressionFn),
                // 5th argument: annotations by stereotype,
                GenUtils.stringMapOf(annotationsByStereotype, false, Collections.emptyList(), GenUtils::listOfString),
                // 6th argument: has property expressions,
                ExpressionDef.constant(annotationMetadata.hasPropertyExpressions()),
                // 7th argument: has evaluated expressions
                ExpressionDef.constant(annotationMetadata.hasEvaluatedExpressions())
            );
    }

    @NonNull
    private static ExpressionDef pushCreateAnnotationData(Map<String, Map<CharSequence, Object>> annotationData,
                                                          Set<String> sourceRetentionAnnotations,
                                                          Function<String, ExpressionDef> loadClassValueExpressionFn) {
        if (annotationData != null) {
            annotationData = new LinkedHashMap<>(annotationData);
            for (String sourceRetentionAnnotation : sourceRetentionAnnotations) {
                annotationData.remove(sourceRetentionAnnotation);
            }
        }

        return GenUtils.stringMapOf(annotationData, false, Collections.emptyMap(),
            attributes -> GenUtils.stringMapOf(attributes, true, null,
                value -> asValueExpression(value, loadClassValueExpressionFn)));
    }

    @NonNull
    private static ExpressionDef asValueExpression(Object value,
                                                   Function<String, ExpressionDef> loadClassValueExpressionFn) {
        if (value == null) {
            throw new IllegalStateException("Cannot map null value");
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
                return loadClassValueExpressionFn.apply(acv.getName());
            }
        }
        if (value.getClass().isArray()) {
            Class<?> arrayComponentType = value.getClass().getComponentType();
            if (Enum.class.isAssignableFrom(arrayComponentType)) {
                // Express enums as strings
                arrayComponentType = String.class;
            }
            return TypeDef.of(arrayComponentType).array().instantiate(Arrays.stream(getArray(value))
                .map(v -> asValueExpression(v, loadClassValueExpressionFn))
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
                .instantiate(collection.stream().map(i -> asValueExpression(i, loadClassValueExpressionFn)).toList());
        }
        if (value instanceof AnnotationValue<?> data) {
            return ClassTypeDef.of(AnnotationValue.class)
                .instantiate(
                    CONSTRUCTOR_ANNOTATION_VALUE_AND_MAP,
                    ExpressionDef.constant(data.getAnnotationName()),
                    stringMapOf(data.getValues(), loadClassValueExpressionFn),
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

    @NonNull
    private static <T> ExpressionDef stringMapOf(Map<? extends CharSequence, T> annotationData,
                                                 Function<String, ExpressionDef> loadClassValueExpressionFn) {
        return GenUtils.stringMapOf(
            annotationData,
            true,
            null,
            AnnotationMetadataGenUtils::isSupportedMapValue,
            o -> asValueExpression(o, loadClassValueExpressionFn)
        );
    }

    @NonNull
    private static ExpressionDef pushNewAnnotationMetadataOrReference(AnnotationMetadata annotationMetadata,
                                                                      Function<String, ExpressionDef> loadClassValueExpressionFn) {
        annotationMetadata = annotationMetadata.getTargetAnnotationMetadata();
        if (annotationMetadata instanceof AnnotationMetadataHierarchy annotationMetadataHierarchy) {
            // Synthetic property getters / setters can consist of field + (setter / getter) annotation hierarchy
            annotationMetadata = MutableAnnotationMetadata.of(annotationMetadataHierarchy);
        }
        if (annotationMetadata.isEmpty()) {
            return emptyMetadata();
        } else if (annotationMetadata instanceof MutableAnnotationMetadata mutableAnnotationMetadata) {
            return instantiateNewMetadata(mutableAnnotationMetadata, loadClassValueExpressionFn);
        } else if (annotationMetadata instanceof AnnotationMetadataReference reference) {
            return annotationMetadataReference(reference);
        } else {
            throw new IllegalStateException("Unknown annotation metadata: " + annotationMetadata);
        }
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

    private static boolean isSupportedMapValue(Object value) {
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

}
