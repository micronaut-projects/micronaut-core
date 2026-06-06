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
package io.micronaut.context;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.QualifiedBeanType;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;

/**
 * Customizes selected bean resolution behavior for integrations.
 *
 * @since 5.1.0
 */
@Experimental
public interface BeanResolutionCustomizer {

    /**
     * The default customizer.
     */
    BeanResolutionCustomizer DEFAULT = new BeanResolutionCustomizer() {
    };

    /**
     * Whether an object array injection point should first resolve an exact array bean.
     *
     * @param injectionPoint The array injection point
     * @return True to resolve an exact array bean
     */
    default boolean shouldResolveArrayAsBean(Argument<?> injectionPoint) {
        return false;
    }

    /**
     * Resolve an additional argument that may be used to find bean candidates if the
     * requested type does not resolve a bean.
     *
     * @param beanType The requested bean type
     * @return The additional bean type to use for lookup
     * @since 5.1.0
     */
    default Argument<?> resolveBeanLookupArgument(Argument<?> beanType) {
        return beanType;
    }

    /**
     * Returns whether the bean type is a candidate for the requested type.
     *
     * @param beanType The requested bean type
     * @param candidate The candidate bean type
     * @return True if the candidate matches
     * @since 5.1.0
     */
    default boolean isCandidateBean(Argument<?> beanType, QualifiedBeanType<?> candidate) {
        return candidate.isCandidateBean(beanType);
    }

    /**
     * Resolve one bean from multiple qualified candidates before the default
     * {@code @Secondary}, order, and default-implementation resolution rules are applied.
     *
     * @param beanType The requested bean type
     * @param qualifier The requested qualifier
     * @param candidates The qualified candidates
     * @param <T> The bean type
     * @return The resolved bean, or empty to use default resolution
     * @since 5.1.0
     */
    default <T> Optional<BeanDefinition<T>> resolveNonUniqueBean(Argument<T> beanType,
                                                                 @Nullable Qualifier<T> qualifier,
                                                                 Collection<BeanDefinition<T>> candidates) {
        return Optional.empty();
    }

    /**
     * Resolve a replacement value for a bean that was found but produced {@code null}.
     *
     * @param requestedBeanType The originally requested bean type
     * @param resolvedBeanType The resolved bean lookup type
     * @param beanDefinition The bean definition that produced {@code null}
     * @return A replacement bean value, or empty to preserve the default behavior
     * @since 5.1.0
     */
    default Optional<?> resolveNullBean(Argument<?> requestedBeanType, Argument<?> resolvedBeanType, BeanDefinition<?> beanDefinition) {
        return Optional.empty();
    }

    /**
     * Returns whether a newly resolved dependent bean should be destroyed after the current bean resolution
     * completes instead of being tracked as a dependent of the resolved bean.
     *
     * @param resolutionContext The current resolution context
     * @param beanRegistration The dependent bean registration
     * @return True if the dependent bean should be destroyed after the current resolution completes
     * @since 5.1.0
     */
    default boolean shouldDestroyDependentBeanAfterResolution(BeanResolutionContext resolutionContext, BeanRegistration<?> beanRegistration) {
        return false;
    }

    /**
     * Returns whether a newly constructed bean instance should receive field/method injection and initialization
     * callbacks.
     *
     * @param resolutionContext The current resolution context
     * @param beanDefinition The bean definition
     * @param bean The newly constructed bean instance
     * @return True if the bean instance should be injected and initialized
     * @since 5.1.0
     */
    default boolean shouldInitializeBean(BeanResolutionContext resolutionContext, BeanDefinition<?> beanDefinition, Object bean) {
        return true;
    }

    /**
     * Returns whether a lazy proxy should preserve the current resolution path when storing the context
     * that will later be used to resolve the proxy target.
     *
     * @param resolutionContext The current resolution context
     * @param proxyBeanDefinition The proxy bean definition
     * @return True if the current resolution path should be preserved
     * @since 5.1.0
     */
    default boolean shouldPreserveLazyProxyTargetResolutionPath(BeanResolutionContext resolutionContext, BeanDefinition<?> proxyBeanDefinition) {
        return true;
    }
}
