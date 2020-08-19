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
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.function.client.FunctionDefinition;
import io.micronaut.function.client.FunctionDiscoveryClient;
import io.micronaut.function.client.FunctionInvoker;
import io.micronaut.function.client.FunctionInvokerChooser;
import io.micronaut.function.client.exceptions.FunctionNotFoundException;
import io.reactivex.Flowable;
import io.reactivex.Maybe;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Implements advice for the {@link io.micronaut.function.client.FunctionClient} annotation.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class FunctionClientAdvice implements MethodInterceptor<Object, Object> {

    private final FunctionDiscoveryClient discoveryClient;
    private final FunctionInvokerChooser functionInvokerChooser;

    /**
     * Constructor.
     *
     * @param discoveryClient discoveryClient
     * @param functionInvokerChooser functionInvokerChooser
     */
    public FunctionClientAdvice(FunctionDiscoveryClient discoveryClient, FunctionInvokerChooser functionInvokerChooser) {
        this.discoveryClient = discoveryClient;
        this.functionInvokerChooser = functionInvokerChooser;
    }

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

        String functionName = context.stringValue(Named.class)
                .orElse(NameUtils.hyphenate(context.getMethodName(), true));

        Flowable<FunctionDefinition> functionDefinition = Flowable.fromPublisher(discoveryClient.getFunction(functionName));
        InterceptedMethod interceptedMethod = InterceptedMethod.of(context);
        try {
            switch (interceptedMethod.resultType()) {
                case PUBLISHER:
                    return interceptedMethod.handleResult(invokeFn(body, functionName, functionDefinition, interceptedMethod.returnTypeValue()));
                case COMPLETION_STAGE:
                    return interceptedMethod.handleResult(toCompletableFuture(
                            invokeFn(body, functionName, functionDefinition, interceptedMethod.returnTypeValue())
                    ));
                case SYNCHRONOUS:
                    FunctionDefinition def = functionDefinition.blockingFirst();
                    FunctionInvoker functionInvoker = functionInvokerChooser.choose(def).orElseThrow(() -> new FunctionNotFoundException(def.getName()));
                    return functionInvoker.invoke(def, body, context.getReturnType().asArgument());
                default:
                    return interceptedMethod.unsupported();
            }
        } catch (Exception e) {
            return interceptedMethod.handleException(e);
        }
    }

    private Flowable<Object> invokeFn(Object body, String functionName, Flowable<FunctionDefinition> functionDefinition, Argument<?> valueType) {
        return functionDefinition.firstElement().flatMap(def -> {
            FunctionInvoker functionInvoker = functionInvokerChooser.choose(def).orElseThrow(() -> new FunctionNotFoundException(def.getName()));
            return (Maybe<Object>) functionInvoker.invoke(
                    def,
                    body,
                    Argument.of(Maybe.class, valueType)
            );
        }).switchIfEmpty(Maybe.error(() -> new FunctionNotFoundException(functionName))).toFlowable();
    }

    private CompletableFuture<Object> toCompletableFuture(Flowable<Object> flowable) {
        CompletableFuture<Object> completableFuture = new CompletableFuture<>();
        flowable.firstElement().subscribe(completableFuture::complete, completableFuture::completeExceptionally, () -> completableFuture.complete(null));
        return completableFuture;
    }

}
