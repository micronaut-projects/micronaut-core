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

import groovy.transform.CompileStatic
import org.particleframework.aop.annotation.Trace
import org.particleframework.aop.internal.InterceptorChain
import org.particleframework.aop.internal.InterceptorSupport
import org.particleframework.aop.internal.Interceptors
import org.particleframework.context.ExecutionHandleLocator
import org.particleframework.inject.Argument
import org.particleframework.inject.ExecutionHandle
import org.particleframework.inject.MethodExecutionHandle
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

    void "test interceptor chain interaction with Java code"() {
        given:
        Interceptor[] interceptors = [new OneInterceptor(), new ArgMutating()]
        def executionHandle = Mock(MethodExecutionHandle)
        def handleLocator = Mock(ExecutionHandleLocator)
        def arg = Mock(Argument)
        arg.getName() >> 'name'
        arg.getType() >> String

        executionHandle.getArguments() >> ([arg] as Argument[])

        handleLocator.getExecutionHandle(*_) >> executionHandle

        FooJava$Intercepted foo  = new FooJava$Intercepted(10, handleLocator, interceptors )

        expect:
        foo.blah("test") == 'Name is changed'
    }

    static class ArgMutating implements Interceptor {

        @Override
        Object intercept(InvocationContext context) {
            context.getParameters().get("name").setValue("changed")
            return context.proceed()
        }
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

class Foo {

    int c

    Foo(int c) {
        this.c = c
    }

    @Trace
    String blah(String name) {
        "Name is $name"
    }

}

@CompileStatic
class Foo$Intercepted extends Foo {

    private final Interceptor[] interceptors
    private ExecutionHandle[] executionHandles

    Foo$Intercepted(int c, ExecutionHandleLocator locator, @Interceptors(Trace) Interceptor[] interceptors) {
        super(c)
        this.interceptors = interceptors
        this.executionHandles = new ExecutionHandle[1]
        this.executionHandles[0] = InterceptorSupport.adapt(
                locator.getExecutionHandle(this, "blah", String)
        ) { Object[] args ->
            super.blah((String)args[0])
        }
    }

    @Override
    String blah(String name) {
        InterceptorChain chain = new InterceptorChain(
            interceptors,
            this,
            this.executionHandles[0],
            name
        )
        return chain.proceed()
    }
}