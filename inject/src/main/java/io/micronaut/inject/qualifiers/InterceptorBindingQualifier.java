/*
 * Copyright 2017-2021 original authors
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

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.ObjectUtils;
import io.micronaut.inject.BeanType;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Qualifies interceptor beans by the {@code io.micronaut.aop.InterceptorBinding} annotations of an interception point.
 *
 * <p>A binding names the binding annotation and, when it binds members, the members are compared on the occurrences
 * of the binding annotation found on each side, see {@link #resolveBoundOccurrences(AnnotationValue, AnnotationMetadata)}.
 * An interceptor bound by name only applies wherever its binding annotation does.</p>
 *
 * @param <T> The bean type
 * @author graemerocher
 * @since 3.0.0
 */
@Internal
public final class InterceptorBindingQualifier<T> extends FilteringQualifier<T> {
    /**
     * The member of a binding written by earlier versions of the framework, carrying a copy of the occurrence of the
     * binding annotation the binding was declared through. Members are now compared on the occurrences themselves,
     * which a binding annotation retains, and the copy is only read where it is still present.
     */
    public static final String META_BINDING_VALUES = "$bindingValues";
    public static final String META_MEMBER_INTERCEPTOR_TYPE = "interceptorType";
    private static final String META_BIND_MEMBERS = "bindMembers";
    private final Map<String, List<AnnotationValue<?>>> supportedAnnotationNames;
    private final Set<String> supportedInterceptorTypes;

    /**
     * Interceptor binding qualifiers.
     *
     * @param annotationMetadata The annotation metadata of the interception point, or of the parameter a proxy
     *                           receives its interceptors through
     */
    InterceptorBindingQualifier(AnnotationMetadata annotationMetadata) {
        Collection<AnnotationValue<Annotation>> annotationValues;
        AnnotationValue<Annotation> av = annotationMetadata.findAnnotation(AnnotationUtil.ANN_INTERCEPTOR_BINDING_QUALIFIER).orElse(null);
        if (av == null) {
            annotationValues = Collections.emptyList();
        } else {
            annotationValues = av.getAnnotations(AnnotationMetadata.VALUE_MEMBER);
        }
        if (annotationValues.isEmpty()) {
            annotationValues = annotationMetadata.getAnnotationValuesByName(AnnotationUtil.ANN_INTERCEPTOR_BINDING);
        }
        supportedAnnotationNames = findSupportedAnnotations(annotationValues, annotationMetadata);
        Set<String> supportedInterceptorTypes = CollectionUtils.newHashSet(annotationValues.size());
        for (AnnotationValue<?> annotationValue : annotationValues) {
            annotationValue.annotationClassValue(META_MEMBER_INTERCEPTOR_TYPE).map(AnnotationClassValue::getName).ifPresent(supportedInterceptorTypes::add);
        }
        this.supportedInterceptorTypes = supportedInterceptorTypes;
    }

    /**
     * Interceptor binding qualifiers.
     *
     * @param bindingAnnotations The binding annotations, compared by name
     */
    InterceptorBindingQualifier(Collection<AnnotationValue<Annotation>> bindingAnnotations) {
        if (CollectionUtils.isNotEmpty(bindingAnnotations)) {
            supportedAnnotationNames = findSupportedAnnotations(bindingAnnotations, null);
        } else {
            this.supportedAnnotationNames = Collections.emptyMap();
        }
        this.supportedInterceptorTypes = Collections.emptySet();
    }

    private static Map<String, List<AnnotationValue<?>>> findSupportedAnnotations(Collection<AnnotationValue<Annotation>> annotationValues,
                                                                                  @Nullable AnnotationMetadata annotationMetadata) {
        final Map<String, List<AnnotationValue<?>>> supportedAnnotationNames = CollectionUtils.newHashMap(annotationValues.size());
        for (AnnotationValue<?> annotationValue : annotationValues) {
            final String name = annotationValue.stringValue().orElse(null);
            if (name == null) {
                continue;
            }
            final List<AnnotationValue<?>> occurrences = resolveBoundOccurrences(annotationValue, annotationMetadata);
            if (occurrences != null) {
                supportedAnnotationNames.computeIfAbsent(name, k -> new ArrayList<>(5)).addAll(occurrences);
            } else {
                supportedAnnotationNames.put(name, null);
            }
        }
        return supportedAnnotationNames;
    }

