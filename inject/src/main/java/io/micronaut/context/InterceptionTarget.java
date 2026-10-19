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
package io.micronaut.context;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.inject.BeanDefinition;

/**
 * The bean an interceptor instance was created for.
 *
 * <p>An interceptor that no scope holds, a {@code @Prototype} or one with no scope, is created by the container for
 * one intercepted bean and destroyed with it. Such an interceptor can inject this type, in its constructor or any
 * other injection point of its own, to learn which bean that is: the definition of the bean being created, or, for
 * a proxy that resolves its interceptors for each target, the definition of the target. The definition carries the
 * qualifier of the bean, so two beans of one class, for example two beans of one {@code @EachBean}, are told
 * apart.</p>
 *
 * <p>It is only available while the container creates an interceptor for an intercepted bean. Injecting it into
 * any other bean, into a dependency of the interceptor, or into an interceptor obtained by a plain lookup fails
 * with a {@link io.micronaut.context.exceptions.NoSuchBeanException}; a {@code @Nullable} injection point receives
 * {@code null} instead. A singleton interceptor, or one of a custom scope, is shared by many beans and never receives
 * it.</p>
 *
 * @since 5.3.0
 */
@Experimental
public interface InterceptionTarget {

    /**
     * @return The definition of the intercepted bean, with its qualifier
     */
    BeanDefinition<?> definition();
}
