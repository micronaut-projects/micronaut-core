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
package io.micronaut.tracing.interceptor;

import io.micronaut.aop.InterceptPhase;
import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.tracing.annotation.ContinueSpan;
import io.micronaut.tracing.annotation.NewSpan;
import io.micronaut.tracing.annotation.SpanTag;
import io.micronaut.tracing.instrument.util.TracingPublisher;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.log.Fields;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

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

    private static final String TAG_HYSTRIX_COMMAND = "hystrix.command";
    private static final String TAG_HYSTRIX_GROUP = "hystrix.group";
    private static final String TAG_HYSTRIX_THREAD_POOL = "hystrix.threadPool";
    private static final String HYSTRIX_ANNOTATION = "io.micronaut.configuration.hystrix.annotation.HystrixCommand";

    private final Tracer tracer;
    private final ConversionService<?> conversionService;

    /**
     * Initialize the interceptor with tracer and conversion service.
     *
     * @param tracer For span creation and propagation across arbitrary transports
     * @param conversionService A service to convert from one type to another
     */
    public TraceInterceptor(Tracer tracer, ConversionService<?> conversionService) {
        this.tracer = tracer;
        this.conversionService = conversionService;
    }

    @Override
    public int getOrder() {
        return InterceptPhase.TRACE.getPosition();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        boolean isContinue = context.hasAnnotation(ContinueSpan.class);
        AnnotationValue<NewSpan> newSpan = context.getAnnotation(NewSpan.class);
        boolean isNew = newSpan != null;
        if (!isContinue && !isNew) {
            return context.proceed();
        }
        Span currentSpan = tracer.activeSpan();
        if (isContinue) {
            if (currentSpan == null) {
                return context.proceed();
            }
            InterceptedMethod interceptedMethod = InterceptedMethod.of(context);
            try {
                switch (interceptedMethod.resultType()) {
                    case PUBLISHER:
                        Publisher<?> publisher = interceptedMethod.interceptResultAsPublisher();
                        if (publisher instanceof TracingPublisher) {
                            return publisher;
                        }
                        return interceptedMethod.handleResult(
                                new TracingPublisher(publisher, tracer) {
                                    @Override
                                    protected void doOnSubscribe(@NonNull Span span) {
                                        tagArguments(span, context);
                                    }
                                }
                        );
                    case COMPLETION_STAGE:
                    case SYNCHRONOUS:
                        tagArguments(currentSpan, context);
                        try {
                            return context.proceed();
                        } catch (RuntimeException e) {
                            logError(currentSpan, e);
                            throw e;
                        }
                    default:
                        return interceptedMethod.unsupported();
                }
            } catch (Exception e) {
                return interceptedMethod.handleException(e);
            }
        } else {
            // must be new
            String operationName = newSpan.stringValue().orElse(null);
            Optional<String> hystrixCommand = context.stringValue(HYSTRIX_ANNOTATION);
            if (StringUtils.isEmpty(operationName)) {
                // try hystrix command name
                operationName = hystrixCommand.orElse(context.getMethodName());
            }
            Tracer.SpanBuilder builder = tracer.buildSpan(operationName);
            if (currentSpan != null) {
                builder.asChildOf(currentSpan);
            }

            InterceptedMethod interceptedMethod = InterceptedMethod.of(context);
            try {
                switch (interceptedMethod.resultType()) {
                    case PUBLISHER:
                        Publisher<?> publisher = interceptedMethod.interceptResultAsPublisher();
                        if (publisher instanceof TracingPublisher) {
                            return publisher;
                        }
                        return interceptedMethod.handleResult(
                                new TracingPublisher(publisher, tracer, builder) {
                                    @Override
                                    protected void doOnSubscribe(@NonNull Span span) {
                                        populateTags(context, hystrixCommand, span);
                                    }
                                }
                        );
                    case COMPLETION_STAGE:
                        Span span = builder.start();
                        try (Scope ignored = tracer.scopeManager().activate(span)) {
                            populateTags(context, hystrixCommand, span);
                            try {
                                CompletionStage<?> completionStage = interceptedMethod.interceptResultAsCompletionStage();
                                if (completionStage != null) {
                                    completionStage = completionStage.whenComplete((o, throwable) -> {
                                        if (throwable != null) {
                                            logError(span, throwable);
                                        }
                                        span.finish();
                                    });
                                }
                                return interceptedMethod.handleResult(completionStage);
                            } catch (RuntimeException e) {
                                logError(span, e);
                                throw e;
                            }
                        }
                    case SYNCHRONOUS:
                        Span syncSpan = builder.start();
                        try (Scope scope = tracer.scopeManager().activate(syncSpan)) {
                            populateTags(context, hystrixCommand, syncSpan);
                            try {
                                return context.proceed();
                            } catch (RuntimeException e) {
                                logError(syncSpan, e);
                                throw e;
                            } finally {
                                syncSpan.finish();
                            }
                        }
                    default:
                        return interceptedMethod.unsupported();
                }
            } catch (Exception e) {
                return interceptedMethod.handleException(e);
            }
        }
    }

    private void populateTags(MethodInvocationContext<Object, Object> context, Optional<String> hystrixCommand, Span span) {
        span.setTag(CLASS_TAG, context.getDeclaringType().getSimpleName());
        span.setTag(METHOD_TAG, context.getMethodName());
        hystrixCommand.ifPresent(s -> span.setTag(TAG_HYSTRIX_COMMAND, s));
        context.stringValue(HYSTRIX_ANNOTATION, "group").ifPresent(s ->
                span.setTag(TAG_HYSTRIX_GROUP, s)
        );
        context.stringValue(HYSTRIX_ANNOTATION, "threadPool").ifPresent(s ->
                span.setTag(TAG_HYSTRIX_THREAD_POOL, s)
        );
        tagArguments(span, context);
    }

    /**
     * Logs an error to the span.
     *
     * @param span The span
     * @param e    The error
     */
    public static void logError(Span span, Throwable e) {
        HashMap<String, Object> fields = new HashMap<>(2);
        fields.put(Fields.ERROR_OBJECT, e);
        String message = e.getMessage();
        if (message != null) {
            fields.put(Fields.MESSAGE, message);
        }
        span.log(fields);
    }

    private void tagArguments(Span span, MethodInvocationContext<Object, Object> context) {
        Argument[] arguments = context.getArguments();
        Object[] parameterValues = context.getParameterValues();
        for (int i = 0; i < arguments.length; i++) {
            Argument argument = arguments[i];
            AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();
            if (annotationMetadata.hasAnnotation(SpanTag.class)) {
                Object v = parameterValues[i];
                if (v != null) {
                    String tagName = annotationMetadata.stringValue(SpanTag.class).orElse(argument.getName());
                    span.setTag(tagName, v.toString());
                }
            }
        }
    }

}
