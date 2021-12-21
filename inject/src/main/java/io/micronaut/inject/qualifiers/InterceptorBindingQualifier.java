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

import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.*;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.BeanType;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Qualifier used to resolve the interceptor binding when injection method interceptors for AOP.
 *
 * @param <T> The bean type
 * @author graemerocher
 * @since 2.4.0
 */
@Internal
public final class InterceptorBindingQualifier<T> implements Qualifier<T> {
    public static final String META_MEMBER_MEMBERS = "bindMembers";
    public static final String META_MEMBER_NON_BINDING = "$nonBinding";
    private static final String META_MEMBER_INTERCEPTOR_TYPE = "interceptorType";
    private final Map<String, AnnotationValue<?>> supportedAnnotationNames;
    private final Set<Class<?>> supportedInterceptorTypes;

    InterceptorBindingQualifier(AnnotationMetadata annotationMetadata) {
        final List<AnnotationValue<Annotation>> annotationValues = annotationMetadata
                .findAnnotation(AnnotationUtil.ANN_INTERCEPTOR_BINDING_QUALIFIER)
                .map(av -> av.getAnnotations(AnnotationMetadata.VALUE_MEMBER))
                .orElse(Collections.emptyList());
        this.supportedAnnotationNames = new HashMap<>(annotationValues.size());
        for (AnnotationValue<Annotation> annotationValue : annotationValues) {
            final String name = annotationValue.stringValue().orElse(null);
            if (name != null) {
                final AnnotationValue<Annotation> members =
                        annotationValue.getAnnotation(META_MEMBER_MEMBERS).orElse(null);
                if (members != null) {
                    supportedAnnotationNames.put(name, members);
                } else {
                    supportedAnnotationNames.putIfAbsent(name, null);
                }
            }
        }
        this.supportedInterceptorTypes = annotationValues
                .stream()
                .flatMap(av -> av.classValue(META_MEMBER_INTERCEPTOR_TYPE).map(Stream::of).orElse(Stream.empty()))
                .collect(Collectors.toSet());
    }

    /**
     * Interceptor binding qualifiers.
     * @param bindingAnnotations The binding annotations
     */
    InterceptorBindingQualifier(Collection<AnnotationValue<?>> bindingAnnotations) {
        if (CollectionUtils.isNotEmpty(bindingAnnotations)) {
            this.supportedAnnotationNames = new HashMap<>(bindingAnnotations.size());
            for (AnnotationValue<?> bindingAnnotation : bindingAnnotations) {
                final String name = bindingAnnotation.stringValue().orElse(null);
                if (name != null) {
                    final AnnotationValue<Annotation> members =
                            bindingAnnotation.getAnnotation(META_MEMBER_MEMBERS).orElse(null);
                    if (members != null) {
                        supportedAnnotationNames.put(name, members);
                    } else {
                        supportedAnnotationNames.putIfAbsent(name, null);
                    }
                }
            }
        } else {
            this.supportedAnnotationNames = Collections.emptyMap();
        }
        this.supportedInterceptorTypes = Collections.emptySet();
    }

    /**
     * Interceptor binding qualifiers.
     * @param bindingAnnotations The binding annotations
     * @deprecated Use {@link #InterceptorBindingQualifier(java.util.Collection)} instead
     */
    @Deprecated
    InterceptorBindingQualifier(String[] bindingAnnotations) {
        if (ArrayUtils.isNotEmpty(bindingAnnotations)) {
            this.supportedAnnotationNames = new HashMap<>(bindingAnnotations.length);
            for (String bindingAnnotation : bindingAnnotations) {
                supportedAnnotationNames.put(bindingAnnotation, null);
            }
        } else {
            this.supportedAnnotationNames = Collections.emptyMap();
        }
        this.supportedInterceptorTypes = Collections.emptySet();
    }

    @Override
    public <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates) {
        return candidates.filter(candidate -> {
            if (supportedInterceptorTypes.contains(candidate.getBeanType())) {
                return true;
            }
            final AnnotationMetadata annotationMetadata = candidate.getAnnotationMetadata();
            List<String> annotationNames = new ArrayList<>(resolveInterceptorValues(annotationMetadata));
            final Iterator<String> i = annotationNames.iterator();
            while (i.hasNext()) {
                final String annotation = i.next();
                if (supportedAnnotationNames.containsKey(annotation)) {
                    final AnnotationValue<?> binding = supportedAnnotationNames.get(annotation);
                    if (binding != null) {

                        final Set<String> nonBinding =
                                CollectionUtils.setOf(
                                        binding.stringValues(InterceptorBindingQualifier.META_MEMBER_NON_BINDING));
                        if (nonBinding.isEmpty()) {
                            final AnnotationValue<Annotation> otherBinding = annotationMetadata.getAnnotation(annotation);
                            if (!binding.equals(otherBinding)) {
                                i.remove();
                            }
                        } else {
                            final Map<CharSequence, Object> thisValues = new HashMap<>(binding.getValues());
                            final AnnotationValue<Annotation> otherBinding = annotationMetadata.getAnnotation(annotation);
                            if (otherBinding == null) {
                                i.remove();
                            } else {
                                final Map<CharSequence, Object> thatValues = new HashMap<>(otherBinding.getValues());
                                thisValues.remove(InterceptorBindingQualifier.META_MEMBER_NON_BINDING);
                                thatValues.keySet().removeAll(nonBinding);
                                if (!thisValues.equals(thatValues)) {
                                    i.remove();
                                }
                            }

                        }
                    }
                } else {
                    i.remove();
                }
            }
            return !annotationNames.isEmpty();
        });
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
        return Objects.hash(supportedAnnotationNames, supportedInterceptorTypes);
    }

    @Override
    public String toString() {
        if (CollectionUtils.isEmpty(supportedAnnotationNames) && CollectionUtils.isEmpty(supportedInterceptorTypes)) {
            return "@InterceptorBinding(NONE)";
        } else {
            return supportedAnnotationNames.keySet().stream().map((name) -> "@InterceptorBinding(" + name + ")").collect(Collectors.joining(" ")) +
                    supportedInterceptorTypes.stream().map((name) -> "@InterceptorBinding(interceptorType = " + name + ")").collect(Collectors.joining(" "));
        }
    }

    public static List<String> resolveInterceptorValues(AnnotationMetadata annotationMetadata) {
        return resolveInterceptorValues(annotationMetadata, null);
    }

    public static List<String> resolveInterceptorValues(AnnotationMetadata annotationMetadata, @Nullable String kind) {
        List<AnnotationValue<Annotation>> bindings = annotationMetadata
                .getAnnotationValuesByName(AnnotationUtil.ANN_INTERCEPTOR_BINDING);
        if (CollectionUtils.isNotEmpty(bindings)) {
            return bindings
                    .stream()
                    .filter(av -> {
                        final String specifiedkind = av.stringValue("kind").orElse(null);
                        return kind == null || specifiedkind == null || specifiedkind.equals(kind);
                    })
                    .flatMap(av -> {
                        final String v = av.stringValue().orElse(null);
                        return v != null ? Stream.of(v) : Stream.empty();
                    })
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }
}
