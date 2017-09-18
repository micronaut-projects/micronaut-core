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
import org.particleframework.aop.internal.InterceptorSupport;
import org.particleframework.aop.internal.Interceptors;
import org.particleframework.context.ExecutionHandleLocator;
import org.particleframework.inject.ExecutionHandle;

import java.util.function.Function;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class FooJava$Intercepted extends Foo {
    private final Interceptor[] interceptors;
    private ExecutionHandle[] executionHandles;

    FooJava$Intercepted(int c, ExecutionHandleLocator locator, @Interceptors(Trace.class) Interceptor[] interceptors) throws NoSuchMethodException {
        super(c);
        this.interceptors = interceptors;
        this.executionHandles = new ExecutionHandle[1];
        this.executionHandles[0] = InterceptorSupport.adapt(
                locator.getExecutionHandle(this, "blah", String.class),
                (Function<Object[], Object>) objects -> FooJava$Intercepted.super.blah((String) objects[0])
        );
    }

    @Override
    public String blah(String name) {
        InterceptorChain chain = new InterceptorChain(
                interceptors,
                this,
                this.executionHandles[0],
                name
        );
        return (String) chain.proceed();
    }
}
