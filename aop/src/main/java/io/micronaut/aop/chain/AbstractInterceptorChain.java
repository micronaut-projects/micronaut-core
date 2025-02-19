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
package io.micronaut.aop.chain;

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.InvocationContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableArgumentValue;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract interceptor chain implementation.
 *
 * @param <B> The bean type
 * @param <R> The return type
 * @author graemerocher
 * @since 3.0.0
 */
@Internal
abstract class AbstractInterceptorChain<B, R> implements InvocationContext<B, R> {
    /**
     * Used by subclasses!
     */
    protected static final Logger LOG = LoggerFactory.getLogger(InterceptorChain.class);

    protected final Interceptor<B, R>[] interceptors;
    protected final Object[] originalParameters;
    protected final int interceptorCount;
    protected volatile MutableConvertibleValues<Object> attributes;
    protected int index = 0;
    protected volatile Map<String, MutableArgumentValue<?>> parameters;

    AbstractInterceptorChain(Interceptor<B, R>[] interceptors, Object... originalParameters) {
        this.interceptors = interceptors;
        this.interceptorCount = interceptors.length;
        this.originalParameters = originalParameters;
    }

    @Override
    public @NonNull Object[] getParameterValues() {
        return originalParameters;
    }

    @Override
    public @NonNull MutableConvertibleValues<Object> getAttributes() {
        MutableConvertibleValues<Object> localAttributes = this.attributes;
        if (localAttributes == null) {
            synchronized (this) { // double check
                localAttributes = this.attributes;
                if (localAttributes == null) {
                    localAttributes = MutableConvertibleValues.of(new ConcurrentHashMap<>(5));
                    this.attributes = localAttributes;
                }
            }
        }
        return localAttributes;
    }

    @Override
    public @NonNull Map<String, MutableArgumentValue<?>> getParameters() {
        Map<String, MutableArgumentValue<?>> localParameters = this.parameters;
        if (localParameters == null) {
            synchronized (this) { // double check
                localParameters = this.parameters;
                if (localParameters == null) {
                    Argument<?>[] arguments = getArguments();
                    localParameters = CollectionUtils.newLinkedHashMap(arguments.length);
                    for (int i = 0; i < arguments.length; i++) {
                        Argument argument = arguments[i];
                        int finalIndex = i;
                        localParameters.put(argument.getName(), new MutableArgumentValue<>() {
                            @NonNull
                            @Override
                            public AnnotationMetadata getAnnotationMetadata() {
                                return argument.getAnnotationMetadata();
                            }

                            @Override
                            public Optional<Argument<?>> getFirstTypeVariable() {
                                return argument.getFirstTypeVariable();
                            }

                            @Override
                            public Argument[] getTypeParameters() {
                                return argument.getTypeParameters();
                            }

                            @Override
                            public Map<String, Argument<?>> getTypeVariables() {
                                return argument.getTypeVariables();
                            }

                            @NonNull
                            @Override
                            public String getName() {
                                return argument.getName();
                            }

                            @NonNull
                            @Override
                            public Class<Object> getType() {
                                return argument.getType();
                            }

                            @Override
                            public boolean equalsType(@Nullable Argument<?> other) {
                                return argument.equalsType(other);
                            }

                            @Override
                            public int typeHashCode() {
                                return argument.typeHashCode();
                            }

                            @Override
                            public Object getValue() {
                                return originalParameters[finalIndex];
                            }

                            @Override
                            public void setValue(Object value) {
                                originalParameters[finalIndex] = value;
                            }
                        });
                    }
                    localParameters = Collections.unmodifiableMap(localParameters);
                    this.parameters = localParameters;
                }
            }
        }
        return localParameters;
    }

    @Override
    public R proceed(@NonNull Interceptor from) throws RuntimeException {
        for (int i = 0; i < interceptors.length; i++) {
            Interceptor<B, R> interceptor = interceptors[i];
            if (interceptor == from) {
                index = i + 1;
                return proceed();

            }
        }
        throw new IllegalArgumentException("Argument [" + from + "] is not within the interceptor chain");
    }

    /**
     * Resolve interceptor binding for the given annotation metadata and kind.
     *
     * @param annotationMetadata The annotation metadata
     * @param kind The kind
     * @return The binding
     * @since 3.3.0
     */
    protected static @NonNull Collection<AnnotationValue<?>> resolveInterceptorValues(@NonNull AnnotationMetadata annotationMetadata,
                                                                                      @NonNull InterceptorKind kind) {
        annotationMetadata = annotationMetadata.getTargetAnnotationMetadata();
        if (annotationMetadata instanceof AnnotationMetadataHierarchy annotationMetadataHierarchy) {
            final List<AnnotationValue<InterceptorBinding>> declaredValues =
                annotationMetadataHierarchy.getDeclaredMetadata().getAnnotationValuesByType(InterceptorBinding.class);
            final List<AnnotationValue<InterceptorBinding>> parentValues =
                annotationMetadataHierarchy.getRootMetadata()
                    .getAnnotationValuesByType(InterceptorBinding.class);
            if (CollectionUtils.isEmpty(declaredValues) && CollectionUtils.isEmpty(parentValues)) {
                return Collections.emptyList();
            }
            Set<AnnotationValue<?>> resolved = CollectionUtils.newHashSet(declaredValues.size() + parentValues.size());
            Set<String> declared = CollectionUtils.newHashSet(declaredValues.size());
            for (AnnotationValue<InterceptorBinding> declaredValue : declaredValues) {
                final String annotationName = declaredValue.stringValue().orElse(null);
                if (annotationName != null) {
                    final InterceptorKind specifiedKind = declaredValue.enumValue("kind", InterceptorKind.class).orElse(null);
                    if (specifiedKind == null || specifiedKind.equals(kind)) {
                        if (!annotationMetadata.isRepeatableAnnotation(annotationName)) {
                            declared.add(annotationName);
                        }
                        resolved.add(declaredValue);
                    }
                }
            }
            for (AnnotationValue<InterceptorBinding> parentValue : parentValues) {
                final String annotationName = parentValue.stringValue().orElse(null);
                if (annotationName != null && !declared.contains(annotationName)) {
                    final InterceptorKind specifiedKind = parentValue.enumValue("kind", InterceptorKind.class).orElse(null);
                    if (specifiedKind == null || specifiedKind.equals(kind)) {
                        resolved.add(parentValue);
                    }
                }
            }

            return resolved;
        } else {
            List<AnnotationValue<InterceptorBinding>> bindings = annotationMetadata
                .getAnnotationValuesByType(InterceptorBinding.class);
            if (CollectionUtils.isEmpty(bindings)) {
                return Collections.emptyList();
            }
            Set<AnnotationValue<?>> selectedBindings = CollectionUtils.newHashSet(bindings.size());
            for (AnnotationValue<InterceptorBinding> av : bindings) {
                if (av.stringValue().isEmpty()) {
                    continue;
                }
                final InterceptorKind specifiedKind = av.enumValue("kind", InterceptorKind.class).orElse(null);
                if (specifiedKind == null || specifiedKind.equals(kind)) {
                    selectedBindings.add(av);
                }
            }
            return selectedBindings;
        }
    }
}
