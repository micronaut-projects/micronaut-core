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
package org.particleframework.aop;

import org.particleframework.aop.annotation.Trace;
import org.particleframework.aop.internal.InterceptorChain;
import org.particleframework.aop.internal.MethodInterceptorChain;
import org.particleframework.context.ExecutionHandleLocator;
import org.particleframework.context.annotation.Type;
import org.particleframework.inject.ExecutableMethod;

import java.util.function.Function;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class FooJava$Intercepted extends Foo implements Intercepted<Foo> {
    private final Interceptor[] interceptors;
    private ExecutableMethod[] executionHandles;
    private Function<Object[], Object>[] proxyHandles;

    FooJava$Intercepted(int c, ExecutionHandleLocator locator, @Type(Trace.class) Interceptor[] interceptors) throws NoSuchMethodException {
        super(c);
        this.interceptors = interceptors;
        this.executionHandles = new ExecutableMethod[1];
        this.proxyHandles = new Function[1];

        Function<Object[], Object> handle = new Function<Object[], Object>() {
            @Override
            public Object apply(Object[] objects) {
                return FooJava$Intercepted.super.blah((String) objects[0]);
            }
        };
        this.proxyHandles[0] = handle;
        this.executionHandles[0] = locator.getExecutableMethod(getClass().getSuperclass(), "blah", String.class);
    }

    @Override
    public String blah(String name) {
        ExecutableMethod executableMethod = this.executionHandles[0];
        InterceptorChain chain = MethodInterceptorChain.get (
                interceptors,
                this,
                executableMethod,
                name
        );
        chain.setLast(proxyHandles[0]);
        try {
            return (String) chain.proceed();
        } finally {
            MethodInterceptorChain.remove(executableMethod);
        }
    }

    @Override
    public Interceptor<Foo, Object>[] getInterceptors() {
        return this.interceptors;
    }
}
