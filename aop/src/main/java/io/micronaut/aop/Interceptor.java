/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.aop;

import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.order.Ordered;

/**
 * <p>An Interceptor intercepts the execution of a method allowing cross cutting behaviour to be applied to a
 * method's execution.</p>
 *
 * <p>All implementations should be thread safe and {@link javax.inject.Singleton} scoped beans</p>
 *
 * <p>In the case of {@link Around} advice the interceptor should invoke {@link InvocationContext#proceed()}
 * to proceed with the method invocation</p>
 *
 * <p>In the case of {@link Introduction} advice the interceptor should invoke {@link InvocationContext#proceed()}
 * if it is unable to implement the method. The last call to  {@link InvocationContext#proceed()} will produce a
 * {@link UnsupportedOperationException} indicating the method cannot be implemented. This mechanism allows multiple
 * possible interceptors to participate in method implementation.</p>
 *
 * @param <T> The intercepted type
 * @param <R> The result type
 * @author Graeme Rocher
 * @since 1.0
 */
@Indexed(Interceptor.class)
public interface Interceptor<T, R> extends Ordered {

    /**
     * The {@link Around#proxyTarget()} setting.
     */
    CharSequence PROXY_TARGET = "proxyTarget";

    /**
     * The {@link Around#hotswap()}  setting.
     */
    CharSequence HOTSWAP = "hotswap";

    /**
     * The {@link Around#lazy()}   setting.
     */
    CharSequence LAZY = "lazy";

    /**
     * Intercepts an execution from a declared {@link Around} advice. The implementation can either call {@link InvocationContext#proceed()} to return the original value or provide a replacement value
     *
     * @param context The interception context
     * @return result type
     */
    R intercept(InvocationContext<T, R> context);
}
