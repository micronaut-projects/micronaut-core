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
package io.micronaut.management.endpoint.processors;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.uri.UriTemplate;
import io.micronaut.management.endpoint.Endpoint;
import io.micronaut.management.endpoint.Write;

import javax.inject.Singleton;
import java.lang.annotation.Annotation;

/**
 * A processor that processes references to {@link Write} operations {@link Endpoint} instances
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class WriteEndpointRouteBuilder extends AbstractEndpointRouteBuilder {

    public WriteEndpointRouteBuilder(ApplicationContext beanContext, UriNamingStrategy uriNamingStrategy, ConversionService<?> conversionService) {
        super(beanContext, uriNamingStrategy, conversionService);
    }

    @Override
    protected Class<? extends Annotation> getSupportedAnnotation() {
        return Write.class;
    }

    @Override
    protected void registerRoute(ExecutableMethod<?, ?> method, String id) {
        Class<?> declaringType = method.getDeclaringType();
        UriTemplate template = buildUriTemplate(method, id);
        Write annotation = method.getAnnotation(Write.class);
        POST(template.toString(), declaringType, method.getMethodName(), method.getArgumentTypes())
                .consumes(MediaType.of(annotation.consumes()));
    }

    @Override
    protected boolean isPathParameter(Argument argument) {
        return argument.getAnnotation(QueryValue.class) != null;
    }
}
