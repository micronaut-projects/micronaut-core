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

import org.particleframework.core.annotation.Internal;
import org.particleframework.inject.Argument;
import org.particleframework.inject.MethodExecutionHandle;
import org.particleframework.inject.ReturnType;

import java.lang.annotation.Annotation;
import java.util.function.Function;

/**
 * Support methods for {@link org.particleframework.aop.Interceptor}. This class is considered internal and should not
 * be used directly in user code.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class InterceptorSupport {

    @Internal
    public static <R> MethodExecutionHandle<R> adapt(MethodExecutionHandle<R> handle, Function<Object[], R> adapter) {
        return new MethodExecutionHandle<R>() {
            @Override
            public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
                return handle.getAnnotation(annotationClass);
            }

            @Override
            public Annotation[] getAnnotations() {
                return handle.getAnnotations();
            }

            @Override
            public Annotation[] getDeclaredAnnotations() {
                return handle.getDeclaredAnnotations();
            }

            @Override
            public String getMethodName() {
                return handle.getMethodName();
            }

            @Override
            public ReturnType<R> getReturnType() {
                return handle.getReturnType();
            }

            @Override
            public Class getDeclaringType() {
                return handle.getDeclaringType();
            }

            @Override
            public Argument[] getArguments() {
                return handle.getArguments();
            }

            @Override
            public R invoke(Object... arguments) {
                return adapter.apply(arguments);
            }
        };
    }
}
