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
package io.micronaut.inject.qualifiers;

import io.micronaut.context.annotation.NonBinding;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.BeanType;
import io.micronaut.inject.QualifiedBeanType;
import io.micronaut.inject.annotation.AnnotationMetadataSupport;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A {@link io.micronaut.context.Qualifier} that uses {@link AnnotationMetadata}.
 *
 * @param <T> The type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
final class AnnotationMetadataQualifier<T> extends FilteringQualifier<T> {

    final String annotationName;
    final String annotationSimpleName;
    @Nullable
    final AnnotationValue<Annotation> qualifierAnn;
    /**
     * Whether a candidate that does not carry the annotation qualifies by its name: a bean named
     * {@code V8Engine} qualifies for {@code @V8 Engine}. That is the convention for a qualifier read off an
     * injection point; a qualifier built from an annotation given to the API names the annotation it selects by.
     */
    final boolean matchByName;

    private AnnotationMetadataQualifier(String annotationName,
                                        String annotationSimpleName,
                                        @Nullable AnnotationValue<Annotation> value,
                                        boolean matchByName) {
        this.annotationName = annotationName;
        this.annotationSimpleName = annotationSimpleName;
        this.qualifierAnn = value;
        this.matchByName = matchByName;
    }

    static <T> AnnotationMetadataQualifier<T> fromType(AnnotationMetadata annotationMetadata,
                                                       Class<? extends Annotation> annotationType) {
        return new AnnotationMetadataQualifier<>(
            annotationType.getName(),
            annotationType.getSimpleName(),
            resolveBindingAnnotationValue(annotationMetadata, annotationType.getName()),
            true
        );
    }

    static <T> AnnotationMetadataQualifier<T> fromTypeName(AnnotationMetadata annotationMetadata,
                                                           String annotationTypeName) {
        return new AnnotationMetadataQualifier<>(
            annotationTypeName,
            NameUtils.getSimpleName(annotationTypeName),
            resolveBindingAnnotationValue(annotationMetadata, annotationTypeName),
            true
        );
    }

    /**
     * The qualifier for an annotation value, comparing the members it was written with.
     *
     * <p>The value names the annotation the qualifier selects by, so a candidate that does not carry it does not
     * qualify by its name the way it does for a qualifier read off an injection point.</p>
     *
     * @param annotationMetadata The metadata the value belongs to, which says which of its members do not bind
     * @param annotationValue    The annotation value
     * @param <T>                The type
     * @return The qualifier
     */
    static <T extends Annotation> AnnotationMetadataQualifier<T> fromValue(AnnotationMetadata annotationMetadata,
                                                                           AnnotationValue<T> annotationValue) {
        return fromValue(annotationMetadata, annotationValue, false);
    }

    /**
     * The qualifier a bean definition declares, for one of the qualifier annotations in its metadata: it selects
     * the way a qualifier read off an injection point does, by the annotation and by the candidate's name.
     *
     * @param annotationMetadata The metadata of the bean
     * @param annotationValue    The qualifier annotation
     * @param <T>                The type
     * @return The qualifier
     */
    static <T extends Annotation> AnnotationMetadataQualifier<T> fromDeclaredValue(AnnotationMetadata annotationMetadata,
                                                                                   AnnotationValue<T> annotationValue) {
        return fromValue(annotationMetadata, annotationValue, true);
    }

    private static <T extends Annotation> AnnotationMetadataQualifier<T> fromValue(AnnotationMetadata annotationMetadata,
                                                                                   AnnotationValue<T> annotationValue,
                                                                                   boolean matchByName) {
        return new AnnotationMetadataQualifier<>(
            annotationValue.getAnnotationName(),
            NameUtils.getSimpleName(annotationValue.getAnnotationName()),
            resolveBindingAnnotationValue(annotationMetadata, annotationValue.getAnnotationName(), annotationValue.getValues()),
            matchByName
        );
    }

