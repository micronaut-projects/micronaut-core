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
package io.micronaut.docs.aop.around_reactive;

// tag::imports[]

import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import jakarta.inject.Singleton;
// end::imports[]

// tag::interceptor[]
@Singleton
@InterceptorBean(Tx.class) // <1>
public class TxInterceptor implements MethodInterceptor<Object, Object> { // <2>

    private final TxManager txManager;
    private final ConversionService conversionService;

    TxInterceptor(TxManager txManager, ConversionService conversionService) {
        this.txManager = txManager;
        this.conversionService = conversionService;
    }

    @Nullable
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        InterceptedMethod interceptedMethod = InterceptedMethod.of(context, conversionService);
        try {
            if (interceptedMethod.resultType() == InterceptedMethod.ResultType.PUBLISHER) {
                if (context.getReturnType().isSingleResult()) {
                    return interceptedMethod.handleResult(
                        txManager.inTransactionMono(tx -> interceptedMethod.interceptResultAsPublisher())
                    );
                }
                return interceptedMethod.handleResult(
                    txManager.inTransactionFlux(tx -> interceptedMethod.interceptResultAsPublisher())
                );
            }
            throw new IllegalStateException("This interceptor supports only publishers!");
        } catch (Exception e) {
            return interceptedMethod.handleException(e);
        }
    }


}
// end::interceptor[]
