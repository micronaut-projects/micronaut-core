/*
 * Copyright 2017 original authors
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
package org.particleframework.web.router;

import org.particleframework.context.BeanContext;
import org.particleframework.context.ExecutionHandleLocator;
import org.particleframework.context.processor.ExecutableMethodProcessor;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.util.ArrayUtils;
import org.particleframework.http.HttpMethod;
import org.particleframework.http.annotation.Filter;
import org.particleframework.http.filter.HttpClientFilter;
import org.particleframework.http.filter.HttpFilter;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.qualifiers.Qualifiers;

import javax.annotation.PostConstruct;
import javax.inject.Singleton;
import java.util.Collection;

/**
 * An {@link ExecutableMethodProcessor} for the {@link Filter} annotation
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class AnnotatedFilterRouteBuilder extends DefaultRouteBuilder  {


    private final BeanContext beanContext;

    public AnnotatedFilterRouteBuilder(
            BeanContext beanContext,
            ExecutionHandleLocator executionHandleLocator,
            UriNamingStrategy uriNamingStrategy,
            ConversionService<?> conversionService) {
        super(executionHandleLocator, uriNamingStrategy, conversionService);
        this.beanContext = beanContext;
    }

    @PostConstruct
    public void process() {
        Collection<BeanDefinition<?>> filterDefinitions = beanContext.getBeanDefinitions(Qualifiers.byStereotype(Filter.class));
        for (BeanDefinition<?> beanDefinition : filterDefinitions) {
            if(HttpClientFilter.class.isAssignableFrom(beanDefinition.getBeanType())) {
                // ignore http client filters
                continue;
            }
            Filter filterAnn = beanDefinition.getAnnotation(Filter.class);
            String[] patterns = filterAnn.value();
            if(ArrayUtils.isNotEmpty(patterns)) {
                HttpMethod[] methods = filterAnn.methods();
                String first = patterns[0];
                FilterRoute filterRoute = addFilter(first, () -> beanContext.getBean((Class<HttpFilter>) beanDefinition.getBeanType()));
                if(patterns.length > 1) {
                    for (int i = 1; i < patterns.length; i++) {
                        String pattern = patterns[i];
                        filterRoute.pattern(pattern);
                    }
                }
                if(ArrayUtils.isNotEmpty(methods)) {
                    filterRoute.methods(methods);
                }
            }
        }
    }
}
