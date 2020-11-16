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

import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.Interceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.aop.util.CompletableFutureContinuation;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.KotlinUtils;
import kotlin.coroutines.Continuation;

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
final class KotlinInterceptedMethod implements InterceptedMethod {

    private final MethodInvocationContext<?, ?> context;
    private final Continuation continuation;
    private final Consumer<Object> replaceContinuation;
    private final Argument<?> returnTypeValue;
    private final boolean isUnitValueType;

    private KotlinInterceptedMethod(MethodInvocationContext<?, ?> context,
                                    Continuation continuation, Consumer<Object> replaceContinuation,
                                    Argument<?> returnTypeValue, boolean isUnitValueType) {
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
     * @return true if Kotlin coroutine
     */
    public static KotlinInterceptedMethod of(MethodInvocationContext<?, ?> context) {
        if (!KotlinUtils.KOTLIN_COROUTINES_SUPPORTED || !context.getExecutableMethod().isSuspend()) {
            return null;
        }
        Object[] parameterValues = context.getParameterValues();
        if (parameterValues.length == 0) {
            return null;
        }
        int lastParameterIndex = parameterValues.length - 1;
        Object lastArgumentValue = parameterValues[lastParameterIndex];
        if (lastArgumentValue instanceof Continuation) {
            Continuation continuation = (Continuation) lastArgumentValue;
            Consumer<Object> replaceContinuation = value -> parameterValues[lastParameterIndex] = value;
            Argument<?> returnTypeValue = context.getArguments()[lastParameterIndex].getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            boolean isUnitValueType = returnTypeValue.getType() == kotlin.Unit.class;
            if (isUnitValueType) {
                returnTypeValue = Argument.VOID_OBJECT;
            }
            return new KotlinInterceptedMethod(context, continuation, replaceContinuation, returnTypeValue, isUnitValueType);
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
        CompletableFutureContinuation completableFutureContinuation;
        if (continuation instanceof CompletableFutureContinuation) {
            completableFutureContinuation = (CompletableFutureContinuation) continuation;
        } else {
            completableFutureContinuation = new CompletableFutureContinuation(continuation);
            replaceContinuation.accept(completableFutureContinuation);
        }
        Object result = context.proceed();
        replaceContinuation.accept(continuation);
        if (result != KotlinUtils.COROUTINE_SUSPENDED) {
            completableFutureContinuation.resumeWith(result);
        }
        return completableFutureContinuation.getCompletableFuture();
    }

    @Override
    public CompletableFuture<Object> interceptResultAsCompletionStage(Interceptor<?, ?> from) {
        CompletableFutureContinuation completableFutureContinuation;
        if (continuation instanceof CompletableFutureContinuation) {
            completableFutureContinuation = (CompletableFutureContinuation) continuation;
        } else {
            completableFutureContinuation = new CompletableFutureContinuation(continuation);
            replaceContinuation.accept(completableFutureContinuation);
        }
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
        CompletionStage completionStageResult;
        if (result instanceof CompletionStage) {
            completionStageResult = (CompletionStage<?>) result;
        } else {
            throw new IllegalStateException("Cannot convert " + result + "  to 'java.util.concurrent.CompletionStage'");
        }
        completionStageResult.whenComplete((value, throwable) -> {
            if (throwable == null) {
                if (value == null) {
                    if (isUnitValueType) {
                        value = kotlin.Unit.INSTANCE;
                    } else {
                        CompletableFutureContinuation.Companion.completeExceptionally(continuation, new IllegalStateException("Cannot complete Kotlin coroutine with null: " + returnTypeValue.getType()));
                        return;
                    }
                }
                CompletableFutureContinuation.Companion.completeSuccess(continuation, value);
            } else {
                CompletableFutureContinuation.Companion.completeExceptionally(continuation, (Throwable) throwable);
            }
        });
        return KotlinUtils.COROUTINE_SUSPENDED;
    }

    @Override
    public <E extends Throwable> Object handleException(Exception exception) throws E {
        CompletableFutureContinuation.Companion.completeExceptionally(continuation, exception);
        return KotlinUtils.COROUTINE_SUSPENDED;
    }

}
