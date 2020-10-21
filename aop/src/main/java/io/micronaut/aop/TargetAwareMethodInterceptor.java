/*
 * Copyright 2017-2020 original authors
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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.context.Qualifier;

/**
 * Extended version of {@link MethodInterceptor} that allows an interceptor to receive the target.
 *
 * @param <T> The target type.
 * @param <R> The return type
 * @since 2.2.0
 * @author graemerocher
 */
public interface TargetAwareMethodInterceptor<T, R> extends MethodInterceptor<T, R> {
    /**
     * Invokes once a target of this interceptor is instantiated. Note that in the case of {@link javax.inject.Singleton}
     * interceptors this method would be invoked once for each target. Switching to {@link io.micronaut.context.annotation.Prototype}
     * interceptors will allow for a unique interceptor per target.
     *
     * <p>
     *     It is important to note that logic contained within the newTarget method should exclusively deal with initializing the interceptor and should not
     *     do any explicit bean lookups with methods such as {@link io.micronaut.context.BeanContext#getBean(Class)} which could lead to dead locks.
     * </p>
     *
     * @param qualifier The qualifier of the bean or null if there is no qualifier as is the case with primary beans
     * @param target The target bean
     */
    void newTarget(@Nullable Qualifier<T> qualifier, @NonNull T target);
}