    /**
     * The qualifier for an annotation instance, comparing the members it was written with.
     *
     * <p>An annotation instance answers every one of its members, so the members are read off it directly rather
     * than out of metadata, and the ones {@link NonBinding} excludes from the comparison are left out the same way
     * they are left out of a candidate's.</p>
     *
     * @param annotation The annotation
     * @param <T>        The type
     * @return The qualifier, or {@code null} when the annotation has no member to compare and so qualifies by its
     *         type alone
     */
    @Nullable
    static <T> AnnotationMetadataQualifier<T> fromAnnotation(Annotation annotation) {
        Class<? extends Annotation> annotationType = annotation.annotationType();
        Map<CharSequence, Object> bindingValues = resolveAnnotationBindingValues(annotation);
        if (bindingValues == null || bindingValues.isEmpty()) {
            return null;
        }
        return new AnnotationMetadataQualifier<>(
            annotationType.getName(),
            annotationType.getSimpleName(),
            new AnnotationValue<>(annotationType.getName(), bindingValues),
            false
        );
    }

    /**
     * The members of an annotation instance that take part in the comparison of two qualifiers, or {@code null}
     * when one of them cannot be read: the annotation type is not open to this module, or the instance would not
     * answer for the member.
     *
     * <p>The members are read as {@link AnnotationValue#of(Annotation)} records them, which is the form the
     * metadata of an annotated element records them in, so that the two sides of the comparison are the same
     * values. {@link NonBinding} excludes a member from the comparison of the qualifier it is declared on, which is
     * the annotation on the element, so a nested annotation is read whole.</p>
     *
     * <p>A qualifier that cannot read one of its members cannot compare it either, and it goes on qualifying by
     * its type alone rather than by the members it did manage to read.</p>
     */
    @Nullable
    private static Map<CharSequence, Object> resolveAnnotationBindingValues(Annotation annotation) {
        AnnotationValue<Annotation> annotationValue;
        try {
            annotationValue = AnnotationValue.of(annotation);
        } catch (IllegalStateException e) {
            // the member of an annotation this module cannot read is a member it cannot compare
            return null;
        }
        Map<CharSequence, Object> values = annotationValue.getValues();
        Map<CharSequence, Object> bindingValues = CollectionUtils.newLinkedHashMap(values.size());
        bindingValues.putAll(values);
        for (Method member : annotation.annotationType().getDeclaredMethods()) {
            if (member.isAnnotationPresent(NonBinding.class)) {
                bindingValues.remove(member.getName());
            }
        }
        return bindingValues;
    }

    @Override
    public boolean doesQualify(Class<T> beanType, BeanType<T> candidate) {
        if (QualifierUtils.match(candidate, this)) {
            return true;
        }
        if (matchByAnnotationMetadata(candidate)) {
            return true;
        }
        return matchByName && QualifierUtils.matchByCandidateName(candidate, beanType, annotationSimpleName);
    }

    @Override
    public boolean doesQualify(Class<T> beanType, QualifiedBeanType<T> candidate) {
        if (QualifierUtils.matchQualified(candidate, this)) {
            return true;
        }
        if (matchByAnnotationMetadata(candidate)) {
            return true;
        }
        return matchByName && QualifierUtils.matchByCandidateName(candidate, beanType, annotationSimpleName);
    }

    private <BT extends BeanType<T>> boolean matchByAnnotationMetadata(BT candidate) {
        AnnotationMetadata candidateMetadata = candidate.getAnnotationMetadata();
        if (qualifierAnn == null) {
            return candidateMetadata.hasAnnotation(annotationName);
        }
        if (!candidateMetadata.hasAnnotation(annotationName)) {
            return false;
        }
        // the values recorded for an element are the members it declared, so a candidate leaving a member at its
        // default records something other than a qualifier writing that default down. The two are the same
        // annotation, and AnnotationValue#matches compares them as such, filling in the members neither of them
        // declared from the one set of defaults both sides are given here. A member either side records as
        // non-binding is left out of both: the qualifier's own list was applied when it was built, and the
        // candidate's is applied here, so a qualifier built without the list still compares by the binding members.
        // The list is the one the candidate's occurrence of this annotation records, not the one on the shared
        // qualifier stereotype, which merges the lists of every qualifier the candidate carries
        Set<String> nonBinding = resolveNonBindingMembers(candidateMetadata, annotationName);
        Map<CharSequence, Object> defaults = AnnotationMetadataSupport.getDefaultValues(annotationName);
        return withDefaults(resolveBindingValues(nonBinding, qualifierAnn.getValues()), defaults)
            .matches(withDefaults(resolveBindingValues(nonBinding, candidateMetadata.getValues(annotationName)), defaults));
    }

