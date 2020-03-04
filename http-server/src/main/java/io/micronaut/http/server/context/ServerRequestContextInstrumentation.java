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
package io.micronaut.http.server.context;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;
import io.micronaut.scheduling.instrument.InvocationInstrumenterFactory;
import io.micronaut.scheduling.instrument.ReactiveInvocationInstrumenterFactory;

import javax.inject.Singleton;

/**
 * Instruments Micronaut such that {@link io.micronaut.http.context.ServerRequestContext} state is propagated.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Internal
final class ServerRequestContextInstrumentation implements InvocationInstrumenterFactory, ReactiveInvocationInstrumenterFactory {

    @Override
    public InvocationInstrumenter newInvocationInstrumenter() {
        return ServerRequestContext.currentRequest().map(invocationRequest -> new InvocationInstrumenter() {

            private HttpRequest<Object> currentRequest;
            private boolean isSet = false;

            @Override
            public void beforeInvocation() {
                currentRequest = ServerRequestContext.currentRequest().orElse(null);
                System.out.println("Thread.currentThread().getName() = " + Thread.currentThread().getName());
                if (invocationRequest != currentRequest) {
                    isSet = true;
                    ServerRequestContext.set(invocationRequest);
                    System.out.println("beforeInvocation() set request = " + invocationRequest.getPath());
                } else {
                    System.out.println("beforeInvocation() not setting request = " + currentRequest);
                }
            }

            @Override
            public void afterInvocation() {
                System.out.println("Thread.currentThread().getName() = " + Thread.currentThread().getName());
                if (isSet) {
                    if (currentRequest != null) {
                        System.out.println("afterInvocation() restore request = " + currentRequest.getPath());
                    } else {
                        System.out.println("Setting current request to null");
                    }
                    ServerRequestContext.set(currentRequest);
                    isSet = false;
                } else {
                    System.out.println("No request to restore");
                }
            }

        }).orElse(null);
    }

    @Override
    public InvocationInstrumenter newReactiveInvocationInstrumenter() {
        return newInvocationInstrumenter();
    }

}
