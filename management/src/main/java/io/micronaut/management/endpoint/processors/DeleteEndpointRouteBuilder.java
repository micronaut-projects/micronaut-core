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

package io.micronaut.management.endpoint.processors;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.uri.UriTemplate;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.management.endpoint.Delete;

import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.util.Collection;

/**
 * A processor that processes references to {@link Delete} operations {@link io.micronaut.management.endpoint.Endpoint}
 * instances.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class DeleteEndpointRouteBuilder extends AbstractEndpointRouteBuilder {

    /**
     * @param beanContext       The application contect
     * @param uriNamingStrategy The URI naming strategy
     * @param conversionService The conversion service
     * @param nonPathTypesProviders A list of providers which defines types not to be used as Path parameters
     */
    public DeleteEndpointRouteBuilder(ApplicationContext beanContext,
                                      UriNamingStrategy uriNamingStrategy,
                                      ConversionService<?> conversionService,
                                      Collection<NonPathTypesProvider> nonPathTypesProviders) {
        super(beanContext, uriNamingStrategy, conversionService, nonPathTypesProviders);
    }

    @Override
    protected Class<? extends Annotation> getSupportedAnnotation() {
        return Delete.class;
    }

    @Override
    protected void registerRoute(ExecutableMethod<?, ?> method, String id) {
        Class<?> declaringType = method.getDeclaringType();
        UriTemplate template = buildUriTemplate(method, id);
        DELETE(template.toString(), declaringType, method.getMethodName(), method.getArgumentTypes());
    }
}
