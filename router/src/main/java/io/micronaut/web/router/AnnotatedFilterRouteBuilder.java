/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.context.ServerContextPathProvider;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.http.filter.HttpFilter;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;

import edu.umd.cs.findbugs.annotations.Nullable;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
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
    private final ServerContextPathProvider contextPathProvider;

    /**
     * Constructor.
     *
     * @param beanContext The bean context
     * @param executionHandleLocator The execution handler locator
     * @param uriNamingStrategy The URI naming strategy
     * @param conversionService The conversion service
     * @param contextPathProvider The server context path provider
     */
    @Inject
    public AnnotatedFilterRouteBuilder(
            BeanContext beanContext,
            ExecutionHandleLocator executionHandleLocator,
            UriNamingStrategy uriNamingStrategy,
            ConversionService<?> conversionService,
            @Nullable ServerContextPathProvider contextPathProvider) {
        super(executionHandleLocator, uriNamingStrategy, conversionService);
        this.beanContext = beanContext;
        this.contextPathProvider = contextPathProvider;
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
            String[] patterns = getPatterns(beanDefinition);
            if (ArrayUtils.isNotEmpty(patterns)) {
                HttpMethod[] methods = beanDefinition.findAnnotation(Filter.class)
                        .map(av -> av.enumValues("methods", HttpMethod.class))
                        .orElse(null);
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

    /**
     * @param beanDefinition The bean definition
     * @return The array of patterns that should match request URLs for the bean to
     * be invoked.
     */
    protected String[] getPatterns(BeanDefinition<?> beanDefinition) {
        String[] values = beanDefinition.stringValues(Filter.class);
        String contextPath = contextPathProvider != null ? contextPathProvider.getContextPath() : null;
        if (contextPath != null) {
            for (int i = 0; i < values.length; i++) {
                if (!values[i].startsWith(contextPath)) {
                    String newValue = StringUtils.prependUri(contextPath, values[i]);
                    if (newValue.charAt(0) != '/') {
                        newValue = "/" + newValue;
                    }
                    values[i] = newValue;
                }
            }
        }
        return values;
    }
}
