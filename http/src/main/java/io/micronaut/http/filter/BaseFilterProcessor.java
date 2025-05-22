/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.filter;

import io.micronaut.context.BeanContext;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.AntPathMatcher;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.InternalByteBody;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Base class for processing {@link io.micronaut.http.annotation.ServerFilter} and
 * {@link io.micronaut.http.annotation.ClientFilter} beans.
 *
 * @param <A> Filter annotation type
 * @author Jonas Konrad
 * @since 4.0.0
 */
@Internal
public abstract class BaseFilterProcessor<A extends Annotation> implements ExecutableMethodProcessor<A> {
    @Nullable
    private final BeanContext beanContext;
    private final Class<A> filterAnnotation;
    private final RequestBinderRegistry argumentBinderRegistry;

    public BaseFilterProcessor(@Nullable BeanContext beanContext, Class<A> filterAnnotation) {
        this.beanContext = beanContext;
        this.filterAnnotation = filterAnnotation;
        Optional<RequestBinderRegistry> requestBinderRegistry = beanContext != null ? beanContext.findBean(RequestBinderRegistry.class) : Optional.empty();
        this.argumentBinderRegistry = new RequestBinderRegistry() {
            @Override
            public <T> Optional<ArgumentBinder<T, HttpRequest<?>>> findArgumentBinder(Argument<T> argument) {
                if (argument.getAnnotationMetadata().hasAnnotation(Body.class)) {
                    return Optional.of((AsyncBodyBinder<T>) (context, source) -> {
                        if (source instanceof ServerHttpRequest<?> fullHttpRequest) {
                            return InternalByteBody.bufferFlow(fullHttpRequest.byteBody().split(ByteBody.SplitBackpressureMode.FASTEST)).map(imm -> {
                                Argument<T> t = context.getArgument();
                                if (t.isAssignableFrom(ByteBuffer.class)) {
                                    return () -> Optional.of((T) imm.toByteBuffer());
                                } else if (t.isAssignableFrom(byte[].class)) {
                                    byte[] bytes = imm.toByteArray();
                                    return () -> Optional.of((T) bytes);
                                } else if (t.isAssignableFrom(String.class)) {
                                    String str = imm.toString(StandardCharsets.UTF_8);
                                    return () -> Optional.of((T) str);
                                } else {
                                    return ArgumentBinder.BindingResult.unsatisfied();
                                }
                            });
                        }
                        return ExecutionFlow.just(ArgumentBinder.BindingResult.unsatisfied());
                    });
                } else {
                    return requestBinderRegistry.flatMap(registry -> registry.findArgumentBinder(argument));
                }
            }
        };
    }

