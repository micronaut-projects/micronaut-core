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

import org.particleframework.aop.internal.InterceptorChain;
import org.particleframework.aop.internal.MethodInterceptorChain;
import org.particleframework.context.AbstractExecutableMethod;
import org.particleframework.context.annotation.Type;
import org.particleframework.core.reflect.ReflectionUtils;
import org.particleframework.core.type.Argument;
import org.particleframework.inject.ExecutableMethod;

import java.util.Collections;

/**
 * This class is what the final compiled byte code for a proxy generated with @Around looks like when decompiled
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class FooJava$Intercepted extends Foo implements Intercepted {
    private final Interceptor[][] interceptors;
    private final ExecutableMethod[] proxyMethods;

    FooJava$Intercepted(int c, @Type({Mutating.class, Trace.class}) Interceptor[] interceptors) throws NoSuchMethodException {
        super(c);
        this.interceptors = new Interceptor[1][];
        this.proxyMethods = new ExecutableMethod[1];
        this.proxyMethods[0] = new $blah0();
        this.interceptors[0] = InterceptorChain.resolveAroundInterceptors(proxyMethods[0], interceptors);
    }

    @Override
    public String blah(String name) {
        ExecutableMethod executableMethod = this.proxyMethods[0];
        Interceptor[] interceptors = this.interceptors[0];
        InterceptorChain chain = new MethodInterceptorChain(interceptors, this, executableMethod, name);
        return (String) chain.proceed();
    }

    class $blah0 extends AbstractExecutableMethod {
        protected $blah0() {
            super(ReflectionUtils.findMethod(Foo.class, "blah", String.class).get(),
                    new Class[0],
                    Argument.create(
                            ReflectionUtils.findMethod(Foo.class, "blah", String.class).get(),
                            "name",
                            0,
                            null

                    )
            );
        }
        @Override
        protected Object invokeInternal(Object instance, Object[] arguments) {
            return FooJava$Intercepted.super.blah((String) arguments[0]);
        }
    }

}