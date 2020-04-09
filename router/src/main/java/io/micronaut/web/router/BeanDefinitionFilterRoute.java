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
package io.micronaut.web.router;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.context.BeanLocator;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.filter.HttpFilter;
import io.micronaut.inject.BeanDefinition;

/**
 * Extended version of {@link DefaultFilterRoute} that optimizes bean lookup.
 *
 * @author graemerocher
 * @since 2.0
 */
@Internal
class BeanDefinitionFilterRoute extends DefaultFilterRoute {

    private final BeanDefinition<? extends HttpFilter> definition;

    /**
     * Default constructor.
     * @param pattern The pattern
     * @param beanLocator The bean locator
     * @param definition The definition
     */
    BeanDefinitionFilterRoute(String pattern, BeanLocator beanLocator, BeanDefinition<? extends HttpFilter> definition) {
        super(pattern, () -> beanLocator.getBean(definition));
        this.definition = definition;
    }

    @NonNull
    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return definition.getAnnotationMetadata();
    }
}
