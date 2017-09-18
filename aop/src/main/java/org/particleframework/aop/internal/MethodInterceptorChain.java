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
package org.particleframework.aop.internal;

import org.particleframework.aop.Interceptor;
import org.particleframework.aop.MethodInvocationContext;
import org.particleframework.context.BeanLocator;
import org.particleframework.context.ExecutionHandleLocator;
import org.particleframework.inject.MethodExecutionHandle;
import org.particleframework.inject.ReturnType;

import java.lang.reflect.Method;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class MethodInterceptorChain<R> extends InterceptorChain<R> implements MethodInvocationContext<R> {
    public MethodInterceptorChain(Interceptor<R>[] interceptors, Object target, MethodExecutionHandle<R> executionHandle, Object... originalParameters) {
        super(interceptors, target, executionHandle, originalParameters);
    }

    public MethodInterceptorChain(Class<Interceptor<R>>[] interceptorTypes, BeanLocator beanLocator, ExecutionHandleLocator handleLocator, Object target, Method method, Object... originalParameters) {
        super(interceptorTypes, beanLocator, handleLocator, target, method, originalParameters);
    }

    @Override
    public String getMethodName() {
        return ((MethodExecutionHandle)executionHandle).getMethodName();
    }

    @Override
    public ReturnType<R> getReturnType() {
        return ((MethodExecutionHandle<R>)executionHandle).getReturnType();
    }
}
