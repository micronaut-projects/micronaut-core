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
import org.particleframework.core.annotation.Internal;
import org.particleframework.inject.ExecutableMethod;
import org.particleframework.inject.ReturnType;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 *
 * An internal representation of the {@link Interceptor} chain. This class implements {@link MethodInvocationContext} and is
 * consumed by the framework itself and should not be used directly in application code.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class MethodInterceptorChain<T, R> extends InterceptorChain<T,R> implements MethodInvocationContext<T, R> {

    public MethodInterceptorChain(Interceptor<T, R>[] interceptors, T target, ExecutableMethod<T, R> executionHandle, Object... originalParameters) {
        super(interceptors, target, executionHandle, originalParameters);
    }

    @Override
    public AnnotatedElement[] getAnnotatedElements() {
        return executionHandle.getAnnotatedElements();
    }

    @Override
    public String getMethodName() {
        return executionHandle.getMethodName();
    }

    @Override
    public Class[] getArgumentTypes() {
        return new Class[0];
    }

    @Override
    public Set<? extends Annotation> getExecutableAnnotations() {
        return null;
    }

    @Override
    public Method getTargetMethod() {
        return executionHandle.getTargetMethod();
    }

    @Override
    public ReturnType<R> getReturnType() {
        return executionHandle.getReturnType();
    }

    @Override
    public Class<?> getDeclaringType() {
        return null;
    }

}
