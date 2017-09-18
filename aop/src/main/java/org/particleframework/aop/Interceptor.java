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

import org.particleframework.core.order.Ordered;

/**
 * <p>An Interceptor intercepts the execution of a method allowing cross cutting behaviour to be applied to a method's execution.</p>
 *
 * <p>All implementations should be thread safe and {@link javax.inject.Singleton} scoped beans</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Interceptor<T> extends Ordered {

    /**
     * Intercepts an execution from a declared {@link Around} advice. The implementation can either call {@link InvocationContext#proceed()} to return the original value or provide a replacement value
     *
     * @param context The interception context
     */
    T intercept(InvocationContext<T> context);
}
