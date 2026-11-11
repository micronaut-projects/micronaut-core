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

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/**
 * The dependencies of one bean instance: the record of what was created for that instance and is destroyed with
 * it. It is reached through {@link BeanRegistration#dependencies()}.
 *
 * <p>This is not an API for acquiring beans; that is {@link BeanDependencyResolver} and
 * {@link BeanDependencyGroup}. It lets code outside the injection module create something on behalf of a bean
 * the container already made. A proxy that selects interceptors for a target does so, so that the unscoped ones
 * are created as dependents of the target and destroyed with it.</p>
 *
 * @since 5.3.0
 */
@Internal
public sealed interface BeanDependencies permits DefaultBeanDependencies, DefaultBeanDependencyResolver {

    /**
     * Runs a resolution on behalf of the bean. What the operation creates becomes a dependent of the bean,
     * destroyed with it; what it adds through {@link BeanResolutionContext#addDependentBean(BeanRegistration)}
     * does too. A failure destroys what was created instead.
     *
     * @param context The context the bean belongs to
     * @param definition The definition the resolution is rooted at, or null
     * @param operation The resolution
     * @param <S> The result type
     * @return The result, or null when the bean is being destroyed or the context cannot resolve on its behalf
     */
    <S> @Nullable S resolveDependencies(BeanLocator context, @Nullable BeanDefinition<?> definition,
                                        Function<BeanResolutionContext, S> operation);
}
