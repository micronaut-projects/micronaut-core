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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.CollectionUtils;

import java.lang.annotation.Annotation;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        super(declaredAnnotations,
              declaredStereotypes,
              allStereotypes,
              allAnnotations,
              annotationsByStereotype,
              hasPropertyExpressions);
        this.hasPropertyExpressions = hasPropertyExpressions;
    }

    public static MutableAnnotationMetadata of(AnnotationMetadata annotationMetadata) {
        if (annotationMetadata.isEmpty()) {
            return new MutableAnnotationMetadata();
        }
        annotationMetadata = annotationMetadata.unwrap();
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
    protected void addAnnotationMetadata(DefaultAnnotationMetadata annotationMetadata) {
        hasPropertyExpressions |= annotationMetadata.hasPropertyExpressions();
        super.addAnnotationMetadata(annotationMetadata);
    }

    @Override
    public boolean hasPropertyExpressions() {
        return hasPropertyExpressions;
    }

    @Override
    public MutableAnnotationMetadata clone() {
        final MutableAnnotationMetadata cloned = new MutableAnnotationMetadata(
                declaredAnnotations != null ? cloneMapOfMapValue(declaredAnnotations) : null,
                declaredStereotypes != null ? cloneMapOfMapValue(declaredStereotypes) : null,
                allStereotypes != null ? cloneMapOfMapValue(allStereotypes) : null,
                allAnnotations != null ? cloneMapOfMapValue(allAnnotations) : null,
                annotationsByStereotype != null ? cloneMapOfListValue(annotationsByStereotype) : null,
                hasPropertyExpressions
        );
        if (annotationDefaultValues != null) {
            cloned.annotationDefaultValues = new LinkedHashMap<>(annotationDefaultValues);
        }
        if (repeated != null) {
            cloned.repeated = new HashMap<>(repeated);
        }
        if (sourceRetentionAnnotations != null) {
            cloned.sourceRetentionAnnotations = new HashSet<>(sourceRetentionAnnotations);
        }
        if (annotationDefaultValues != null) {
            cloned.annotationDefaultValues = cloneMapOfMapValue(annotationDefaultValues);
        }
        cloned.hasPropertyExpressions = hasPropertyExpressions;
        return cloned;
    }

    @NonNull
    @Override
    public Map<String, Object> getDefaultValues(@NonNull String annotation) {
        Map<String, Object> values = super.getDefaultValues(annotation);
        if (values.isEmpty() && annotationDefaultValues != null) {
            final Map<CharSequence, Object> compileTimeDefaults = annotationDefaultValues.get(annotation);
            if (compileTimeDefaults != null && !compileTimeDefaults.isEmpty()) {
                return compileTimeDefaults.entrySet().stream()
                        .collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue));
            }
        }
        return values;
    }

    @Override
    public <A extends Annotation> void removeAnnotationIf(@NonNull Predicate<AnnotationValue<A>> predicate) {
        super.removeAnnotationIf(predicate);
    }

    @Override
    public void removeAnnotation(String annotationType) {
        super.removeAnnotation(annotationType);
    }

    @Override
    public void removeStereotype(String annotationType) {
        super.removeStereotype(annotationType);
    }

    @Override
    public void addAnnotation(String annotation, Map<CharSequence, Object> values) {
        this.hasPropertyExpressions = computeHasPropertyExpressions(values, RetentionPolicy.RUNTIME);
        super.addAnnotation(annotation, values);
    }

    @Override
    public void addAnnotation(String annotation, Map<CharSequence, Object> values, RetentionPolicy retentionPolicy) {
        this.hasPropertyExpressions = computeHasPropertyExpressions(values, retentionPolicy);
        super.addAnnotation(annotation, values, retentionPolicy);
    }

    @Override
    public void addRepeatableStereotype(List<String> parents, String stereotype, AnnotationValue annotationValue) {
        Objects.requireNonNull(annotationValue, "Annotation Value cannot be null");
        this.hasPropertyExpressions = computeHasPropertyExpressions(annotationValue.getValues(), RetentionPolicy.RUNTIME);
        super.addRepeatableStereotype(parents, stereotype, annotationValue);
    }

    @Override
    public void addDeclaredRepeatableStereotype(List<String> parents, String stereotype, AnnotationValue annotationValue) {
        Objects.requireNonNull(annotationValue, "Annotation Value cannot be null");
        this.hasPropertyExpressions = computeHasPropertyExpressions(annotationValue.getValues(), RetentionPolicy.RUNTIME);
        super.addDeclaredRepeatableStereotype(parents, stereotype, annotationValue);
    }

    @Override
    public void addDeclaredAnnotation(String annotation, Map<CharSequence, Object> values) {
        this.hasPropertyExpressions = computeHasPropertyExpressions(values, RetentionPolicy.RUNTIME);
        super.addDeclaredAnnotation(annotation, values);
    }

    @Override
    public void addDeclaredAnnotation(String annotation, Map<CharSequence, Object> values, RetentionPolicy retentionPolicy) {
        this.hasPropertyExpressions = computeHasPropertyExpressions(values, retentionPolicy);
        super.addDeclaredAnnotation(annotation, values, retentionPolicy);
    }

    @Override
    public void addRepeatable(String annotationName, AnnotationValue annotationValue) {
        Objects.requireNonNull(annotationValue, "Annotation Value cannot be null");
        this.hasPropertyExpressions = computeHasPropertyExpressions(annotationValue.getValues(), RetentionPolicy.RUNTIME);
        super.addRepeatable(annotationName, annotationValue);
    }

    @Override
    public void addRepeatable(String annotationName, AnnotationValue annotationValue, RetentionPolicy retentionPolicy) {
        Objects.requireNonNull(annotationValue, "Annotation Value cannot be null");
        this.hasPropertyExpressions = computeHasPropertyExpressions(annotationValue.getValues(), retentionPolicy);
        super.addRepeatable(annotationName, annotationValue, retentionPolicy);
    }

    @Override
    public void addDeclaredRepeatable(String annotationName, AnnotationValue annotationValue) {
        Objects.requireNonNull(annotationValue, "Annotation Value cannot be null");
        this.hasPropertyExpressions = computeHasPropertyExpressions(annotationValue.getValues(), RetentionPolicy.RUNTIME);
        super.addDeclaredRepeatable(annotationName, annotationValue);
    }

    @Override
    public void addDeclaredRepeatable(String annotationName, AnnotationValue annotationValue, RetentionPolicy retentionPolicy) {
        Objects.requireNonNull(annotationValue, "Annotation Value cannot be null");
        this.hasPropertyExpressions = computeHasPropertyExpressions(annotationValue.getValues(), retentionPolicy);
        super.addDeclaredRepeatable(annotationName, annotationValue, retentionPolicy);
    }

    @Override
    public void addDeclaredStereotype(List<String> parentAnnotations, String stereotype, Map<CharSequence, Object> values) {
        super.addDeclaredStereotype(parentAnnotations, stereotype, values);
    }

    @Override
    public void addDeclaredStereotype(List<String> parentAnnotations, String stereotype, Map<CharSequence, Object> values, RetentionPolicy retentionPolicy) {
        super.addDeclaredStereotype(parentAnnotations, stereotype, values, retentionPolicy);
    }

    private boolean computeHasPropertyExpressions(Map<CharSequence, Object> values, RetentionPolicy retentionPolicy) {
        return hasPropertyExpressions || values != null && retentionPolicy == RetentionPolicy.RUNTIME && hasPropertyExpressions(values);
    }

    private boolean hasPropertyExpressions(Map<CharSequence, Object> values) {
        if (CollectionUtils.isEmpty(values)) {
            return false;
        }
        return values.values().stream().anyMatch(v -> {
            if (v instanceof CharSequence) {
                return v.toString().contains(DefaultPropertyPlaceholderResolver.PREFIX);
            } else if (v instanceof String[]) {
                return Arrays.stream((String[]) v).anyMatch(s -> s.contains(DefaultPropertyPlaceholderResolver.PREFIX));
            } else if (v instanceof AnnotationValue) {
                return hasPropertyExpressions(((AnnotationValue<?>) v).getValues());
            } else if (v instanceof AnnotationValue[]) {
                final AnnotationValue[] a = (AnnotationValue[]) v;
                if (a.length > 0) {
                    return Arrays.stream(a).anyMatch(av -> hasPropertyExpressions(av.getValues()));
                } else {
                    return false;
                }
            } else {
                return false;
            }
        });
    }
}
