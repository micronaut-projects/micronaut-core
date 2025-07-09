/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.context.conditions;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.io.file.FileSystemResourceLoader;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.io.value.Base64ResourceLoader;
import io.micronaut.core.io.value.StringResourceLoader;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Matches resources of classes condition.
 *
 * @param resourcePaths The resources
 * @author Denis Stepanov
 * @since 4.6
 */
@UsedByGeneratedCode
@Internal
public record MatchesPresenceOfResourcesCondition(String[] resourcePaths) implements Condition {
    @Override
    public boolean matches(ConditionContext context) {
        final BeanContext beanContext = context.getBeanContext();
        final List<ResourceLoader> resourceLoaders;
        if (beanContext instanceof ApplicationContext applicationContext) {
            ResourceLoader resourceLoader = applicationContext.getEnvironment();
            resourceLoaders = Arrays.asList(resourceLoader, FileSystemResourceLoader.defaultLoader());
        } else {
            resourceLoaders = Arrays.asList(
                ClassPathResourceLoader.defaultLoader(beanContext.getClassLoader()),
                FileSystemResourceLoader.defaultLoader(),
                Base64ResourceLoader.getInstance(),
                StringResourceLoader.getInstance()
            );
        }
        ResourceResolver resolver = new ResourceResolver(resourceLoaders);
        for (String resourcePath : resourcePaths) {
            if (resolver.getResource(resourcePath).isEmpty()) {
                context.fail("Resource [" + resourcePath + "] does not exist");
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MatchesPresenceOfResourcesCondition that = (MatchesPresenceOfResourcesCondition) o;
        return Objects.deepEquals(resourcePaths, that.resourcePaths);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(resourcePaths);
    }

    @Override
    public String toString() {
        return "MatchesPresenceOfResourcesCondition{" +
            "resourcePaths=" + Arrays.toString(resourcePaths) +
            '}';
    }
}
