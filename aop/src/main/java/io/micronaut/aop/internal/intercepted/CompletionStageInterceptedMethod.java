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
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;

/**
 * The {@link CompletionStage} method intercept.
 *
 * @author Denis Stepanov
 * @since 2.1.0
 */
@Internal
@Experimental
class CompletionStageInterceptedMethod implements InterceptedMethod {
    private final ConversionService<?> conversionService = ConversionService.SHARED;

    private final MethodInvocationContext<?, ?> context;
    private final Argument<?> returnTypeValue;

    CompletionStageInterceptedMethod(MethodInvocationContext<?, ?> context) {
        this.context = context;
        this.returnTypeValue = context.getReturnType().asArgument().getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
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
    public Object interceptResult() {
        return interceptResultAsCompletionStage();
    }

    @Override
    public Object interceptResult(Interceptor<?, ?> from) {
        return interceptResultAsCompletionStage(from);
    }

    @Override
    public CompletionStage<Object> interceptResultAsCompletionStage() {
        return convertToCompletionStage(context.proceed());
    }

    @Override
    public CompletionStage<Object> interceptResultAsCompletionStage(Interceptor<?, ?> from) {
        return convertToCompletionStage(context.proceed(from));
    }

    @Override
    public Object handleResult(Object result) {
        if (result == null) {
            result = CompletableFuture.completedFuture(null);
        }
        return convertCompletionStageResult(context.getReturnType(), result);
    }

    @Override
    public <E extends Throwable> Object handleException(Exception exception) throws E {
        CompletableFuture<Object> newFuture = new CompletableFuture<>();
        newFuture.completeExceptionally(exception);
        return convertCompletionStageResult(context.getReturnType(), newFuture);
    }

    private CompletionStage<Object> convertToCompletionStage(Object result) {
        if (result instanceof CompletionStage) {
            return (CompletionStage<Object>) result;
        }
        throw new IllegalStateException("Cannot convert " + result + "  to 'java.util.concurrent.CompletionStage'");
    }

    private Object convertCompletionStageResult(ReturnType<?> returnType, Object result) {
        Class<?> returnTypeClass = returnType.getType();
        if (returnTypeClass.isInstance(result)) {
            return result;
        }
        if (result instanceof CompletionStage && (returnTypeClass == CompletableFuture.class || returnTypeClass == Future.class)) {
            return ((CompletionStage<?>) result).toCompletableFuture();
        }
        return conversionService.convert(result, returnType.asArgument())
                .orElseThrow(() -> new IllegalStateException("Cannot convert completion stage result: " + result + " to '" + returnType.getType().getName() + "'"));
    }
}