    @Override
    public boolean doesQualify(Class<T> beanType, BeanType<T> candidate) {
        if (supportedInterceptorTypes.contains(candidate.getBeanType().getName())) {
            return true;
        }
        if (supportedAnnotationNames.isEmpty()) {
            return false;
        }
        final AnnotationMetadata annotationMetadata = candidate.getAnnotationMetadata();
        Collection<AnnotationValue<Annotation>> interceptorValues = resolveInterceptorAnnotationValues(annotationMetadata);
        for (AnnotationValue<?> interceptorBinding : interceptorValues) {
            final String annotationName = interceptorBinding.stringValue().orElse(null);
            if (annotationName == null || !supportedAnnotationNames.containsKey(annotationName)) {
                continue;
            }
            final List<AnnotationValue<?>> bound = supportedAnnotationNames.get(annotationName);
            if (bound == null) {
                return true;
            }
            final List<AnnotationValue<?>> candidateOccurrences = resolveBoundOccurrences(interceptorBinding, annotationMetadata);
            if (candidateOccurrences == null || anyMatch(candidateOccurrences, bound)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The occurrences of the binding annotation a binding compares members with, in the given metadata.
     *
     * <p>{@code null} when the binding does not bind members, or when the metadata holds no occurrence of the
     * binding annotation: such a binding matches by name. Otherwise the occurrences of the binding annotation,
     * declared on the element or composed by another annotation and retained on it, with the members marked
     * {@code io.micronaut.context.annotation.NonBinding} left out. A binding written by an earlier version of the
     * framework carries a copy of its occurrence, which is used as is.</p>
     *
     * @param binding            The interceptor binding
     * @param annotationMetadata The metadata of the element the binding was declared on
     * @return The occurrences to compare, or {@code null} to compare by name
     * @since 5.2.0
     */
    @Internal
    @Nullable
    public static List<AnnotationValue<?>> resolveBoundOccurrences(AnnotationValue<?> binding,
                                                                   @Nullable AnnotationMetadata annotationMetadata) {
        return resolveBoundOccurrences(binding, annotationMetadata, false);
    }

    /**
     * The occurrences of the binding annotation a binding compares members with, in the given metadata.
     *
     * @param binding            The interceptor binding
     * @param annotationMetadata The metadata of the element the binding was declared on
     * @param declaredOverrides  Whether, for a hierarchy such as a method under its class, an occurrence declared
     *                           on the most specific element replaces the ones above it, the way the bindings
     *                           themselves are resolved for an interception point
     * @return The occurrences to compare, or {@code null} to compare by name
     * @see #resolveBoundOccurrences(AnnotationValue, AnnotationMetadata)
     * @since 5.2.0
     */
    @Internal
    @Nullable
    public static List<AnnotationValue<?>> resolveBoundOccurrences(AnnotationValue<?> binding,
                                                                   @Nullable AnnotationMetadata annotationMetadata,
                                                                   boolean declaredOverrides) {
        final AnnotationValue<?> copy = binding.getAnnotation(META_BINDING_VALUES).orElse(null);
        if (copy != null) {
            return List.of(copy);
        }
        final String annotationName = binding.stringValue().orElse(null);
        if (annotationName == null || annotationMetadata == null || !binding.booleanValue(META_BIND_MEMBERS).orElse(false)) {
            return null;
        }
        final Set<AnnotationValue<?>> occurrences = new LinkedHashSet<>(4);
        if (declaredOverrides
            && annotationMetadata instanceof AnnotationMetadataHierarchy hierarchy
            && !annotationMetadata.isRepeatableAnnotation(annotationName)) {
            collectOccurrences(hierarchy.getDeclaredMetadata(), annotationName, occurrences);
            if (occurrences.isEmpty()) {
                collectOccurrences(hierarchy.getRootMetadata(), annotationName, occurrences);
            }
        } else {
            collectOccurrences(annotationMetadata, annotationName, occurrences);
        }
        return occurrences.isEmpty() ? null : new ArrayList<>(occurrences);
    }

    private static void collectOccurrences(AnnotationMetadata annotationMetadata,
                                           String annotationName,
                                           Set<AnnotationValue<?>> occurrences) {
        if (annotationMetadata.isRepeatableAnnotation(annotationName)) {
            for (AnnotationValue<?> declared : annotationMetadata.getAnnotationValuesByName(annotationName)) {
                occurrences.add(boundMembers(declared));
            }
        } else {
            annotationMetadata.findAnnotation(annotationName).ifPresent(declared -> occurrences.add(boundMembers(declared)));
        }
        for (AnnotationValue<?> composing : annotationMetadata.getAnnotationValuesByStereotype(annotationName)) {
            collectRetained(composing, annotationName, occurrences);
        }
    }

    private static void collectRetained(AnnotationValue<?> composing,
                                        String annotationName,
                                        Set<AnnotationValue<?>> occurrences) {
        final List<AnnotationValue<?>> stereotypes = composing.getStereotypes();
        if (stereotypes == null) {
            return;
        }
        for (AnnotationValue<?> stereotype : stereotypes) {
            if (annotationName.equals(stereotype.getAnnotationName())) {
                occurrences.add(boundMembers(stereotype));
            } else {
                collectRetained(stereotype, annotationName, occurrences);
            }
        }
    }

    private static AnnotationValue<?> boundMembers(AnnotationValue<?> occurrence) {
        final String[] nonBinding = occurrence.stringValues(AnnotationUtil.NON_BINDING_ATTRIBUTE);
        if (nonBinding.length == 0) {
            return occurrence;
        }
        final Map<CharSequence, Object> members = new LinkedHashMap<>(occurrence.getValues());
        for (String member : nonBinding) {
            members.remove(member);
        }
        members.remove(AnnotationUtil.NON_BINDING_ATTRIBUTE);
        return new AnnotationValue<>(occurrence.getAnnotationName(), members, occurrence.getDefaultValues());
    }

    /**
     * Whether any occurrence of one side is the same annotation as any occurrence of the other, every member
     * compared with the defaults filled in.
     *
     * @param occurrences      The occurrences of one side
     * @param otherOccurrences The occurrences of the other side
     * @return Whether one pair matches
     * @since 5.2.0
     */
    @Internal
    public static boolean anyMatch(Collection<AnnotationValue<?>> occurrences, Collection<AnnotationValue<?>> otherOccurrences) {
        for (AnnotationValue<?> occurrence : occurrences) {
            for (AnnotationValue<?> other : otherOccurrences) {
                if (occurrence.matches(other)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether two binding occurrences are the same annotation.
     *
     * @param interceptorValues    The occurrence bound on the interceptor
     * @param interceptPointValues The occurrence at the interception point
     * @return Whether the two are the same annotation, or there is nothing bound on the interceptor
     */
    @Internal
    public static boolean bindingValuesMatch(@Nullable AnnotationValue<?> interceptorValues,
                                             @Nullable AnnotationValue<?> interceptPointValues) {
        if (interceptorValues == null) {
            return true;
        }
        return interceptorValues.matches(interceptPointValues);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        InterceptorBindingQualifier<?> that = (InterceptorBindingQualifier<?>) o;
        return supportedAnnotationNames.equals(that.supportedAnnotationNames) && supportedInterceptorTypes.equals(that.supportedInterceptorTypes);
    }

    @Override
    public int hashCode() {
        return ObjectUtils.hash(supportedAnnotationNames, supportedInterceptorTypes);
    }

    @Override
    public String toString() {
        if (CollectionUtils.isEmpty(supportedAnnotationNames) && CollectionUtils.isEmpty(supportedInterceptorTypes)) {
            return "@InterceptorBinding(NONE)";
        } else {
            return supportedAnnotationNames.keySet().stream().map((name) -> "@InterceptorBinding(" + NameUtils.getShortenedName(name) + ")").collect(Collectors.joining(" ")) +
                    supportedInterceptorTypes.stream().map((type) -> "@InterceptorBinding(interceptorType = " + NameUtils.getShortenedName(type) + ")").collect(Collectors.joining(" "));
        }
    }

    private static Collection<AnnotationValue<Annotation>> resolveInterceptorAnnotationValues(AnnotationMetadata annotationMetadata) {
        List<AnnotationValue<Annotation>> bindings = annotationMetadata.getAnnotationValuesByName(AnnotationUtil.ANN_INTERCEPTOR_BINDING);
        if (CollectionUtils.isEmpty(bindings)) {
            return Collections.emptyList();
        }
        List<AnnotationValue<Annotation>> result = new ArrayList<>(bindings.size());
        for (AnnotationValue<Annotation> av : bindings) {
            if (av.stringValue().isEmpty()) {
                continue;
            }
            result.add(av);
        }
        return result;
    }
}
