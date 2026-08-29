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
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ReflectionUtils;
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
import java.util.LinkedHashMap;
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

    private AnnotationMetadataQualifier(String annotationName,
                                        String annotationSimpleName,
                                        @Nullable AnnotationValue<Annotation> value) {
        this.annotationName = annotationName;
        this.annotationSimpleName = annotationSimpleName;
        this.qualifierAnn = value;
    }

    static <T> AnnotationMetadataQualifier<T> fromType(AnnotationMetadata annotationMetadata,
                                                       Class<? extends Annotation> annotationType) {
        return new AnnotationMetadataQualifier<>(
            annotationType.getName(),
            annotationType.getSimpleName(),
            resolveBindingAnnotationValue(annotationMetadata, annotationType.getName())
        );
    }

    static <T> AnnotationMetadataQualifier<T> fromTypeName(AnnotationMetadata annotationMetadata,
                                                           String annotationTypeName) {
        return new AnnotationMetadataQualifier<>(
            annotationTypeName,
            NameUtils.getSimpleName(annotationTypeName),
            resolveBindingAnnotationValue(annotationMetadata, annotationTypeName)
        );
    }

    static <T extends Annotation> AnnotationMetadataQualifier<T> fromValue(AnnotationMetadata annotationMetadata,
                                                                           AnnotationValue<T> annotationValue) {
        return new AnnotationMetadataQualifier<>(
            annotationValue.getAnnotationName(),
            NameUtils.getSimpleName(annotationValue.getAnnotationName()),
            resolveBindingAnnotationValue(annotationMetadata, annotationValue.getAnnotationName(), annotationValue.getValues())
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
            new AnnotationValue<>(annotationType.getName(), bindingValues)
        );
    }

    /**
     * The members of an annotation instance that take part in the comparison of two qualifiers, or {@code null}
     * when one of them cannot be read.
     */
    @Nullable
    private static Map<CharSequence, Object> resolveAnnotationBindingValues(Annotation annotation) {
        Map<CharSequence, Object> bindingValues = new LinkedHashMap<>();
        for (Method member : annotation.annotationType().getDeclaredMethods()) {
            if (member.getParameterCount() != 0 || member.isSynthetic() || member.isAnnotationPresent(NonBinding.class)) {
                continue;
            }
            Object value = readMember(annotation, member);
            if (value == null) {
                return null;
            }
            bindingValues.put(member.getName(), asMemberValue(value));
        }
        return bindingValues;
    }

    /**
     * A member read off an annotation instance, or {@code null} when it cannot be read: the annotation type is
     * not open to this module, or the instance would not answer for the member. A member of an annotation is
     * never null itself, so the two do not overlap.
     *
     * <p>A qualifier that cannot read one of its members cannot compare it either, and it goes on qualifying by
     * its type alone rather than by the members it did manage to read.</p>
     */
    @Nullable
    private static Object readMember(Annotation annotation, Method member) {
        try {
            return ReflectionUtils.invokeInaccessibleMethod(annotation, member);
        } catch (RuntimeException e) {
            // the member of an annotation this module cannot read is a member it cannot compare
            return null;
        }
    }

    /**
     * A member value read off an annotation instance, as the metadata of an annotated element records it: a class
     * is recorded as an {@link AnnotationClassValue}, an enum by the name of its constant and a nested annotation
     * as an {@link AnnotationValue}, so that the two sides of the comparison are the same values.
     */
    private static Object asMemberValue(Object value) {
        if (value instanceof Class<?> type) {
            return new AnnotationClassValue<>(type);
        }
        if (value instanceof Enum<?> constant) {
            return constant.name();
        }
        if (value instanceof Annotation nested) {
            return asAnnotationValue(nested);
        }
        if (value instanceof Class<?>[] types) {
            AnnotationClassValue<?>[] classValues = new AnnotationClassValue[types.length];
            for (int i = 0; i < types.length; i++) {
                classValues[i] = new AnnotationClassValue<>(types[i]);
            }
            return classValues;
        }
        if (value instanceof Enum<?>[] constants) {
            String[] names = new String[constants.length];
            for (int i = 0; i < constants.length; i++) {
                names[i] = constants[i].name();
            }
            return names;
        }
        if (value instanceof Annotation[] nested) {
            AnnotationValue<?>[] annotationValues = new AnnotationValue[nested.length];
            for (int i = 0; i < nested.length; i++) {
                annotationValues[i] = asAnnotationValue(nested[i]);
            }
            return annotationValues;
        }
        return value;
    }

    private static AnnotationValue<Annotation> asAnnotationValue(Annotation nested) {
        return new AnnotationValue<>(nested.annotationType().getName(), resolveAllValues(nested));
    }

    /**
     * Every member of a nested annotation. {@link NonBinding} excludes a member from the comparison of the
     * qualifier it is declared on, which is the annotation on the element, so a nested one is read whole.
     */
    private static Map<CharSequence, Object> resolveAllValues(Annotation annotation) {
        Map<CharSequence, Object> values = new LinkedHashMap<>();
        for (Method member : annotation.annotationType().getDeclaredMethods()) {
            if (member.getParameterCount() != 0 || member.isSynthetic()) {
                continue;
            }
            Object value = readMember(annotation, member);
            if (value != null) {
                values.put(member.getName(), asMemberValue(value));
            }
        }
        return values;
    }

    @Override
    public boolean doesQualify(Class<T> beanType, BeanType<T> candidate) {
        if (QualifierUtils.match(candidate, this)) {
            return true;
        }
        if (matchByAnnotationMetadata(candidate)) {
            return true;
        }
        return QualifierUtils.matchByCandidateName(candidate, beanType, annotationSimpleName);
    }

    @Override
    public boolean doesQualify(Class<T> beanType, QualifiedBeanType<T> candidate) {
        if (QualifierUtils.matchQualified(candidate, this)) {
            return true;
        }
        if (matchByAnnotationMetadata(candidate)) {
            return true;
        }
        return QualifierUtils.matchByCandidateName(candidate, beanType, annotationSimpleName);
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
        // declared from the one set of defaults both sides are given here
        Map<CharSequence, Object> defaults = AnnotationMetadataSupport.getDefaultValues(annotationName);
        return withDefaults(qualifierAnn.getValues(), defaults)
            .matches(withDefaults(resolveBindingValues(candidateMetadata, candidateMetadata.getValues(annotationName)), defaults));
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

    @Nullable
    private static <K extends Annotation> AnnotationValue<K> resolveBindingAnnotationValue(AnnotationMetadata annotationMetadata,
                                                                                           String annotationName,
                                                                                           Map<CharSequence, Object> values) {
        Map<CharSequence, Object> bindingValues = resolveBindingValues(annotationMetadata, values);
        if (CollectionUtils.isNotEmpty(bindingValues)) {
            return new AnnotationValue<>(annotationName, bindingValues);
        }
        return null;
    }

    private static Map<CharSequence, Object> resolveBindingValues(AnnotationMetadata annotationMetadata,
                                                                  Map<CharSequence, Object> values) {
        Set<String> nonBinding = resolveNonBindingMembers(annotationMetadata);
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

    private static Set<String> resolveNonBindingMembers(AnnotationMetadata annotationMetadata) {
        String[] nonBindingArray = AnnotationUtil.resolveNonBindingMembers(annotationMetadata);
        return ArrayUtils.isNotEmpty(nonBindingArray) ? new LinkedHashSet<>(Arrays.asList(nonBindingArray)) : Collections.emptySet();
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
