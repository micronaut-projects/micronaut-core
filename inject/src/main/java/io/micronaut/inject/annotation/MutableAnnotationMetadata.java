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

import io.micronaut.context.env.DefaultPropertyPlaceholderResolver;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.expressions.EvaluatedExpressionReference;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A mutable various of {@link DefaultAnnotationMetadata} that is used only at build time.
 *
 * @author graemerocher
 * @since 2.4.0
 */
public class MutableAnnotationMetadata extends DefaultAnnotationMetadata {

    private boolean hasPropertyExpressions = false;
    private boolean hasEvaluatedExpressions = false;

    @Nullable
    Map<String, Map<CharSequence, Object>> annotationDefaultValues;
    @Nullable
    private Set<String> sourceRetentionAnnotations;
    @Nullable
    private Map<String, Map<CharSequence, Object>> sourceAnnotationDefaultValues;
    @Nullable
    Map<String, String> annotationRepeatableContainer;

    /**
     * Default constructor.
     */
    public MutableAnnotationMetadata() {
    }

    private MutableAnnotationMetadata(@Nullable Map<String, Map<CharSequence, Object>> declaredAnnotations,
                                      @Nullable Map<String, Map<CharSequence, Object>> declaredStereotypes,
                                      @Nullable Map<String, Map<CharSequence, Object>> allStereotypes,
                                      @Nullable Map<String, Map<CharSequence, Object>> allAnnotations,
                                      @Nullable Map<String, List<String>> annotationsByStereotype,
                                      boolean hasPropertyExpressions) {
        this(declaredAnnotations, declaredStereotypes, allStereotypes, allAnnotations, annotationsByStereotype, hasPropertyExpressions, false);
    }

    private MutableAnnotationMetadata(@Nullable Map<String, Map<CharSequence, Object>> declaredAnnotations,
                                     @Nullable Map<String, Map<CharSequence, Object>> declaredStereotypes,
                                     @Nullable Map<String, Map<CharSequence, Object>> allStereotypes,
                                     @Nullable Map<String, Map<CharSequence, Object>> allAnnotations,
                                     @Nullable Map<String, List<String>> annotationsByStereotype,
                                     boolean hasPropertyExpressions,
                                     boolean hasEvaluatedExpressions) {
        super(declaredAnnotations,
              declaredStereotypes,
              allStereotypes,
              allAnnotations,
              annotationsByStereotype,
              hasPropertyExpressions);
        this.hasPropertyExpressions = hasPropertyExpressions;
        this.hasEvaluatedExpressions = hasEvaluatedExpressions;
    }

    public static MutableAnnotationMetadata of(AnnotationMetadata annotationMetadata) {
        if (annotationMetadata.isEmpty()) {
            return new MutableAnnotationMetadata();
        }
        annotationMetadata = annotationMetadata.getTargetAnnotationMetadata();
        if (annotationMetadata instanceof AnnotationMetadataHierarchy) {
            return  ((AnnotationMetadataHierarchy) annotationMetadata).merge();
        } else if (annotationMetadata instanceof MutableAnnotationMetadata) {
            return  ((MutableAnnotationMetadata) annotationMetadata).clone();
        } else if (annotationMetadata instanceof DefaultAnnotationMetadata) {
            MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
            metadata.addAnnotationMetadata((DefaultAnnotationMetadata) annotationMetadata);
            return metadata;
        } else {
            throw new IllegalStateException("Unknown annotation metadata: " + annotationMetadata);
        }
    }

    @Override
    public boolean hasPropertyExpressions() {
        return hasPropertyExpressions;
    }

    @Override
    public boolean hasEvaluatedExpressions() {
        return hasEvaluatedExpressions;
    }

    @Override
    public MutableAnnotationMetadata clone() {
        final MutableAnnotationMetadata cloned = new MutableAnnotationMetadata(
                declaredAnnotations != null ? cloneMapOfMapValue(declaredAnnotations) : null,
                declaredStereotypes != null ? cloneMapOfMapValue(declaredStereotypes) : null,
                allStereotypes != null ? cloneMapOfMapValue(allStereotypes) : null,
                allAnnotations != null ? cloneMapOfMapValue(allAnnotations) : null,
                annotationsByStereotype != null ? cloneMapOfListValue(annotationsByStereotype) : null,
                hasPropertyExpressions,
                hasEvaluatedExpressions
        );
        if (annotationDefaultValues != null) {
            cloned.annotationDefaultValues = new LinkedHashMap<>(annotationDefaultValues);
        }
        if (annotationRepeatableContainer != null) {
            cloned.annotationRepeatableContainer = new HashMap<>(annotationRepeatableContainer);
        }
        if (sourceRetentionAnnotations != null) {
            cloned.sourceRetentionAnnotations = new HashSet<>(sourceRetentionAnnotations);
        }
        if (annotationDefaultValues != null) {
            cloned.annotationDefaultValues = cloneMapOfMapValue(annotationDefaultValues);
        }
        if (sourceAnnotationDefaultValues != null) {
            cloned.sourceAnnotationDefaultValues = cloneMapOfMapValue(sourceAnnotationDefaultValues);
        }
        cloned.hasPropertyExpressions = hasPropertyExpressions;
        cloned.hasEvaluatedExpressions = hasEvaluatedExpressions;
        return cloned;
    }

