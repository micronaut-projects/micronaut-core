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
package io.micronaut.aop;

import io.micronaut.aop.internal.InterceptorBindings;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.ObjectUtils;
import io.micronaut.inject.BeanType;
import io.micronaut.inject.qualifiers.FilteringQualifier;
import io.micronaut.inject.qualifiers.InterceptorBindingQualifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Qualifies beans by their interceptor bindings the way the interceptor registry selects the interceptors of an
 * interception point, for beans of any type.
 *
 * <p>A candidate qualifies when it declares at least one interceptor binding of the given kind and every one of
 * them applies at the interception point, which may bind more:</p>
 *
 * <ul>
 *     <li>a binding which does not bind members applies wherever the point has its binding annotation;</li>
 *     <li>a binding with {@link InterceptorBinding#bindMembers()} applies when one of the occurrences of the
 *     binding annotation on the candidate is the same annotation as one at the point, compared with
 *     {@link AnnotationValue#matches(AnnotationValue)};</li>
 *     <li>a binding which also sets {@link InterceptorBinding#matchAllOccurrences()} requires every occurrence on the
 *     candidate to be the same annotation as one at the point, with the occurrences declared on a method replacing
 *     those of its class.</li>
 * </ul>
 *
 * <p>Members marked {@link io.micronaut.context.annotation.NonBinding} take no part. An occurrence built at runtime
 * does not list them, and the members the other side lists are left out of it as well.</p>
 *
 * <p>A candidate which does not declare its bindings through {@link InterceptorBinding} can be given by the
 * occurrences of its binding annotations, see {@link #matches(Collection)}.</p>
 *
 * <p>The bean-candidate pre-filter {@link io.micronaut.inject.qualifiers.Qualifiers#byInterceptorBinding} instead
 * accepts a candidate any of whose bindings applies.</p>
 *
 * @param <T> The bean type
 * @since 5.3.0
 */
@Experimental
public final class ExactInterceptorBindingQualifier<T> extends FilteringQualifier<T> {

    private final InterceptorKind kind;
    /**
     * Per binding annotation, the occurrences of each binding of the point; an empty list for a binding which
     * binds no members.
     */
    private final Map<String, List<List<AnnotationValue<?>>>> pointOccurrences;
    private final Set<String> interceptorTypes;

    private ExactInterceptorBindingQualifier(InterceptorKind kind,
                                             Map<String, List<List<AnnotationValue<?>>>> pointOccurrences,
                                             Set<String> interceptorTypes) {
        this.kind = kind;
        this.pointOccurrences = pointOccurrences;
        this.interceptorTypes = interceptorTypes;
    }

    /**
     * Qualifies the beans bound to an interception point.
     *
     * <p>The occurrences of a binding annotation declared on a method replace those of its class, as the
     * bindings themselves do. A point binding {@code @InterceptorBinding(interceptorType = ...)} also qualifies
     * a bean of that type.</p>
     *
     * @param interceptionPoint The metadata of the interception point, such as an executable method
     * @param kind              The kind of the bindings to compare
     * @param <T>               The bean type
     * @return The qualifier
     */
    public static <T> ExactInterceptorBindingQualifier<T> of(AnnotationMetadata interceptionPoint, InterceptorKind kind) {
        Map<String, List<List<AnnotationValue<?>>>> occurrences = new LinkedHashMap<>();
        Set<String> interceptorTypes = new LinkedHashSet<>();
        for (AnnotationValue<?> binding : InterceptorBindings.resolveInterceptorValues(interceptionPoint, kind)) {
            binding.annotationClassValue(InterceptorBindingQualifier.META_MEMBER_INTERCEPTOR_TYPE)
                .map(AnnotationClassValue::getName)
                .ifPresent(interceptorTypes::add);
            String annotationName = binding.stringValue().orElse(null);
            if (annotationName == null) {
                continue;
            }
            List<AnnotationValue<?>> bound =
                InterceptorBindingQualifier.resolveDeclaredOccurrences(binding, interceptionPoint, true);
            occurrences.computeIfAbsent(annotationName, k -> new ArrayList<>(2)).add(bound != null ? bound : List.of());
        }
        return new ExactInterceptorBindingQualifier<>(kind, occurrences, interceptorTypes);
    }

    /**
     * Qualifies the beans bound to an interception point given by the occurrences of its binding annotations, such
     * as the annotations of a programmatic lookup.
     *
     * <p>Each value is an occurrence of a binding annotation itself, repeatable annotations given one value per
     * occurrence, rather than an annotation composing one. A value built without the defaults of its annotation
     * takes those of the occurrence it is compared with, so it may leave a defaulted member out.</p>
     *
     * @param occurrences The occurrences of the binding annotations of the point
     * @param kind        The kind of the bindings to compare
     * @param <T>         The bean type
     * @return The qualifier
     */
    public static <T> ExactInterceptorBindingQualifier<T> ofOccurrences(Collection<? extends AnnotationValue<?>> occurrences,
                                                                         InterceptorKind kind) {
        Map<String, List<AnnotationValue<?>>> grouped = group(occurrences);
        Map<String, List<List<AnnotationValue<?>>>> pointOccurrences = LinkedHashMap.newLinkedHashMap(grouped.size());
        grouped.forEach((name, group) -> pointOccurrences.put(name, List.of(group)));
        return new ExactInterceptorBindingQualifier<>(kind, pointOccurrences, Set.of());
    }

    @Override
    public boolean doesQualify(Class<T> beanType, BeanType<T> candidate) {
        return interceptorTypes.contains(candidate.getBeanType().getName()) || matches(candidate.getAnnotationMetadata());
    }

    /**
     * Whether a candidate is bound to the interception point: it declares at least one interceptor binding of the
     * kind of this qualifier, and every one of them applies at the point.
     *
     * @param candidateMetadata The annotation metadata of the candidate, such as an interceptor bean definition
     * @return Whether the candidate is bound to the point
     */
    public boolean matches(AnnotationMetadata candidateMetadata) {
        boolean bound = false;
        for (AnnotationValue<?> binding : InterceptorBindings.resolveInterceptorValues(candidateMetadata, kind)) {
            String annotationName = binding.stringValue().orElse(null);
            if (annotationName == null) {
                continue;
            }
            bound = true;
            if (!pointOccurrences.containsKey(annotationName)) {
                return false;
            }
            List<AnnotationValue<?>> candidateOccurrences =
                InterceptorBindingQualifier.resolveDeclaredOccurrences(binding, candidateMetadata, false);
            if (candidateOccurrences != null
                && !applies(annotationName, candidateOccurrences, binding.booleanValue(InterceptorBinding.META_MATCH_ALL_OCCURRENCES).orElse(false))) {
                return false;
            }
        }
        return bound;
    }

    /**
     * Whether a candidate given by the occurrences of its binding annotations is bound to the interception point,
     * for a candidate which does not declare its bindings through {@link InterceptorBinding}, such as an
     * interceptor class of another programming model which records its binding annotations itself.
     *
     * <p>The occurrences are grouped by annotation, and each group is compared as a binding with
     * {@link InterceptorBinding#bindMembers()} and {@link InterceptorBinding#matchAllOccurrences()}: the point must
     * have the binding annotation, and every occurrence of the group must be the same annotation as one at the
     * point, which may declare more. A binding the point has without binding members applies by name. Each value
     * is an occurrence of a binding annotation itself, repeatable annotations given one value per occurrence.</p>
     *
     * @param candidateOccurrences The occurrences of the binding annotations of the candidate
     * @return Whether the candidate has at least one occurrence and every one of them applies at the point
     */
    public boolean matches(Collection<? extends AnnotationValue<?>> candidateOccurrences) {
        if (candidateOccurrences.isEmpty()) {
            return false;
        }
        Map<String, List<AnnotationValue<?>>> grouped = group(candidateOccurrences);
        for (Map.Entry<String, List<AnnotationValue<?>>> group : grouped.entrySet()) {
            if (!pointOccurrences.containsKey(group.getKey()) || !applies(group.getKey(), group.getValue(), true)) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, List<AnnotationValue<?>>> group(Collection<? extends AnnotationValue<?>> occurrences) {
        Map<String, List<AnnotationValue<?>>> grouped = new LinkedHashMap<>();
        for (AnnotationValue<?> occurrence : occurrences) {
            grouped.computeIfAbsent(occurrence.getAnnotationName(), k -> new ArrayList<>(2)).add(occurrence);
        }
        return grouped;
    }

    /**
     * Whether the occurrences of a binding annotation on the candidate apply at the point, which has the annotation.
     */
    private boolean applies(String annotationName, List<AnnotationValue<?>> candidateOccurrences, boolean matchAllOccurrences) {
        List<List<AnnotationValue<?>>> point = pointOccurrences.getOrDefault(annotationName, List.of());
        boolean boundByOccurrences = false;
        boolean boundByNoOccurrences = false;
        for (List<AnnotationValue<?>> occurrences : point) {
            if (occurrences.isEmpty()) {
                // an annotation carrying both @Around and @InterceptorBinding(bindMembers = true) records a
                // second binding which binds no members, and it must not decide the match while a binding
                // carrying the occurrences the point binds by is present
                boundByNoOccurrences = true;
                continue;
            }
            boundByOccurrences = true;
            if (matchAllOccurrences
                ? allMatch(candidateOccurrences, occurrences)
                : InterceptorBindingQualifier.anyMatch(candidateOccurrences, occurrences)) {
                return true;
            }
        }
        // a point binding no members at all is one compiled before the candidate bound by them
        return boundByNoOccurrences && !boundByOccurrences;
    }

    /**
     * Whether every occurrence declared on the candidate is the same annotation as one of the occurrences at the
     * point, which may declare more.
     */
    private static boolean allMatch(List<AnnotationValue<?>> candidateOccurrences,
                                    List<AnnotationValue<?>> pointOccurrences) {
        for (AnnotationValue<?> required : candidateOccurrences) {
            boolean found = false;
            for (AnnotationValue<?> present : pointOccurrences) {
                if (required.matches(present)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ExactInterceptorBindingQualifier<?> that = (ExactInterceptorBindingQualifier<?>) o;
        return kind == that.kind && pointOccurrences.equals(that.pointOccurrences) && interceptorTypes.equals(that.interceptorTypes);
    }

    @Override
    public int hashCode() {
        return ObjectUtils.hash(kind, pointOccurrences, interceptorTypes);
    }

    @Override
    public String toString() {
        return pointOccurrences.keySet().stream()
            .map(name -> "@InterceptorBinding(" + NameUtils.getShortenedName(name) + ", exact)")
            .collect(Collectors.joining(" "))
            + interceptorTypes.stream()
            .map(type -> " @InterceptorBinding(interceptorType = " + NameUtils.getShortenedName(type) + ")")
            .collect(Collectors.joining());
    }
}