    private AnnotationValue<Annotation> withDefaults(Map<CharSequence, Object> bindingValues,
                                                     Map<CharSequence, Object> defaults) {
        return new AnnotationValue<>(annotationName, bindingValues, defaults);
    }

    @Nullable
    private static <K extends Annotation> AnnotationValue<K> resolveBindingAnnotationValue(AnnotationMetadata annotationMetadata,
                                                                                           String annotationName) {
        return resolveBindingAnnotationValue(annotationMetadata, annotationName, annotationMetadata.getValues(annotationName));
    }

    /**
     * The binding members of an annotation value: the ones its own record of non-binding members, or failing
     * that the record of the metadata's occurrence of the annotation, does not list.
     */
    @Nullable
    private static <K extends Annotation> AnnotationValue<K> resolveBindingAnnotationValue(AnnotationMetadata annotationMetadata,
                                                                                           String annotationName,
                                                                                           Map<CharSequence, Object> values) {
        Set<String> nonBinding = values.get(AnnotationUtil.NON_BINDING_ATTRIBUTE) instanceof String[] recorded ? toSet(recorded) : Collections.emptySet();
        if (nonBinding.isEmpty()) {
            nonBinding = resolveNonBindingMembers(annotationMetadata, annotationName);
        }
        Map<CharSequence, Object> bindingValues = resolveBindingValues(nonBinding, values);
        if (CollectionUtils.isNotEmpty(bindingValues)) {
            return new AnnotationValue<>(annotationName, bindingValues);
        }
        return null;
    }

    private static Map<CharSequence, Object> resolveBindingValues(Set<String> nonBinding,
                                                                  Map<CharSequence, Object> values) {
        if (values.isEmpty() || nonBinding.isEmpty()) {
            return values;
        }
        Map<CharSequence, Object> map = new HashMap<>();
        for (Map.Entry<CharSequence, Object> entry : values.entrySet()) {
            if (!nonBinding.contains(entry.getKey().toString()) && map.put(entry.getKey(), entry.getValue()) != null) {
                throw new IllegalStateException("Duplicate key: " + entry.getKey());
            }
        }
        return map;
    }

    /**
     * The members the metadata's occurrence of the annotation records as non-binding.
     */
    private static Set<String> resolveNonBindingMembers(AnnotationMetadata annotationMetadata, String annotationName) {
        return toSet(annotationMetadata.stringValues(annotationName, AnnotationUtil.NON_BINDING_ATTRIBUTE));
    }

    private static Set<String> toSet(String[] members) {
        return ArrayUtils.isNotEmpty(members) ? new LinkedHashSet<>(Arrays.asList(members)) : Collections.emptySet();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        return QualifierUtils.annotationQualifiersEquals(this, o);
    }

    @Override
    public int hashCode() {
        return annotationName.hashCode();
    }

    @Override
    public String toString() {
        if (this.qualifierAnn != null) {
            return "@" + annotationSimpleName + "(" + qualifierAnn.getValues().entrySet().stream().map(entry -> entry.getKey() + "=" + valueToString(entry)).collect(Collectors.joining(", ")) + ")";
        }
        return "@" + annotationSimpleName;
    }

    private Object valueToString(Map.Entry<CharSequence, Object> entry) {
        final Object v = entry.getValue();
        if (v instanceof Object[] objects) {
            return Arrays.toString(objects);
        }
        return v;
    }
}
