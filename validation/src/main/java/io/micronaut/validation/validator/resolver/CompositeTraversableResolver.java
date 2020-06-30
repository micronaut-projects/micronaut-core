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
package io.micronaut.validation.validator.resolver;

import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;

import javax.validation.Path;
import javax.validation.TraversableResolver;
import java.lang.annotation.ElementType;
import java.util.List;

/**
 * Primary {@link TraversableResolver} that takes into account all configured {@link TraversableResolver} instances.
 *
 * @author graemerocher
 * @since 1.2.0
 */
@Primary
@Internal
public class CompositeTraversableResolver implements TraversableResolver {

    private final List<TraversableResolver> traversableResolvers;

    /**
     * Default constructor.
     * @param traversableResolvers The traversable resolvers
     */
    public CompositeTraversableResolver(List<TraversableResolver> traversableResolvers) {
        this.traversableResolvers = CollectionUtils.isEmpty(traversableResolvers) ? null : traversableResolvers;
    }

    @Override
    public boolean isReachable(Object traversableObject, Path.Node traversableProperty, Class<?> rootBeanType, Path pathToTraversableObject, ElementType elementType) {
        if (traversableResolvers == null) {
            return true;
        }
        return traversableResolvers.stream().allMatch(r ->
                r.isReachable(traversableObject, traversableProperty, rootBeanType, pathToTraversableObject, elementType)
        );
    }

    @Override
    public boolean isCascadable(Object traversableObject, Path.Node traversableProperty, Class<?> rootBeanType, Path pathToTraversableObject, ElementType elementType) {
        if (traversableResolvers == null) {
            return true;
        }
        return traversableResolvers.stream().allMatch(r ->
            r.isCascadable(traversableObject, traversableProperty, rootBeanType, pathToTraversableObject, elementType)
        );
    }
}