    @Override
    public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        //noinspection unchecked,rawtypes
        process0(beanDefinition, (ExecutableMethod) method);
    }

    /**
     * Add a filter. Called during {@link #process(BeanDefinition, ExecutableMethod)}.
     *
     * @param factory           Factory that will create the filter instance
     * @param methodAnnotations Annotations on the filter method
     * @param metadata          Filter metadata from class and method annotations
     */
    protected abstract void addFilter(Supplier<GenericHttpFilter> factory, AnnotationMetadata methodAnnotations, FilterMetadata metadata);

    private <T> void process0(BeanDefinition<T> beanDefinition, ExecutableMethod<T, ?> method) {
        if (beanContext != null) {
            FilterMetadata beanLevel = metadata(beanDefinition, filterAnnotation);
            if (method.isAnnotationPresent(RequestFilter.class)) {
                FilterMetadata methodLevel = metadata(method, RequestFilter.class);
                FilterMetadata combined = combineMetadata(beanLevel, methodLevel);
                addFilter(() -> MethodFilter.prepareFilterMethod(beanContext.getConversionService(), beanContext.getBean(beanDefinition), method, false, combined.order, argumentBinderRegistry, getExecutor(combined)), method, combined);
            }
            if (method.isAnnotationPresent(ResponseFilter.class)) {
                FilterMetadata methodLevel = metadata(method, ResponseFilter.class);
                FilterMetadata combined = combineMetadata(beanLevel, methodLevel);
                addFilter(() -> MethodFilter.prepareFilterMethod(beanContext.getConversionService(), beanContext.getBean(beanDefinition), method, true, combined.order, argumentBinderRegistry, getExecutor(combined)), method, combined);
            }
        }
    }

    private Executor getExecutor(FilterMetadata metadata) {
        if (metadata.executeOn != null) {
            return beanContext.getBean(Executor.class, Qualifiers.byName(metadata.executeOn));
        } else {
            return null;
        }
    }

    private FilterMetadata combineMetadata(FilterMetadata beanLevel, FilterMetadata methodLevel) {
        List<String> patterns;
        if (beanLevel.patterns == null) {
            patterns = methodLevel.patterns;
        } else if (methodLevel.patterns == null) {
            patterns = beanLevel.patterns;
        } else {
            if (beanLevel.patternStyle == FilterPatternStyle.REGEX ||
                methodLevel.patternStyle == FilterPatternStyle.REGEX) {
                throw new UnsupportedOperationException("Concatenating regex filter patterns is " +
                    "not supported. Please declare the full pattern on the method instead.");
            }
            patterns = beanLevel.patterns.stream()
                .flatMap(p1 -> methodLevel.patterns.stream().map(p2 -> concatAntPatterns(p1, p2)))
                .toList();
        }

        if (patterns != null && (beanLevel.appendContextPath == null || beanLevel.appendContextPath)) {
            patterns = prependContextPath(patterns);
        }

        FilterOrder order;
        if (methodLevel.order != null) {
            order = methodLevel.order;
        } else if (beanLevel.order != null) {
            // allow overriding using Ordered.getOrder, where possible
            order = new FilterOrder.Dynamic(((FilterOrder.Fixed) beanLevel.order).value());
        } else {
            order = new FilterOrder.Dynamic(Ordered.LOWEST_PRECEDENCE);
        }

        return new FilterMetadata(
            methodLevel.patterns == null ? beanLevel.patternStyle : methodLevel.patternStyle,
            patterns,
            methodLevel.methods == null ? beanLevel.methods : methodLevel.methods,
            order,
            methodLevel.executeOn == null ? beanLevel.executeOn : methodLevel.executeOn,
            beanLevel.serviceId, // only present on bean level
            beanLevel.excludeServiceId, // only present on bean level
            beanLevel.appendContextPath, // Define if contextPath is appended,
            methodLevel.isPreMatching
        );
    }

    /**
     * Prepend server context path if necessary.
     *
     * @param patterns Input patterns
     * @return Output patterns with server context path prepended
     */
    @NonNull
    protected List<String> prependContextPath(@NonNull List<String> patterns) {
        return patterns;
    }

    static String concatAntPatterns(String p1, String p2) {
        StringBuilder combined = new StringBuilder(p1.length() + p2.length() + 1);
        combined.append(p1);
        if (!p1.endsWith(AntPathMatcher.DEFAULT_PATH_SEPARATOR)) {
            combined.append(AntPathMatcher.DEFAULT_PATH_SEPARATOR);
        }
        if (p2.startsWith(AntPathMatcher.DEFAULT_PATH_SEPARATOR)) {
            combined.append(p2, AntPathMatcher.DEFAULT_PATH_SEPARATOR.length(), p2.length());
        } else {
            combined.append(p2);
        }
        return combined.toString();
    }

    private FilterMetadata metadata(AnnotationMetadata annotationMetadata, Class<? extends Annotation> annotationType) {
        HttpMethod[] methods = annotationMetadata.enumValues(annotationType, "methods", HttpMethod.class);
        String[] patterns = annotationMetadata.stringValues(annotationType);
        OptionalInt order = annotationMetadata.intValue(Order.class);
        String[] serviceId = annotationMetadata.stringValues(annotationType, "serviceId"); // only on ClientFilter
        String[] excludeServiceId = annotationMetadata.stringValues(annotationType, "excludeServiceId"); // only on ClientFilter
        Optional<Boolean> appendContextPath = annotationMetadata.booleanValue(annotationType, "appendContextPath");
        return new FilterMetadata(
            annotationMetadata.enumValue(annotationType, "patternStyle", FilterPatternStyle.class).orElse(FilterPatternStyle.ANT),
            ArrayUtils.isNotEmpty(patterns) ? Arrays.asList(patterns) : null,
            ArrayUtils.isNotEmpty(methods) ? Arrays.asList(methods) : null,
            order.isPresent() ? new FilterOrder.Fixed(order.getAsInt()) : null,
            annotationMetadata.stringValue(ExecuteOn.class).orElse(null),
            ArrayUtils.isNotEmpty(serviceId) ? Arrays.asList(serviceId) : null,
            ArrayUtils.isNotEmpty(excludeServiceId) ? Arrays.asList(excludeServiceId) : null,
            appendContextPath.orElse(null),
            annotationMetadata.hasStereotype("io.micronaut.http.server.annotation.PreMatching")
        );
    }

    protected record FilterMetadata(
        FilterPatternStyle patternStyle,
        @Nullable List<String> patterns,
        @Nullable List<HttpMethod> methods,
        @Nullable FilterOrder order,
        @Nullable String executeOn,
        @Nullable List<String> serviceId,
        @Nullable List<String> excludeServiceId,
        @Nullable Boolean appendContextPath,
        boolean isPreMatching
    ) {
    }

    /**
     * Interface that signals to {@link FilterRunner} that we should wait for the request body to
     * arrive before running this binder.
     *
     * @param <T> Arg type
     */
    public interface AsyncBodyBinder<T> extends ArgumentBinder<T, HttpRequest<?>> {
        @Override
        default BindingResult<T> bind(ArgumentConversionContext<T> context, HttpRequest<?> source) {
            throw new UnsupportedOperationException();
        }

        ExecutionFlow<BindingResult<T>> bindAsync(ArgumentConversionContext<T> context, HttpRequest<?> source);
    }
}
