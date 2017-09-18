/*
 * Copyright 2017 original authors
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
package org.particleframework.aop

import org.particleframework.inject.Argument
import org.particleframework.inject.ExecutionHandle
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class InterceptorChainSpec extends Specification {

    void "test invoke interceptor chain"() {
        given:
        Interceptor[] interceptors = [new TwoInterceptor(), new OneInterceptor(), new ThreeInterceptor()]
        def executionHandle = Mock(ExecutionHandle)
        executionHandle.invoke() >> "good"
        executionHandle.getArguments() >> ([] as Argument[])
        InterceptorChain chain = new InterceptorChain(interceptors, this, executionHandle)

        when:
        def result = chain.proceed()

        then:
        result == "good"
        chain.getAll("invoked") == [1,2,3]
    }

    static class OneInterceptor implements Interceptor {

        @Override
        int getOrder() {
            return 1
        }

        @Override
        Object intercept(InvocationContext context) {
            context.add("invoked", 1)
            return context.proceed()
        }
    }

    static class TwoInterceptor implements Interceptor {
        @Override
        int getOrder() {
            return 2
        }

        @Override
        Object intercept(InvocationContext context) {
            context.add("invoked", 2)
            return context.proceed()
        }
    }

    static class ThreeInterceptor implements Interceptor {

        @Override
        int getOrder() {
            return 3
        }

        @Override
        Object intercept(InvocationContext context) {
            context.add("invoked", 3)
            return context.proceed()
        }
    }
}
