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
package io.micronaut.management.endpoint.processors;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.uri.UriTemplate;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.management.endpoint.EndpointDefaultConfiguration;
import io.micronaut.management.endpoint.annotation.Read;
import io.micronaut.web.router.UriRoute;

import javax.inject.Singleton;
import java.lang.annotation.Annotation;

/**
 * A processor that processes references to {@link Read} operations {@link io.micronaut.management.endpoint.annotation.Endpoint}
 * instances.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class ReadEndpointRouteBuilder extends AbstractEndpointRouteBuilder {

    /**
     * @param beanContext The application context
     * @param uriNamingStrategy The URI naming strategy
     * @param conversionService The conversion service
     * @param endpointDefaultConfiguration Endpoints default Configuration
     */
    public ReadEndpointRouteBuilder(ApplicationContext beanContext,
                                    UriNamingStrategy uriNamingStrategy,
                                    ConversionService<?> conversionService,
                                    EndpointDefaultConfiguration endpointDefaultConfiguration) {
        super(beanContext, uriNamingStrategy, conversionService, endpointDefaultConfiguration);
    }

    @Override
    protected Class<? extends Annotation> getSupportedAnnotation() {
        return Read.class;
    }

    @Override
    protected void registerRoute(ExecutableMethod<?, ?> method, String id) {
        Class<?> declaringType = method.getDeclaringType();
        UriTemplate template = buildUriTemplate(method, id);
        UriRoute uriRoute = GET(template.toString(), declaringType, method.getMethodName(), method.getArgumentTypes());
        if (LOG.isDebugEnabled()) {
            LOG.debug("Created Route to @Endpoint {}: {}", method.getDeclaringType().getName(), uriRoute);
        }
        uriRoute = HEAD(template.toString(), declaringType, method.getMethodName(), method.getArgumentTypes());
        if (LOG.isDebugEnabled()) {
            LOG.debug("Created Route to @Endpoint {}: {}", method.getDeclaringType().getName(), uriRoute);
        }
    }
}
