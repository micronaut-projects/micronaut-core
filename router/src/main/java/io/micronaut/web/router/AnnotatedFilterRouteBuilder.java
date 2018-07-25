/*
 * Copyright 2017-2018 original authors
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

import io.micronaut.context.BeanContext;
import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.http.filter.HttpFilter;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;

import javax.annotation.PostConstruct;
import javax.inject.Singleton;
import java.util.Collection;

/**
 * An {@link io.micronaut.context.processor.ExecutableMethodProcessor} for the {@link Filter} annotation.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class AnnotatedFilterRouteBuilder extends DefaultRouteBuilder {

    private final BeanContext beanContext;

    /**
     * Constructor.
     *
     * @param beanContext The bean context
     * @param executionHandleLocator The execution handler locator
     * @param uriNamingStrategy The URI naming strategy
     * @param conversionService The conversion service
     */
    public AnnotatedFilterRouteBuilder(
        BeanContext beanContext,
        ExecutionHandleLocator executionHandleLocator,
        UriNamingStrategy uriNamingStrategy,
        ConversionService<?> conversionService) {
        super(executionHandleLocator, uriNamingStrategy, conversionService);
        this.beanContext = beanContext;
    }

    /**
     * Executed after the bean creation.
     */
    @PostConstruct
    public void process() {
        Collection<BeanDefinition<?>> filterDefinitions = beanContext.getBeanDefinitions(Qualifiers.byStereotype(Filter.class));
        for (BeanDefinition<?> beanDefinition : filterDefinitions) {
            if (HttpClientFilter.class.isAssignableFrom(beanDefinition.getBeanType())) {
                // ignore http client filters
                continue;
            }
            String[] patterns = beanDefinition.getValue(Filter.class, String[].class).orElse(null);
            if (ArrayUtils.isNotEmpty(patterns)) {
                HttpMethod[] methods = beanDefinition.getValue(Filter.class, "methods", HttpMethod[].class).orElse(null);
                String first = patterns[0];
                FilterRoute filterRoute = addFilter(first, () -> beanContext.getBean((Class<HttpFilter>) beanDefinition.getBeanType()));
                if (patterns.length > 1) {
                    for (int i = 1; i < patterns.length; i++) {
                        String pattern = patterns[i];
                        filterRoute.pattern(pattern);
                    }
                }
                if (ArrayUtils.isNotEmpty(methods)) {
                    filterRoute.methods(methods);
                }
            }
        }
    }
}
