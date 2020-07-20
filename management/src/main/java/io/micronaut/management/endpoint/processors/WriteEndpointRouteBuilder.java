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
package io.micronaut.management.endpoint.processors;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.uri.UriTemplate;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.management.endpoint.EndpointDefaultConfiguration;
import io.micronaut.management.endpoint.annotation.Selector;
import io.micronaut.management.endpoint.annotation.Write;
import io.micronaut.web.router.UriRoute;

import javax.inject.Singleton;
import java.lang.annotation.Annotation;

/**
 * A processor that processes references to {@link Write} operations {@link io.micronaut.management.endpoint.annotation.Endpoint}
 * instances.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class WriteEndpointRouteBuilder extends AbstractEndpointRouteBuilder {

    /**
     * @param beanContext       The application context
     * @param uriNamingStrategy The URI naming strategy
     * @param conversionService The conversion service
     * @param endpointDefaultConfiguration Endpoints default Configuration
     */
    public WriteEndpointRouteBuilder(ApplicationContext beanContext,
                                     UriNamingStrategy uriNamingStrategy,
                                     ConversionService<?> conversionService,
                                     EndpointDefaultConfiguration endpointDefaultConfiguration) {
        super(beanContext, uriNamingStrategy, conversionService, endpointDefaultConfiguration);
    }

    @Override
    protected Class<? extends Annotation> getSupportedAnnotation() {
        return Write.class;
    }

    @Override
    protected void registerRoute(ExecutableMethod<?, ?> method, String id, Integer port) {
        Class<?> declaringType = method.getDeclaringType();
        UriTemplate template = buildUriTemplate(method, id);
        String[] consumes = method.stringValues(Write.class, "consumes");
        UriRoute uriRoute = POST(template.toString(), declaringType, method.getMethodName(), method.getArgumentTypes())
                .consumes(MediaType.of(consumes));
        if (port != null) {
            uriRoute = uriRoute.exposedPort(port);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Created Route to @Endpoint {}: {}", method.getDeclaringType().getName(), uriRoute);
        }
    }

    @Override
    protected boolean isPathParameter(Argument argument) {
        return argument.isDeclaredAnnotationPresent(Selector.class);
    }
}
