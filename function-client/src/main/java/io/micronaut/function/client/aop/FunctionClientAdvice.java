/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.function.client.aop;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.function.client.FunctionDefinition;
import io.micronaut.function.client.FunctionDiscoveryClient;
import io.micronaut.function.client.FunctionInvoker;
import io.micronaut.function.client.FunctionInvokerChooser;
import io.micronaut.function.client.exceptions.FunctionExecutionException;
import io.micronaut.function.client.exceptions.FunctionNotFoundException;
import io.reactivex.Flowable;
import io.reactivex.Maybe;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Map;

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
        ReturnType<Object> returnType = context.getReturnType();
        Class<Object> javaReturnType = returnType.getType();
        if (Publishers.isConvertibleToPublisher(javaReturnType)) {
            Maybe flowable = functionDefinition.firstElement().flatMap(def -> {
                FunctionInvoker functionInvoker = functionInvokerChooser.choose(def).orElseThrow(() -> new FunctionNotFoundException(def.getName()));
                return (Maybe) functionInvoker.invoke(
                        def,
                        body,
                        Argument.of(Maybe.class, returnType.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT))
                );
            });
            flowable = flowable.switchIfEmpty(Maybe.error(new FunctionNotFoundException(functionName)));
            return ConversionService.SHARED.convert(flowable, returnType.asArgument()).orElseThrow(() -> new FunctionExecutionException("Unsupported reactive type: " + returnType.getType()));
        } else {
            // blocking operation
            FunctionDefinition def = functionDefinition.blockingFirst();
            FunctionInvoker functionInvoker = functionInvokerChooser.choose(def).orElseThrow(() -> new FunctionNotFoundException(def.getName()));
            return functionInvoker.invoke(def, body, returnType.asArgument());
        }
    }
}
