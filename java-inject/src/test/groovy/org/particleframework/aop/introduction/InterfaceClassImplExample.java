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
package org.particleframework.aop.introduction;

import org.particleframework.aop.Intercepted;
import org.particleframework.aop.Interceptor;
import org.particleframework.aop.internal.InterceptorChain;
import org.particleframework.aop.internal.MethodInterceptorChain;
import org.particleframework.aop.simple.Mutating;
import org.particleframework.context.annotation.Type;
import org.particleframework.inject.ExecutableMethod;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class InterfaceClassImplExample implements Intercepted {
    private final Interceptor[][] interceptors;
    private final ExecutableMethod[] proxyMethods;

    InterfaceClassImplExample(@Type({Mutating.class, Stub.class}) Interceptor[] interceptors) throws NoSuchMethodException {
        this.interceptors = new Interceptor[1][];
        this.proxyMethods = new ExecutableMethod[1];
        this.interceptors[0] = InterceptorChain.resolveInterceptors(proxyMethods[0], interceptors);
    }

    public String blah(String name) {
        ExecutableMethod executableMethod = this.proxyMethods[0];
        Interceptor[] interceptors = this.interceptors[0];
        InterceptorChain chain = new MethodInterceptorChain(interceptors, this, executableMethod, name);
        return (String) chain.proceed();
    }


}
