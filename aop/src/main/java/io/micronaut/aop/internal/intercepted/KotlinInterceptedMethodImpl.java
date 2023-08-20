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
package io.micronaut.aop.internal.intercepted;

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.aop.util.CompletableFutureContinuation;
import io.micronaut.aop.util.DelegatingContextContinuation;
import io.micronaut.aop.util.KotlinInterceptedMethodHelper;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.propagation.KotlinCoroutinePropagation;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.KotlinUtils;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * The Kotlin coroutine method intercepted as a value of {@link CompletionStage}.
 *
 * @author Denis Stepanov
 * @since 2.1.0
 */
@Internal
@Experimental
final class KotlinInterceptedMethodImpl implements io.micronaut.aop.kotlin.KotlinInterceptedMethod {

    private final MethodInvocationContext<?, ?> context;
    private Continuation<?> continuation;
    private final Consumer<Object> replaceContinuation;
    private final Argument<?> returnTypeValue;
    private final boolean isUnitValueType;

    private KotlinInterceptedMethodImpl(MethodInvocationContext<?, ?> context,
                                        Continuation<?> continuation,
                                        Consumer<Object> replaceContinuation,
                                        Argument<?> returnTypeValue,
                                        boolean isUnitValueType) {
        this.context = context;
        this.continuation = continuation;
        this.returnTypeValue = returnTypeValue;
        this.isUnitValueType = isUnitValueType;
        this.replaceContinuation = replaceContinuation;
    }

    /**
     * Checks if the method invocation is a Kotlin coroutine.
     *
     * @param context {@link MethodInvocationContext}
     * @return new intercepted method if Kotlin coroutine or null if it's not
     */
    public static KotlinInterceptedMethodImpl of(MethodInvocationContext<?, ?> context) {
        if (!KotlinUtils.KOTLIN_COROUTINES_SUPPORTED || !context.getExecutableMethod().isSuspend()) {
            return null;
        }
        Object[] parameterValues = context.getParameterValues();
        if (parameterValues.length == 0) {
            return null;
        }
        int lastParameterIndex = parameterValues.length - 1;
        Object lastArgumentValue = parameterValues[lastParameterIndex];
        if (lastArgumentValue instanceof Continuation<?> continuation) {
            Consumer<Object> replaceContinuation = value -> parameterValues[lastParameterIndex] = value;
            Argument<?> returnTypeValue = context.getArguments()[lastParameterIndex].getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            boolean isUnitValueType = returnTypeValue.getType() == kotlin.Unit.class;
            if (isUnitValueType) {
                returnTypeValue = Argument.VOID_OBJECT;
            }
            return new KotlinInterceptedMethodImpl(context, continuation, replaceContinuation, returnTypeValue, isUnitValueType);
        }
        return null;
    }

    @Override
    public ResultType resultType() {
        return ResultType.COMPLETION_STAGE;
    }

    @Override
    public Argument<?> returnTypeValue() {
        return returnTypeValue;
    }

    @Override
    public CompletableFuture<Object> interceptResultAsCompletionStage() {
        if (PropagatedContext.exists()) {
            updateCoroutineContext(
                KotlinCoroutinePropagation.Companion.updatePropagatedContext(
                    getCoroutineContext(),
                    PropagatedContext.get()
                )
            );
        }
        @SuppressWarnings("unchecked")
        CompletableFutureContinuation completableFutureContinuation = new CompletableFutureContinuation((Continuation<Object>) continuation);
        replaceContinuation.accept(completableFutureContinuation);
        Object result = context.proceed();
        replaceContinuation.accept(continuation);
        if (result != KotlinUtils.COROUTINE_SUSPENDED) {
            completableFutureContinuation.resumeWith(result);
        }
        return completableFutureContinuation.getCompletableFuture();
    }

    @Override
    public CompletableFuture<Object> interceptResultAsCompletionStage(Interceptor<?, ?> from) {
        if (PropagatedContext.exists()) {
            updateCoroutineContext(
                KotlinCoroutinePropagation.Companion.updatePropagatedContext(
                    getCoroutineContext(),
                    PropagatedContext.get()
                )
            );
        }
        @SuppressWarnings("unchecked")
        CompletableFutureContinuation completableFutureContinuation = new CompletableFutureContinuation((Continuation<Object>) continuation);
        replaceContinuation.accept(completableFutureContinuation);
        Object result = context.proceed(from);
        replaceContinuation.accept(continuation);
        if (result != KotlinUtils.COROUTINE_SUSPENDED) {
            completableFutureContinuation.resumeWith(result);
        }
        return completableFutureContinuation.getCompletableFuture();
    }

    @Override
    public Object interceptResult() {
        return interceptResultAsCompletionStage();
    }

    @Override
    public Object interceptResult(Interceptor<?, ?> from) {
        return interceptResultAsCompletionStage(from);
    }

    @Override
    public Object handleResult(Object result) {
        CompletionStage<?> completionStageResult;
        if (result instanceof CompletionStage stage) {
            completionStageResult = stage;
        } else {
            throw new IllegalStateException("Cannot convert " + result + "  to 'java.util.concurrent.CompletionStage'");
        }
        if (PropagatedContext.exists()) {
            updateCoroutineContext(
                KotlinCoroutinePropagation.Companion.updatePropagatedContext(
                    getCoroutineContext(),
                    PropagatedContext.get()
                )
            );
        }
        return KotlinInterceptedMethodHelper.handleResult(completionStageResult, isUnitValueType, (Continuation<? super Object>) continuation);
    }

    @Override
    public <E extends Throwable> Object handleException(Exception exception) throws E {
        throw (E) exception;
    }

    @Override
    public CoroutineContext getCoroutineContext() {
        return continuation.getContext();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void updateCoroutineContext(CoroutineContext coroutineContext) {
        continuation = new DelegatingContextContinuation((Continuation<Object>) continuation, coroutineContext);
    }
}
