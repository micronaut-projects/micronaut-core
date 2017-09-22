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

import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.util.ASMifier
import groovyjarjarasm.asm.util.TraceClassVisitor
import org.particleframework.aop.internal.InterceptorChain
import org.particleframework.context.ExecutionHandleLocator
import org.particleframework.context.annotation.Type
import org.particleframework.inject.Argument
import org.particleframework.inject.ExecutableMethod
import spock.lang.Specification

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.Target

import static java.lang.annotation.RetentionPolicy.RUNTIME

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class InterceptorChainSpec extends Specification {



    void "test invoke interceptor chain"() {
        given:
        Interceptor[] interceptors = [new TwoInterceptor(), new OneInterceptor(), new ThreeInterceptor()]
        def executionHandle = Mock(ExecutableMethod)
        executionHandle.getDeclaringType() >> InterceptorChainSpec
        executionHandle.getMethodName() >> "test"
        executionHandle.invoke(_) >> "good"
        executionHandle.getArguments() >> ([] as Argument[])
        InterceptorChain chain = new InterceptorChain(interceptors, this, executionHandle, { "good"})

        when:
        def result = chain.proceed()

        then:
        result == "good"
        chain.get("invoked", List).get() == [1,2,3]
    }

    void "test interceptor chain interaction with Java code"() {
        given:
//        String className = FooJava$Intercepted.getName();
//        String classAsPath = className.replace('.', '/') + ".class";
//        InputStream stream = FooJava$Intercepted.getClassLoader().getResourceAsStream(classAsPath);
//        ClassReader reader = new ClassReader(stream)
//        reader.accept(new TraceClassVisitor((ClassVisitor)null, new ASMifier(), new PrintWriter(System.out)), 0);

        Interceptor[] interceptors = [new OneInterceptor(), new ArgMutating()]
        def executionHandle = Mock(ExecutableMethod)
        def handleLocator = Mock(ExecutionHandleLocator)
        def arg = Mock(Argument)
        executionHandle.getDeclaringType() >> InterceptorChainSpec
        executionHandle.getMethodName() >> "test"

        arg.getName() >> 'name'
        arg.getType() >> String

        executionHandle.getArguments() >> ([arg] as Argument[])

        handleLocator.getExecutableMethod(*_) >> executionHandle

        FooJava$Intercepted foo  = new FooJava$Intercepted(10,  interceptors )

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
            context.put("invoked", [1])
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
            context.get("invoked", List).get().add(2)
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
            context.get("invoked", List).get().add(3)
            return context.proceed()
        }
    }
}

class Foo {

    int c

    Foo(int c) {
        this.c = c
    }

    @Mutating
    String blah(String name) {
        "Name is $name"
    }

    void blahVoid() {

    }

}

@Around
@Type(InterceptorChainSpec.ArgMutating)
@Documented
@Retention(RUNTIME)
@Target([ElementType.METHOD])
@interface Mutating {
}