    @NonNull
    @Override
    public Map<CharSequence, Object> getDefaultValues(@NonNull String annotation) {
        Map<CharSequence, Object> values = super.getDefaultValues(annotation);
        if (!values.isEmpty() || annotationDefaultValues == null) {
            return values;
        }
        final Map<CharSequence, Object> compileTimeDefaults = annotationDefaultValues.get(annotation);
        if (compileTimeDefaults != null && !compileTimeDefaults.isEmpty()) {
            return compileTimeDefaults.entrySet().stream()
                    .collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue));
        }
        return values;
    }

    private boolean computeHasPropertyExpressions(Map<CharSequence, Object> values, RetentionPolicy retentionPolicy) {
        return hasPropertyExpressions || values != null && retentionPolicy == RetentionPolicy.RUNTIME && hasPropertyExpressions(values);
    }

    private boolean computeHasEvaluatedExpressions(Map<CharSequence, Object> values, RetentionPolicy retentionPolicy) {
        return hasEvaluatedExpressions || values != null && retentionPolicy == RetentionPolicy.RUNTIME  &&  hasEvaluatedExpressions(values);
    }

    private boolean hasPropertyExpressions(Map<CharSequence, Object> values) {
        if (CollectionUtils.isEmpty(values)) {
            return false;
        }
        return values.values().stream().anyMatch(v -> {
            if (v instanceof CharSequence) {
                return v.toString().contains(DefaultPropertyPlaceholderResolver.PREFIX);
            } else if (v instanceof String[] strings) {
                return Arrays.stream(strings).anyMatch(s -> s.contains(DefaultPropertyPlaceholderResolver.PREFIX));
            } else if (v instanceof AnnotationValue<?> annotationValue) {
                return hasPropertyExpressions(annotationValue.getValues());
            } else if (v instanceof AnnotationValue<?>[] annotationValues) {
                if (annotationValues.length > 0) {
                    return Arrays.stream(annotationValues).anyMatch(av -> hasPropertyExpressions(av.getValues()));
                } else {
                    return false;
                }
            } else {
                return false;
            }
        });
    }

    /**
     * @return The annotations that are source retention.
     */
    @Internal
    Set<String> getSourceRetentionAnnotations() {
        if (sourceRetentionAnnotations != null) {
            return Collections.unmodifiableSet(sourceRetentionAnnotations);
        }
        return Collections.emptySet();
    }

    /**
     * Adds an annotation and its member values, if the annotation already exists the data will be merged with existing
     * values replaced.
     *
     * @param annotation The annotation
     * @param values     The values
     */
    @SuppressWarnings("WeakerAccess")
    public void addAnnotation(String annotation, Map<CharSequence, Object> values) {
        addAnnotation(annotation, values, RetentionPolicy.RUNTIME);
    }


    /**
     * Adds an annotation and its member values, if the annotation already exists the data will be merged with existing
     * values replaced.
     *
     * @param annotation      The annotation
     * @param values          The values
     * @param retentionPolicy The retention policy
     */
    @SuppressWarnings("WeakerAccess")
    public void addAnnotation(String annotation, Map<CharSequence, Object> values, RetentionPolicy retentionPolicy) {
        if (annotation == null) {
            return;
        }
        if (isRepeatableAnnotationContainer(annotation)) {
            Object v = values.get(AnnotationMetadata.VALUE_MEMBER);
            if (v instanceof io.micronaut.core.annotation.AnnotationValue<?>[] annotationValues) {
                for (io.micronaut.core.annotation.AnnotationValue<?> annotationValue : annotationValues) {
                    addRepeatable(annotation, annotationValue);
                }
            } else if (v instanceof Iterable<?> iterable) {
                for (Object o : iterable) {
                    if (o instanceof io.micronaut.core.annotation.AnnotationValue<?> annotationValue) {
                        addRepeatable(annotation, annotationValue);
                    }
                }
            }
        } else {
            Map<String, Map<CharSequence, Object>> allAnnotations = getAllAnnotations();
            addAnnotation(annotation, values, null, allAnnotations, false, retentionPolicy);
        }
    }

    /**
     * Adds an annotation directly declared on the element and its member values, if the annotation already exists the
     * data will be merged with existing values replaced.
     *
     * @param annotation The annotation
     * @param values     The values
     */
    public final void addDefaultAnnotationValues(String annotation, Map<CharSequence, Object> values) {
        addDefaultAnnotationValues(annotation, values, RetentionPolicy.RUNTIME);
    }

    /**
     * Adds an annotation directly declared on the element and its member values, if the annotation already exists the
     * data will be merged with existing values replaced.
     *
     * @param annotation      The annotation
     * @param values          The values
     * @param retentionPolicy The retention policy
     */
    public final void addDefaultAnnotationValues(String annotation, Map<CharSequence, Object> values, RetentionPolicy retentionPolicy) {
        if (annotation == null) {
            return;
        }
        Map<String, Map<CharSequence, Object>> annotationDefaults;
        if (retentionPolicy == RetentionPolicy.RUNTIME) {
            annotationDefaults = this.annotationDefaultValues;
            if (annotationDefaults == null) {
                this.annotationDefaultValues = new LinkedHashMap<>();
                annotationDefaults = this.annotationDefaultValues;
            }
        } else {
            annotationDefaults = this.sourceAnnotationDefaultValues;
            if (annotationDefaults == null) {
                this.sourceAnnotationDefaultValues = new LinkedHashMap<>();
                annotationDefaults = this.sourceAnnotationDefaultValues;
            }
        }
        putValues(annotation, values, annotationDefaults);
    }

    /**
     * Adds a repeatable annotation value. If a value already exists will be added
     *
     * @param annotationName  The annotation name
     * @param annotationValue The annotation value
     */
    public void addRepeatable(String annotationName, AnnotationValue<?> annotationValue) {
        addRepeatable(annotationName, annotationValue, annotationValue.getRetentionPolicy());
    }

    /**
     * Adds a repeatable annotation value. If a value already exists will be added
     *
     * @param annotationName  The annotation name
     * @param annotationValue The annotation value
     * @param retentionPolicy The retention policy
     */
    public void addRepeatable(String annotationName, AnnotationValue<?> annotationValue, RetentionPolicy retentionPolicy) {
        if (StringUtils.isNotEmpty(annotationName) && annotationValue != null) {
            Map<String, Map<CharSequence, Object>> allAnnotations = getAllAnnotations();

            addRepeatableInternal(annotationName, annotationValue, allAnnotations, retentionPolicy);
        }
    }

    /**
     * Adds a repeatable stereotype value. If a value already exists will be added
     *
     * @param parents         The parent annotations
     * @param stereotype      The annotation name
     * @param annotationValue The annotation value
     */
    public void addRepeatableStereotype(List<String> parents, String stereotype, AnnotationValue<?> annotationValue) {
        Map<String, Map<CharSequence, Object>> allStereotypes = getAllStereotypes();
        List<String> annotationList = getAnnotationsByStereotypeInternal(stereotype);
        for (String parentAnnotation : parents) {
            if (!annotationList.contains(parentAnnotation)) {
                annotationList.add(parentAnnotation);
            }
        }
        addRepeatableInternal(stereotype, annotationValue, allStereotypes, RetentionPolicy.RUNTIME);
    }

    /**
     * Adds a repeatable declared stereotype value. If a value already exists will be added
     *
     * @param parents         The parent annotations
     * @param stereotype      The annotation name
     * @param annotationValue The annotation value
     */
    public void addDeclaredRepeatableStereotype(List<String> parents, String stereotype, AnnotationValue<?> annotationValue) {
        Map<String, Map<CharSequence, Object>> declaredStereotypes = getDeclaredStereotypesInternal();
        List<String> annotationList = getAnnotationsByStereotypeInternal(stereotype);
        for (String parentAnnotation : parents) {
            if (!annotationList.contains(parentAnnotation)) {
                annotationList.add(parentAnnotation);
            }
        }
        addRepeatableInternal(stereotype, annotationValue, declaredStereotypes, RetentionPolicy.RUNTIME);
        addRepeatableInternal(stereotype, annotationValue, getAllStereotypes(), RetentionPolicy.RUNTIME);
    }

    /**
     * Adds a repeatable annotation value. If a value already exists will be added
     *
     * @param annotationName  The annotation name
     * @param annotationValue The annotation value
     */
    public void addDeclaredRepeatable(String annotationName, AnnotationValue<?> annotationValue) {
        addDeclaredRepeatable(annotationName, annotationValue, annotationValue.getRetentionPolicy());
    }

    /**
     * Adds a repeatable annotation value. If a value already exists will be added
     *
     * @param annotationName  The annotation name
     * @param annotationValue The annotation value
     * @param retentionPolicy The retention policy
     */
    public void addDeclaredRepeatable(String annotationName, AnnotationValue<?> annotationValue, RetentionPolicy retentionPolicy) {
        if (StringUtils.isNotEmpty(annotationName) && annotationValue != null) {
            Map<String, Map<CharSequence, Object>> allAnnotations = getDeclaredAnnotationsInternal();

            addRepeatableInternal(annotationName, annotationValue, allAnnotations, retentionPolicy);

            addRepeatable(annotationName, annotationValue);
        }
    }

    /**
     * Adds a stereotype and its member values, if the annotation already exists the data will be merged with existing
     * values replaced.
     *
     * @param parentAnnotations The parent annotations
     * @param stereotype        The annotation
     * @param values            The values
     */
    @SuppressWarnings("WeakerAccess")
    public final void addStereotype(List<String> parentAnnotations, String stereotype, Map<CharSequence, Object> values) {
        addStereotype(parentAnnotations, stereotype, values, RetentionPolicy.RUNTIME);
    }


    /**
     * Adds a stereotype and its member values, if the annotation already exists the data will be merged with existing
     * values replaced.
     *
     * @param parentAnnotations The parent annotations
     * @param stereotype        The annotation
     * @param values            The values
     * @param retentionPolicy   The retention policy
     */
    @SuppressWarnings("WeakerAccess")
    public final void addStereotype(List<String> parentAnnotations, String stereotype, Map<CharSequence, Object> values, RetentionPolicy retentionPolicy) {
        if (stereotype == null) {
            return;
        }
        if (isRepeatableAnnotationContainer(stereotype)) {
            Object v = values.get(AnnotationMetadata.VALUE_MEMBER);
            if (v instanceof io.micronaut.core.annotation.AnnotationValue<?>[] annotationValues) {
                for (io.micronaut.core.annotation.AnnotationValue<?> annotationValue : annotationValues) {
                    addRepeatableStereotype(parentAnnotations, stereotype, annotationValue);
                }
            } else if (v instanceof Iterable<?> iterable) {
                for (Object o : iterable) {
                    if (o instanceof io.micronaut.core.annotation.AnnotationValue<?> annotationValue) {
                        addRepeatableStereotype(parentAnnotations, stereotype, annotationValue);
                    }
                }
            }
        } else {
            Map<String, Map<CharSequence, Object>> allStereotypes = getAllStereotypes();
            List<String> annotationList = getAnnotationsByStereotypeInternal(stereotype);
            if (!parentAnnotations.isEmpty()) {
                final String parentAnnotation = CollectionUtils.last(parentAnnotations);
                if (!annotationList.contains(parentAnnotation)) {
                    annotationList.add(parentAnnotation);
                }
            }

            // add to stereotypes
            addAnnotation(
                    stereotype,
                    values,
                    null,
                    allStereotypes,
                    false,
                    retentionPolicy
            );
        }
    }

    /**
     * Adds a stereotype and its member values, if the annotation already exists the data will be merged with existing
     * values replaced.
     *
     * @param parentAnnotations The parent annotations
     * @param stereotype        The annotation
     * @param values            The values
     */
    @SuppressWarnings("WeakerAccess")
    public void addDeclaredStereotype(List<String> parentAnnotations, String stereotype, Map<CharSequence, Object> values) {
        addDeclaredStereotype(parentAnnotations, stereotype, values, RetentionPolicy.RUNTIME);
    }

    /**
     * Adds a stereotype and its member values, if the annotation already exists the data will be merged with existing
     * values replaced.
     *
     * @param parentAnnotations The parent annotations
     * @param stereotype        The annotation
     * @param values            The values
     * @param retentionPolicy   The retention policy
     */
    @SuppressWarnings("WeakerAccess")
    public void addDeclaredStereotype(List<String> parentAnnotations, String stereotype, Map<CharSequence, Object> values, RetentionPolicy retentionPolicy) {
        if (stereotype == null) {
            return;
        }
        if (isRepeatableAnnotationContainer(stereotype)) {
            Object v = values.get(AnnotationMetadata.VALUE_MEMBER);
            if (v instanceof io.micronaut.core.annotation.AnnotationValue<?>[] annotationValues) {
                for (io.micronaut.core.annotation.AnnotationValue<?> annotationValue : annotationValues) {
                    addDeclaredRepeatableStereotype(parentAnnotations, stereotype, annotationValue);
                }
            } else if (v instanceof Iterable<?> iterable) {
                for (Object o : iterable) {
                    if (o instanceof io.micronaut.core.annotation.AnnotationValue<?> annotationValue) {
                        addDeclaredRepeatableStereotype(parentAnnotations, stereotype, annotationValue);
                    }
                }
            }
        } else {
            Map<String, Map<CharSequence, Object>> declaredStereotypes = getDeclaredStereotypesInternal();
            Map<String, Map<CharSequence, Object>> allStereotypes = getAllStereotypes();
            List<String> annotationList = getAnnotationsByStereotypeInternal(stereotype);
            if (!parentAnnotations.isEmpty()) {
                final String parentAnnotation = CollectionUtils.last(parentAnnotations);
                if (!annotationList.contains(parentAnnotation)) {
                    annotationList.add(parentAnnotation);
                }
            }

            addAnnotation(
                    stereotype,
                    values,
                    declaredStereotypes,
                    allStereotypes,
                    true,
                    retentionPolicy
            );
        }
    }

    /**
     * Adds an annotation directly declared on the element and its member values, if the annotation already exists the
     * data will be merged with existing values replaced.
     *
     * @param annotation The annotation
     * @param values     The values
     */
    public void addDeclaredAnnotation(String annotation, Map<CharSequence, Object> values) {
        addDeclaredAnnotation(annotation, values, RetentionPolicy.RUNTIME);
    }

    /**
     * Adds an annotation directly declared on the element and its member values, if the annotation already exists the
     * data will be merged with existing values replaced.
     *
     * @param annotation      The annotation
     * @param values          The values
     * @param retentionPolicy The retention policy
     */
    public void addDeclaredAnnotation(String annotation, Map<CharSequence, Object> values, RetentionPolicy retentionPolicy) {
        if (annotation == null) {
            return;
        }
        boolean hasOtherMembers = false;
        boolean repeatableAnnotationContainer = isRepeatableAnnotationContainer(annotation);
        if (isRepeatableAnnotationContainer(annotation)) {
            for (Map.Entry<CharSequence, Object> entry : values.entrySet()) {
                if (entry.getKey().equals(AnnotationMetadata.VALUE_MEMBER)) {
                    Object v = entry.getValue();
                    if (v instanceof io.micronaut.core.annotation.AnnotationValue<?>[] annotationValues) {
                        for (io.micronaut.core.annotation.AnnotationValue<?> annotationValue : annotationValues) {
                            addDeclaredRepeatable(annotation, annotationValue);
                        }
                    } else if (v instanceof Iterable<?> iterable) {
                        for (Object o : iterable) {
                            if (o instanceof io.micronaut.core.annotation.AnnotationValue<?> annotationValue) {
                                addDeclaredRepeatable(annotation, annotationValue);
                            }
                        }
                    }
                } else {
                    hasOtherMembers = true;
                }
            }
        }
        if (!repeatableAnnotationContainer || hasOtherMembers) {
            Map<String, Map<CharSequence, Object>> declaredAnnotations = getDeclaredAnnotationsInternal();
            Map<String, Map<CharSequence, Object>> allAnnotations = getAllAnnotations();
            addAnnotation(annotation, values, declaredAnnotations, allAnnotations, true, retentionPolicy);
        }
    }

    @SuppressWarnings("java:S2259") // false positive
    private void addAnnotation(String annotation,
                               Map<CharSequence, Object> values,
                               Map<String, Map<CharSequence, Object>> declaredAnnotations,
                               Map<String, Map<CharSequence, Object>> allAnnotations,
                               boolean isDeclared,
                               RetentionPolicy retentionPolicy) {
        hasPropertyExpressions = computeHasPropertyExpressions(values, retentionPolicy);
        hasEvaluatedExpressions = computeHasEvaluatedExpressions(values, retentionPolicy);

        if (isDeclared && declaredAnnotations != null) {
            putValues(annotation, values, declaredAnnotations);
        }
        putValues(annotation, values, allAnnotations);

        // Annotations with retention CLASS need not be retained at run time
        if (retentionPolicy == RetentionPolicy.SOURCE || retentionPolicy == RetentionPolicy.CLASS) {
            addSourceRetentionAnnotation(annotation);
        }
    }

    private void addSourceRetentionAnnotation(String annotation) {
        if (sourceRetentionAnnotations == null) {
            sourceRetentionAnnotations = new HashSet<>(5);
        }
        sourceRetentionAnnotations.add(annotation);
    }

    @SuppressWarnings("java:S2259")
    private void putValues(String annotation, Map<CharSequence, Object> values, Map<String, Map<CharSequence, Object>> currentAnnotationValues) {
        Map<CharSequence, Object> existing = currentAnnotationValues.get(annotation);
        boolean hasValues = CollectionUtils.isNotEmpty(values);
        if (existing != null && hasValues) {
            if (existing.isEmpty()) {
                existing = new LinkedHashMap<>();
                currentAnnotationValues.put(annotation, existing);
            }
            for (CharSequence key : values.keySet()) {
                if (!existing.containsKey(key)) {
                    existing.put(key, values.get(key));
                }
            }
        } else {
            if (!hasValues) {
                existing = existing == null ? new LinkedHashMap<>(3) : existing;
            } else {
                existing = new LinkedHashMap<>(values.size());
                existing.putAll(values);
            }
            currentAnnotationValues.put(annotation, existing);
        }
    }

    @SuppressWarnings("MagicNumber")
    private Map<String, Map<CharSequence, Object>> getAllStereotypes() {
        Map<String, Map<CharSequence, Object>> stereotypes = this.allStereotypes;
        if (stereotypes == null) {
            stereotypes = new HashMap<>(3);
            this.allStereotypes = stereotypes;
        }
        return stereotypes;
    }

    @SuppressWarnings("MagicNumber")
    private Map<String, Map<CharSequence, Object>> getDeclaredStereotypesInternal() {
        Map<String, Map<CharSequence, Object>> stereotypes = this.declaredStereotypes;
        if (stereotypes == null) {
            stereotypes = new HashMap<>(3);
            this.declaredStereotypes = stereotypes;
        }
        return stereotypes;
    }

    @SuppressWarnings("MagicNumber")
    private Map<String, Map<CharSequence, Object>> getAllAnnotations() {
        Map<String, Map<CharSequence, Object>> annotations = this.allAnnotations;
        if (annotations == null) {
            annotations = new HashMap<>(3);
            this.allAnnotations = annotations;
        }
        return annotations;
    }

    @SuppressWarnings("MagicNumber")
    private Map<String, Map<CharSequence, Object>> getDeclaredAnnotationsInternal() {
        Map<String, Map<CharSequence, Object>> annotations = this.declaredAnnotations;
        if (annotations == null) {
            annotations = new HashMap<>(3);
            this.declaredAnnotations = annotations;
        }
        return annotations;
    }

    private List<String> getAnnotationsByStereotypeInternal(String stereotype) {
        return getAnnotationsByStereotypeInternal().computeIfAbsent(stereotype, s -> new ArrayList<>());
    }

    @SuppressWarnings("MagicNumber")
    private Map<String, List<String>> getAnnotationsByStereotypeInternal() {
        Map<String, List<String>> annotations = this.annotationsByStereotype;
        if (annotations == null) {
            annotations = new HashMap<>(3);
            this.annotationsByStereotype = annotations;
        }
        return annotations;
    }

    private void addRepeatableInternal(String repeatableAnnotationContainer,
                                       AnnotationValue<?> annotationValue,
                                       Map<String, Map<CharSequence, Object>> allAnnotations,
                                       RetentionPolicy retentionPolicy) {
        hasPropertyExpressions = computeHasPropertyExpressions(annotationValue.getValues(), retentionPolicy);
        hasEvaluatedExpressions = computeHasEvaluatedExpressions(annotationValue.getValues(), retentionPolicy);

        if (annotationRepeatableContainer == null) {
            annotationRepeatableContainer = new HashMap<>(2);
        }
        annotationRepeatableContainer.put(annotationValue.getAnnotationName(), repeatableAnnotationContainer);
        if (retentionPolicy == RetentionPolicy.SOURCE) {
            addSourceRetentionAnnotation(repeatableAnnotationContainer);
        }

        Map<CharSequence, Object> values = allAnnotations.computeIfAbsent(repeatableAnnotationContainer, s -> new HashMap<>());
        Object v = values.get(AnnotationMetadata.VALUE_MEMBER);
        if (v != null) {
            if (v.getClass().isArray()) {
                Object[] array = (Object[]) v;
                Set<Object> newValues = CollectionUtils.newLinkedHashSet(array.length + 1);
                newValues.addAll(Arrays.asList(array));
                newValues.add(annotationValue);
                values.put(AnnotationMetadata.VALUE_MEMBER, newValues);
            } else if (v instanceof Collection collection) {
                collection.add(annotationValue);
            }
        } else {
            Set<Object> newValues = new LinkedHashSet<>(2);
            newValues.add(annotationValue);
            values.put(AnnotationMetadata.VALUE_MEMBER, newValues);
        }
    }

    /**
     * <p>Sets a member of the given {@link AnnotationMetadata} return a new annotation metadata instance without
     * mutating the existing.</p>
     *
     * <p>WARNING: for internal use only be the framework</p>
     *
     * @param annotationMetadata The metadata
     * @param annotationName     The annotation name
     * @param member             The member
     * @param value              The value
     * @return The metadata
     */
    @Internal
    public static MutableAnnotationMetadata mutateMember(MutableAnnotationMetadata annotationMetadata,
                                                         String annotationName,
                                                         String member,
                                                         Object value) {

        return mutateMember(annotationMetadata, annotationName, Collections.singletonMap(member, value));
    }

    @Override
    protected <T extends Annotation> AnnotationValue<T> newAnnotationValue(String annotationType, Map<CharSequence, Object> values) {
        Map<CharSequence, Object> defaultValues = null;
        if (annotationDefaultValues != null) {
            defaultValues = annotationDefaultValues.get(annotationType);
        }
        if (defaultValues == null && sourceAnnotationDefaultValues != null) {
            defaultValues = sourceAnnotationDefaultValues.get(annotationType);
        }
        if (defaultValues == null) {
            defaultValues = AnnotationMetadataSupport.getDefaultValuesOrNull(annotationType);
        }
        return new AnnotationValue<>(annotationType, values, defaultValues);
    }

    /**
     * Include the annotation metadata from the other instance of {@link DefaultAnnotationMetadata}.
     *
     * @param annotationMetadata The annotation metadata
     * @since 4.0.0
     */
    @Internal
    public void addAnnotationMetadata(DefaultAnnotationMetadata annotationMetadata) {
        hasPropertyExpressions |= annotationMetadata.hasPropertyExpressions();
        hasEvaluatedExpressions |= annotationMetadata.hasEvaluatedExpressions();
        if (annotationMetadata.declaredAnnotations != null && !annotationMetadata.declaredAnnotations.isEmpty()) {
            if (declaredAnnotations == null) {
                declaredAnnotations = new LinkedHashMap<>();
            }
            for (Map.Entry<String, Map<CharSequence, Object>> entry : annotationMetadata.declaredAnnotations.entrySet()) {
                putValues(entry.getKey(), entry.getValue(), declaredAnnotations);
            }
        }
        if (annotationMetadata.declaredStereotypes != null && !annotationMetadata.declaredStereotypes.isEmpty()) {
            if (declaredStereotypes == null) {
                declaredStereotypes = new LinkedHashMap<>();
            }
            for (Map.Entry<String, Map<CharSequence, Object>> entry : annotationMetadata.declaredStereotypes.entrySet()) {
                putValues(entry.getKey(), entry.getValue(), declaredStereotypes);
            }
        }
        if (annotationMetadata.allStereotypes != null && !annotationMetadata.allStereotypes.isEmpty()) {
            if (allStereotypes == null) {
                allStereotypes = new LinkedHashMap<>();
            }
            for (Map.Entry<String, Map<CharSequence, Object>> entry : annotationMetadata.allStereotypes.entrySet()) {
                putValues(entry.getKey(), entry.getValue(), allStereotypes);
            }
        }
        if (annotationMetadata.allAnnotations != null && !annotationMetadata.allAnnotations.isEmpty()) {
            if (allAnnotations == null) {
                allAnnotations = new LinkedHashMap<>();
            }
            for (Map.Entry<String, Map<CharSequence, Object>> entry : annotationMetadata.allAnnotations.entrySet()) {
                putValues(entry.getKey(), entry.getValue(), allAnnotations);
            }
        }
        Map<String, List<String>> source = annotationMetadata.annotationsByStereotype;
        if (source != null && !source.isEmpty()) {
            if (annotationsByStereotype == null) {
                annotationsByStereotype = new LinkedHashMap<>();
            }
            for (Map.Entry<String, List<String>> entry : source.entrySet()) {
                String ann = entry.getKey();
                List<String> prevValues = annotationsByStereotype.get(ann);
                if (prevValues == null) {
                    annotationsByStereotype.put(ann, new ArrayList<>(entry.getValue()));
                } else {
                    Set<String> prevValuesSet = new LinkedHashSet<>(prevValues);
                    prevValuesSet.addAll(entry.getValue());
                    annotationsByStereotype.put(ann, new ArrayList<>(prevValuesSet));
                }
            }
        }
    }

    /**
     * Include the annotation metadata from the other instance of {@link DefaultAnnotationMetadata}.
     *
     * @param annotationMetadata The annotation metadata
     * @since 4.0.0
     */
    @Internal
    public void addAnnotationMetadata(MutableAnnotationMetadata annotationMetadata) {
        addAnnotationMetadata((DefaultAnnotationMetadata) annotationMetadata);
        hasPropertyExpressions |= annotationMetadata.hasPropertyExpressions;
        hasEvaluatedExpressions |= annotationMetadata.hasEvaluatedExpressions;
        if (annotationMetadata.sourceRetentionAnnotations != null) {
            if (sourceRetentionAnnotations == null) {
                sourceRetentionAnnotations = new HashSet<>(annotationMetadata.sourceRetentionAnnotations);
            } else {
                sourceRetentionAnnotations.addAll(annotationMetadata.sourceRetentionAnnotations);
            }
        }
        if (annotationMetadata.annotationDefaultValues != null) {
            if (annotationDefaultValues == null) {
                annotationDefaultValues = new LinkedHashMap<>(annotationMetadata.annotationDefaultValues);
            } else {
                // No need to merge values
                annotationDefaultValues.putAll(annotationMetadata.annotationDefaultValues);
            }
        }
        if (annotationMetadata.sourceAnnotationDefaultValues != null) {
            if (sourceAnnotationDefaultValues == null) {
                sourceAnnotationDefaultValues = new LinkedHashMap<>(annotationMetadata.sourceAnnotationDefaultValues);
            } else {
                // No need to merge values
                sourceAnnotationDefaultValues.putAll(annotationMetadata.sourceAnnotationDefaultValues);
            }
        }
        if (annotationMetadata.annotationRepeatableContainer != null) {
            if (annotationRepeatableContainer == null) {
                annotationRepeatableContainer = new LinkedHashMap<>(annotationMetadata.annotationRepeatableContainer);
            } else {
                annotationRepeatableContainer.putAll(annotationMetadata.annotationRepeatableContainer);
            }
        }
    }

    /**
     * Contributes defaults to the given target.
     *
     * <p>WARNING: for internal use only be the framework</p>
     *
     * @param target The target
     * @param source The source
     */
    @Internal
    public static void contributeDefaults(AnnotationMetadata target, AnnotationMetadata source) {
        source = source.getTargetAnnotationMetadata();
        if (source instanceof AnnotationMetadataHierarchy annotationMetadataHierarchy) {
            contributeDefaults(target, annotationMetadataHierarchy);
            return;
        }
        if (target instanceof MutableAnnotationMetadata damTarget && source instanceof MutableAnnotationMetadata damSource) {
            final Map<String, Map<CharSequence, Object>> existingDefaults = damTarget.annotationDefaultValues;
            final Map<String, Map<CharSequence, Object>> additionalDefaults = damSource.annotationDefaultValues;
            if (existingDefaults != null) {
                if (additionalDefaults != null) {
                    existingDefaults.putAll(
                            additionalDefaults
                    );
                }
            } else {
                if (additionalDefaults != null) {
                    additionalDefaults.forEach(damTarget::addDefaultAnnotationValues);
                }
            }
        }
        // We don't need to contribute the default source annotation
        contributeRepeatable(target, source);
    }

    /**
     * Contributes defaults to the given target.
     *
     * <p>WARNING: for internal use only be the framework</p>
     *
     * @param target The target
     * @param source The source
     * @since 4.0.0
     */
    @Internal
    public static void contributeDefaults(AnnotationMetadata target, AnnotationMetadataHierarchy source) {
        for (AnnotationMetadata annotationMetadata : source) {
            if (annotationMetadata instanceof AnnotationMetadataReference) {
                continue;
            }
            contributeDefaults(target, annotationMetadata);
        }
    }

    /**
     * Contributes repeatable annotation metadata to the given target.
     *
     * <p>WARNING: for internal use only be the framework</p>
     *
     * @param target The target
     * @param source The source
     */
    @Internal
    public static void contributeRepeatable(AnnotationMetadata target, AnnotationMetadata source) {
        source = source.getTargetAnnotationMetadata();
        if (source instanceof AnnotationMetadataHierarchy) {
            source = ((AnnotationMetadataHierarchy) source).merge();
        }
        if (target instanceof MutableAnnotationMetadata damTarget && source instanceof MutableAnnotationMetadata damSource) {
            if (damSource.annotationRepeatableContainer != null && !damSource.annotationRepeatableContainer.isEmpty()) {
                if (damTarget.annotationRepeatableContainer == null) {
                    damTarget.annotationRepeatableContainer = new HashMap<>(damSource.annotationRepeatableContainer);
                } else {
                    damTarget.annotationRepeatableContainer.putAll(damSource.annotationRepeatableContainer);
                }
            }
        }
    }

    /**
     * <p>Sets a member of the given {@link AnnotationMetadata} return a new annotation metadata instance without
     * mutating the existing.</p>
     *
     * <p>WARNING: for internal use only be the framework</p>
     *
     * @param annotationMetadata The metadata
     * @param annotationName     The annotation name
     * @param members            The key/value set of members and values
     * @return The metadata
     */
    @Internal
    public static MutableAnnotationMetadata mutateMember(MutableAnnotationMetadata annotationMetadata,
                                                         String annotationName,
                                                         Map<CharSequence, Object> members) {
        if (StringUtils.isEmpty(annotationName)) {
            throw new IllegalArgumentException("Argument [annotationName] cannot be blank");
        }
        if (!members.isEmpty()) {
            for (Map.Entry<CharSequence, Object> entry : members.entrySet()) {
                if (StringUtils.isEmpty(entry.getKey())) {
                    throw new IllegalArgumentException("Argument [members] cannot have a blank key");
                }
                if (entry.getValue() == null) {
                    throw new IllegalArgumentException("Argument [members] cannot have a null value. Key [" + entry.getKey() + "]");
                }
            }
        }
        annotationMetadata = annotationMetadata.clone();
        annotationMetadata.addDeclaredAnnotation(annotationName, members);
        return annotationMetadata;
    }

    /**
     * Removes an annotation for the given predicate.
     *
     * @param predicate The predicate
     * @param <A>       The annotation
     */
    public <A extends Annotation> void removeAnnotationIf(@NonNull Predicate<AnnotationValue<A>> predicate) {
        removeAnnotationsIf(predicate, this.declaredAnnotations);
        removeAnnotationsIf(predicate, this.allAnnotations);
    }

    private <A extends Annotation> void removeAnnotationsIf(@NonNull Predicate<AnnotationValue<A>> predicate, Map<String, Map<CharSequence, Object>> annotations) {
        if (annotations == null) {
            return;
        }
        annotations.entrySet().removeIf(entry -> {
            final String annotationName = entry.getKey();
            if (predicate.test(newAnnotationValue(annotationName, entry.getValue()))) {
                removeFromStereotypes(annotationName);
                return true;
            }
            return false;
        });
    }

    /**
     * Removes an annotation for the given annotation type.
     *
     * @param annotationType The annotation type
     * @since 3.0.0
     */
    public void removeAnnotation(String annotationType) {
        if (annotationType == null) {
            return;
        }
        if (annotationDefaultValues != null) {
            annotationDefaultValues.remove(annotationType);
        }
        if (allAnnotations != null) {
            allAnnotations.remove(annotationType);
        }
        if (declaredAnnotations != null) {
            declaredAnnotations.remove(annotationType);
            removeFromStereotypes(annotationType);
        }
        if (annotationRepeatableContainer != null) {
            annotationRepeatableContainer.remove(annotationType);
        }
    }

    /**
     * Removes a stereotype annotation for the given annotation type.
     *
     * @param annotationType The annotation type
     * @since 3.0.0
     */
    public void removeStereotype(String annotationType) {
        if (annotationType == null) {
            return;
        }
        if (annotationsByStereotype == null || annotationsByStereotype.remove(annotationType) == null) {
            return;
        }
        if (allStereotypes != null) {
            allStereotypes.remove(annotationType);
        }
        if (declaredStereotypes != null) {
            declaredStereotypes.remove(annotationType);
        }
        final Iterator<Map.Entry<String, List<String>>> i = annotationsByStereotype.entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry<String, List<String>> entry = i.next();
            final List<String> value = entry.getValue();
            if (value.remove(annotationType) && value.isEmpty()) {
                i.remove();
            }
        }
    }

    private void removeFromStereotypes(String annotationType) {
        if (annotationsByStereotype == null || annotationsByStereotype.isEmpty()) {
            return;
        }
        final Iterator<Map.Entry<String, List<String>>> i = annotationsByStereotype.entrySet().iterator();
        Set<String> removeNext = new LinkedHashSet<>();
        while (i.hasNext()) {
            final Map.Entry<String, List<String>> entry = i.next();
            final String stereotypeName = entry.getKey();
            final List<String> value = entry.getValue();
            if (value.remove(annotationType)) {
                if (value.isEmpty()) {
                    removeNext.add(stereotypeName);
                    i.remove();
                    if (allStereotypes != null) {
                        this.allStereotypes.remove(stereotypeName);
                    }
                    if (declaredStereotypes != null) {
                        this.declaredStereotypes.remove(stereotypeName);
                    }
                    if (annotationDefaultValues != null) {
                        annotationDefaultValues.remove(stereotypeName);
                    }
                    removeNext.add(stereotypeName);
                }
            }
        }
        for (String stereotype : removeNext) {
            removeFromStereotypes(stereotype);
        }
    }

    private boolean isRepeatableAnnotationContainer(String annotation) {
        return annotationRepeatableContainer != null && annotationRepeatableContainer.containsValue(annotation);
    }

    @Override
    protected String findRepeatableAnnotationContainerInternal(String annotation) {
        if (annotationRepeatableContainer != null) {
            String repeatedName = annotationRepeatableContainer.get(annotation);
            if (repeatedName != null) {
                return repeatedName;
            }
        }
        return AnnotationMetadataSupport.getRepeatableAnnotation(annotation);
    }

    private boolean hasEvaluatedExpressions(Map<CharSequence, Object> annotationValues) {
        if (CollectionUtils.isEmpty(annotationValues)) {
            return false;
        }

        return annotationValues.values().stream().anyMatch(value -> {
            if (value instanceof EvaluatedExpressionReference) {
                return true;
            } else if (value instanceof AnnotationValue av) {
                return hasEvaluatedExpressions(av.getValues());
            } else if (value instanceof AnnotationValue[] avArray) {
                return Arrays.stream(avArray)
                           .map(AnnotationValue::getValues)
                           .anyMatch(this::hasEvaluatedExpressions);
            } else {
                return false;
            }
        });
    }
}
