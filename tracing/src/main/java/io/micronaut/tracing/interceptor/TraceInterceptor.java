/*
 * Copyright 2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.tracing.interceptor;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.MutableArgumentValue;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.tracing.annotation.ContinueSpan;
import io.micronaut.tracing.annotation.NewSpan;
import io.micronaut.tracing.annotation.SpanTag;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.log.Fields;
import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An interceptor that implements tracing logic for {@link io.micronaut.tracing.annotation.ContinueSpan} and
 * {@link io.micronaut.tracing.annotation.NewSpan}. Using the Open Tracing API.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Requires(beans = Tracer.class)
public class TraceInterceptor implements MethodInterceptor<Object, Object> {

    public static final String CLASS_TAG = "class";
    public static final String METHOD_TAG = "method";

    private final Tracer tracer;
    private final ConversionService<?> conversionService;

    public TraceInterceptor(Tracer tracer, ConversionService<?> conversionService) {
        this.tracer = tracer;
        this.conversionService = conversionService;
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        boolean isContinue = context.hasAnnotation(ContinueSpan.class);
        NewSpan newSpan = context.getAnnotation(NewSpan.class);
        boolean isNew = newSpan != null;
        if (!isContinue && !isNew) {
            return context.proceed();
        }
        Span currentSpan = tracer.activeSpan();
        ReturnType<Object> returnType = context.getReturnType();
        Class<Object> javaReturnType = returnType.getType();

        if (isContinue) {
            if (currentSpan == null) {
                return context.proceed();
            }

            if (Publishers.isConvertibleToPublisher(javaReturnType)) {
                Flowable<?> resultFlowable = conversionService.convert(context.proceed(), Flowable.class)
                        .orElseThrow(() -> new IllegalStateException("Unsupported Reactive type: " + javaReturnType));

                resultFlowable = resultFlowable.doOnRequest(amount -> {
                    if (amount > 0) {
                        tagArguments(currentSpan, context);
                    }
                });
                resultFlowable = resultFlowable.onErrorResumeNext(throwable -> {
                    logError(currentSpan, throwable);
                    return Flowable.error(throwable);
                });

                return conversionService.convert(
                        resultFlowable,
                        javaReturnType
                ).orElseThrow(() -> new IllegalStateException("Unsupported Reactive type: " + javaReturnType));
            } else {
                tagArguments(currentSpan, context);
                try {
                    return context.proceed();
                } catch (RuntimeException e) {
                    logError(currentSpan, e);
                    throw e;
                }
            }
        } else {
            // must be new
            String operationName = newSpan.value();
            if (StringUtils.isEmpty(operationName)) {
                operationName = context.getMethodName();
            }
            Tracer.SpanBuilder builder = tracer.buildSpan(operationName);
            if (currentSpan != null) {
                builder.asChildOf(currentSpan);
            }

            if (Publishers.isConvertibleToPublisher(javaReturnType)) {
                boolean single = Publishers.isSingle(javaReturnType);
                Flowable<?> resultFlowable = conversionService.convert(context.proceed(), Flowable.class)
                        .orElseThrow(() -> new IllegalStateException("Unsupported Reactive type: " + javaReturnType));
                AtomicReference<Scope> scopeRef = new AtomicReference<>();
                resultFlowable = resultFlowable.doOnRequest(amount -> {
                    if (amount > 0) {
                        Scope scope = builder.startActive(true);
                        scopeRef.set(scope);

                        Span createdSpan = scope.span();
                        createdSpan.setTag(CLASS_TAG, context.getDeclaringType().getSimpleName());
                        createdSpan.setTag(METHOD_TAG, context.getMethodName());
                        tagArguments(createdSpan, context);
                    }
                });

                if (single) {
                    resultFlowable = resultFlowable.doOnNext(o -> teminateScope(scopeRef))
                            .onErrorResumeNext(throwable -> {
                                Scope scope = scopeRef.get();
                                if (scope != null) {
                                    teminateScope(scopeRef);
                                    logError(scope.span(), throwable);
                                }
                                return Flowable.error(throwable);
                            });
                } else {
                    resultFlowable = resultFlowable.doAfterTerminate(() ->
                            teminateScope(scopeRef)
                    ).onErrorResumeNext(throwable -> {
                        Scope scope = scopeRef.get();
                        if (scope != null) {
                            teminateScope(scopeRef);
                            logError(scope.span(), throwable);
                        }
                        return Flowable.error(throwable);
                    });
                }


                return conversionService.convert(
                        resultFlowable,
                        javaReturnType
                ).orElseThrow(() -> new IllegalStateException("Unsupported Reactive type: " + javaReturnType));
            } else {
                try (Scope scope = builder.startActive(true)) {
                    tagArguments(scope.span(), context);
                    try {
                        return context.proceed();
                    } catch (RuntimeException e) {
                        logError(scope.span(), e);
                        throw e;
                    }
                }
            }

        }

    }

    /**
     * Logs an error to the span
     *
     * @param span The span
     * @param e    The error
     */
    protected void logError(Span span, Throwable e) {
        HashMap<String, Object> fields = new HashMap<>();
        fields.put(Fields.ERROR_OBJECT, e);
        String message = e.getMessage();
        if (message != null) {
            fields.put(Fields.MESSAGE, message);
        }
        span.log(fields);
    }

    private void teminateScope(AtomicReference<Scope> scopeRef) {
        Scope scope = scopeRef.get();
        if (scope != null) {
            scope.close();
        }
    }

    private void tagArguments(Span currentSpan, MethodInvocationContext<Object, Object> context) {
        for (MutableArgumentValue<?> argumentValue : context.getParameters().values()) {
            SpanTag spanTag = argumentValue.getAnnotation(SpanTag.class);
            Object v = argumentValue.getValue();
            if (spanTag != null && v != null) {
                String tagName = spanTag.value();
                if (StringUtils.isEmpty(tagName)) {
                    tagName = argumentValue.getName();
                }
                currentSpan.setTag(tagName, v.toString());
            }
        }
    }

}
