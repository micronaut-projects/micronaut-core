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

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.BeanType;

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
    private static final String META_MEMBER_INTERCEPTOR_TYPE = "interceptorType";
    private final Map<String, List<AnnotationValue<?>>> supportedAnnotationNames;
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
                    List<AnnotationValue<?>> existing = supportedAnnotationNames
                            .computeIfAbsent(name, k -> new ArrayList<>(5));
                    existing.add(members);
                } else {
                    supportedAnnotationNames.put(name, null);
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
                        List<AnnotationValue<?>> existing = supportedAnnotationNames
                                .computeIfAbsent(name, k -> new ArrayList<>(5));
                        existing.add(members);
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
            Collection<AnnotationValue<?>> interceptorValues = resolveInterceptorAnnotationValues(annotationMetadata, null);
            if (!interceptorValues.isEmpty()) {
                if (interceptorValues.size() == 1) {
                    // single binding case, fast path
                    final AnnotationValue<?> interceptorBinding = interceptorValues.iterator().next();
                    final String annotationName = interceptorBinding.stringValue().orElse(null);
                    if (annotationName == null) {
                        return false;
                    } else {
                        final List<AnnotationValue<?>> bindingList = supportedAnnotationNames.get(annotationName);
                        if (bindingList != null) {
                            final AnnotationValue<Annotation> otherBinding =
                                    interceptorBinding.getAnnotation(META_MEMBER_MEMBERS).orElse(null);
                            boolean matched = true;
                            for (AnnotationValue<?> binding : bindingList) {
                                matched = matched && (!binding.isPresent(META_MEMBER_MEMBERS) || binding.equals(otherBinding));
                            }
                            return matched;
                        } else {
                            return supportedAnnotationNames.containsKey(annotationName);
                        }
                    }
                } else {
                    // multiple binding case
                    boolean matched = false;
                    for (AnnotationValue<?> annotation : interceptorValues) {
                        final String annotationName = annotation.stringValue().orElse(null);
                        if (annotationName == null) {
                            continue;
                        }
                        final List<AnnotationValue<?>> bindingList = supportedAnnotationNames.get(annotationName);
                        if (bindingList != null) {
                            final AnnotationValue<Annotation> otherBinding =
                                    annotation.getAnnotation(META_MEMBER_MEMBERS).orElse(null);
                            for (AnnotationValue<?> binding : bindingList) {
                                matched = (!binding.isPresent(META_MEMBER_MEMBERS) || binding.equals(otherBinding));
                                if (matched) {
                                    break;
                                }
                            }
                        } else {
                            matched = supportedAnnotationNames.containsKey(annotationName);
                        }

                        if (matched) {
                            break;
                        }
                    }
                    return matched;
                }
            }
            return false;
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

    private static @NonNull Collection<AnnotationValue<?>> resolveInterceptorAnnotationValues(
            @NonNull AnnotationMetadata annotationMetadata,
            @Nullable String kind) {
        List<AnnotationValue<Annotation>> bindings = annotationMetadata
                .getAnnotationValuesByName(AnnotationUtil.ANN_INTERCEPTOR_BINDING);
        if (CollectionUtils.isNotEmpty(bindings)) {
            return bindings
                    .stream()
                    .filter(av -> {
                        if (!av.stringValue().isPresent()) {
                            return false;
                        }
                        if (kind == null) {
                            return true;
                        } else {
                            final String specifiedkind = av.stringValue("kind").orElse(null);
                            return specifiedkind == null || specifiedkind.equals(kind);
                        }
                    })
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }
}
