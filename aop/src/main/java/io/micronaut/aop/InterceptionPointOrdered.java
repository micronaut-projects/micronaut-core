/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.aop;

import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.type.Executable;

/**
 * An {@link Interceptor} whose position among the interceptors of an interception point depends on the point.
 *
 * <p>An interceptor is normally placed by {@link Ordered#getOrder()}, which is the same at every interception point
 * it is selected for. An interceptor implementing this interface is placed by the order it reports for the method
 * or constructor being intercepted instead, so one interceptor bean can run before other advice on one method and
 * after it on another. This is what an interceptor that runs a chain of its own needs, when the chain bound to an
 * element declares where in the surrounding advice it belongs.</p>
 *
 * <p>The order is consulted once for each interception point, when its interceptors are resolved, and only by
 * the {@link InterceptorRegistry}. Any other consumer of an interceptor's order, and any proxy compiled before
 * the registry consulted it, still uses {@link Ordered#getOrder()}, so implementations should return the position
 * they take most often from it.</p>
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
public interface InterceptionPointOrdered extends Ordered {

    /**
     * The order of this interceptor at a method interception point.
     *
     * <p>For {@link InterceptorKind#POST_CONSTRUCT} and {@link InterceptorKind#PRE_DESTROY} the point is the
     * lifecycle event of the bean, one chain running every callback of the phase, and its annotation metadata is
     * the bean's.</p>
     *
     * @param method The intercepted method
     * @param kind   The kind of interception: {@link InterceptorKind#AROUND}, {@link InterceptorKind#INTRODUCTION},
     *               {@link InterceptorKind#POST_CONSTRUCT} or {@link InterceptorKind#PRE_DESTROY}
     * @return The order, lower values run first
     */
    default int getOrder(Executable<?, ?> method, InterceptorKind kind) {
        return getOrder();
    }

    /**
     * The order of this interceptor at a constructor interception point.
     *
     * @param constructor The intercepted constructor
     * @return The order, lower values run first
     */
    default int getOrder(BeanConstructor<?> constructor) {
        return getOrder();
    }
}
