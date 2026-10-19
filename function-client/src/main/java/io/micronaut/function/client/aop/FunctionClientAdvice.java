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
package io.micronaut.function.client.aop;

import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.async.publisher.CompletionStagePublishers;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.function.client.AsyncFunctionDiscoveryClient;
import io.micronaut.function.client.DefaultAsyncFunctionDiscoveryClient;
import io.micronaut.function.client.DefaultFunctionDiscoveryClient;
import io.micronaut.function.client.FunctionDefinition;
import io.micronaut.function.client.FunctionDiscoveryClient;
import io.micronaut.function.client.FunctionInvoker;
import io.micronaut.function.client.FunctionInvokerChooser;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.function.client.exceptions.FunctionNotFoundException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Implements advice for the {@link io.micronaut.function.client.FunctionClient} annotation.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class FunctionClientAdvice implements MethodInterceptor<Object, Object> {

    private final ConversionService conversionService;
    private final FunctionDiscoveryClient discoveryClient;
    private final FunctionInvokerChooser functionInvokerChooser;
    /**
     * The client the functions are looked up with when the method returns a stage or a value, or
     * {@code null} to look them up with {@link #discoveryClient}.
     */
    @Nullable
    private final AsyncFunctionDiscoveryClient asyncDiscoveryClient;
    /**
     * The function name of each method, which does not change.
     */
    private final Map<ExecutableMethod<?, ?>, String> functionNames = new ConcurrentHashMap<>();

    /**
     * Constructor.
     *
     * @param conversionService The conversion service
     * @param discoveryClient discoveryClient
     * @param functionInvokerChooser functionInvokerChooser
     */
    public FunctionClientAdvice(ConversionService conversionService, FunctionDiscoveryClient discoveryClient, FunctionInvokerChooser functionInvokerChooser) {
        this(conversionService, discoveryClient, null, functionInvokerChooser);
    }

    /**
     * Constructor. The functions of the methods that return a {@link CompletionStage} or a value
     * are looked up with the {@link AsyncFunctionDiscoveryClient}, unless it is the default one
     * and the {@link FunctionDiscoveryClient} is not, so that a replaced
     * {@link FunctionDiscoveryClient} keeps being used.
     *
     * @param conversionService      The conversion service
     * @param discoveryClient        The function discovery client
     * @param asyncDiscoveryClient   The asynchronous function discovery client, if any
     * @param functionInvokerChooser The function invoker chooser
     * @since 5.3.0
     */
    @Inject
    public FunctionClientAdvice(ConversionService conversionService,
                                FunctionDiscoveryClient discoveryClient,
                                @Nullable AsyncFunctionDiscoveryClient asyncDiscoveryClient,
                                FunctionInvokerChooser functionInvokerChooser) {
        this.conversionService = conversionService;
        this.discoveryClient = discoveryClient;
        this.functionInvokerChooser = functionInvokerChooser;
        boolean replaced = asyncDiscoveryClient != null
            && asyncDiscoveryClient.getClass() == DefaultAsyncFunctionDiscoveryClient.class
            && discoveryClient.getClass() != DefaultFunctionDiscoveryClient.class;
        this.asyncDiscoveryClient = replaced ? null : asyncDiscoveryClient;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Map<String, Object> parameterValueMap = context.getParameterValueMap();
        int len = parameterValueMap.size();

        Object body;
        if (len == 1) {
            body = parameterValueMap.values().iterator().next();
        } else if (len == 0) {
            body = null;
        } else {
            body = parameterValueMap;
        }

        String functionName = functionNames.computeIfAbsent(context.getExecutableMethod(), method ->
            method.stringValue(AnnotationUtil.NAMED).orElseGet(() -> NameUtils.hyphenate(method.getMethodName(), true))
        );

        InterceptedMethod interceptedMethod = InterceptedMethod.of(context, conversionService);
        try {
            switch (interceptedMethod.resultType()) {
                case PUBLISHER -> {
                    var functionDefinition = Flux.from(discoveryClient.getFunction(functionName));
                    return interceptedMethod.handleResult(invokeFn(body, functionName, functionDefinition, interceptedMethod.returnTypeValue()));
                }
                case COMPLETION_STAGE -> {
                    return interceptedMethod.handleResult(invokeFnAsync(body, functionName, interceptedMethod.returnTypeValue()));
                }
                case SYNCHRONOUS -> {
                    FunctionDefinition def = join(functionDefinition(functionName).toCompletableFuture());
                    FunctionInvoker functionInvoker = functionInvokerChooser.choose(def).orElseThrow(() -> new FunctionNotFoundException(def.getName()));
                    return functionInvoker.invoke(def, body, context.getReturnType().asArgument());
                }
                default -> {
                    return interceptedMethod.unsupported();
                }
            }
        } catch (Exception e) {
            return interceptedMethod.handleException(e);
        }
    }

    private Flux<Object> invokeFn(@Nullable Object body, String functionName, Flux<FunctionDefinition> functionDefinition, Argument<?> valueType) {
        return functionDefinition.next().flatMap(def -> {
            FunctionInvoker functionInvoker = functionInvokerChooser.choose(def).orElseThrow(() -> new FunctionNotFoundException(def.getName()));
            return Mono.from(Objects.requireNonNull(
                (Publisher<Object>) functionInvoker.invoke(
                    def,
                    body,
                    Argument.of(Publisher.class, valueType)
                ),
                "The function invoker returned no publisher"
            ));
        }).switchIfEmpty(Mono.error(() -> new FunctionNotFoundException(functionName))).flux();
    }

    /**
     * Invoke the function once the discovery client found it, and complete with the first item
     * of the publisher of the invoker. The future is completed with the errors as they are, not
     * wrapped in a {@link CompletionException}. Cancelling the future cancels the lookup and the
     * invocation, where the framework created their stages.
     */
    private CompletableFuture<@Nullable Object> invokeFnAsync(@Nullable Object body, String functionName, Argument<?> valueType) {
        return CompletionStagePublishers.compose(functionDefinition(functionName), def -> {
            FunctionInvoker<Object, Publisher<Object>> functionInvoker = functionInvokerChooser.<Object, Publisher<Object>>choose(def)
                .orElseThrow(() -> new FunctionNotFoundException(def.getName()));
            @SuppressWarnings("unchecked")
            Argument<Publisher<Object>> publisherType = (Argument<Publisher<Object>>) (Argument<?>) Argument.of(Publisher.class, valueType);
            Publisher<Object> result = Objects.requireNonNull(
                functionInvoker.invoke(def, body, publisherType),
                "The function invoker returned no publisher"
            );
            return CompletionStagePublishers.map(CompletionStagePublishers.first(result, null), value -> {
                if (value == null) {
                    throw new FunctionNotFoundException(functionName);
                }
                return value;
            });
        });
    }

    /**
     * The function definition from {@link AsyncFunctionDiscoveryClient#getFunction(String)}, or
     * from {@link FunctionDiscoveryClient#getFunction(String)} when there is no asynchronous
     * client, or when it returns no stage, or a stage completed with {@code null}, like a mock.
     */
    private CompletionStage<FunctionDefinition> functionDefinition(String functionName) {
        Supplier<CompletionStage<FunctionDefinition>> fromPublisher = () ->
            CompletionStagePublishers.map(CompletionStagePublishers.first(discoveryClient.getFunction(functionName), null), def -> {
                if (def == null) {
                    throw new FunctionNotFoundException(functionName);
                }
                return def;
            });
        if (asyncDiscoveryClient == null) {
            return fromPublisher.get();
        }
        return CompletionStagePublishers.orElseIfNull(asyncDiscoveryClient.getFunction(functionName), fromPublisher);
    }

    /**
     * Wait for a future and throw its error as it is, a checked exception as a blocking
     * subscription to a publisher would throw it.
     */
    private static <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = CompletionStagePublishers.unwrap(e);
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw Exceptions.propagate(cause);
        }
    }

}
