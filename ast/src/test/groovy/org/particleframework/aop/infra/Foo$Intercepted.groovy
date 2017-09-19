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
package org.particleframework.aop.infra

import org.particleframework.aop.Intercepted
import org.particleframework.aop.Interceptor
import org.particleframework.aop.annotation.Trace
import org.particleframework.aop.internal.InterceptorChain
import org.particleframework.aop.internal.MethodInterceptorChain
import org.particleframework.context.AbstractExecutableMethod
import org.particleframework.context.annotation.Replaces
import org.particleframework.context.annotation.Type
import org.particleframework.core.reflect.ReflectionUtils
import org.particleframework.inject.ExecutableMethod

import javax.inject.Singleton

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Replaces(Foo)
class Foo$Intercepted extends Foo implements Intercepted {
    private final Interceptor[][] interceptors
    private final ExecutableMethod[] proxyMethods


    Foo$Intercepted(Bar bar, @Type([Trace.class]) Interceptor[] interceptors) throws NoSuchMethodException {
        super(bar)
        this.interceptors = new Interceptor[1][]
        this.proxyMethods = new ExecutableMethod[1]
        this.proxyMethods[0] = new $blah0()
        def resolved = InterceptorChain.resolveInterceptors(proxyMethods[0], interceptors)
        this.interceptors[0] = resolved

    }

    @Override
    String blah(String name) {
        ExecutableMethod executableMethod = this.proxyMethods[0]
        Interceptor[] interceptors = this.interceptors[0]
        InterceptorChain chain = new MethodInterceptorChain(interceptors, this, executableMethod, name)
        return (String) chain.proceed()
    }

    class $blah0 extends AbstractExecutableMethod {
        protected $blah0() {
            super(ReflectionUtils.findMethod(Foo.class, "blah", String.class).get(),
                    new Class[0],
                    Collections.singletonMap("name", String.class),
                    Collections.emptyMap(),
                    Collections.emptyMap()
            )
        }
        @Override
        protected Object invokeInternal(Object instance, Object[] arguments) {
            return $bridge000(arguments)
        }
    }

    Object $bridge000(Object...arguments) {
        super.blah((String) arguments[0])
    }
}

