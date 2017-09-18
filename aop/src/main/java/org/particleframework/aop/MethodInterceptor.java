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

/**
 * A MethodInterceptor extends the generic {@link Interceptor} and provides an interface more specific to method interception
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface MethodInterceptor<T> extends Interceptor<T> {

    /**
     * Extended version of the {@link #intercept(InvocationContext)} method that accepts a {@link MethodInvocationContext}
     *
     * @param context The context
     * @return The result
     */
    T intercept(MethodInvocationContext<T> context);

    @Override
    default T intercept(InvocationContext<T> context) {
        if(context instanceof MethodInvocationContext) {
            return intercept((MethodInvocationContext<T>)context);
        }
        throw new IllegalArgumentException("Context must be an instance of MethodInvocationContext");
    }
}
