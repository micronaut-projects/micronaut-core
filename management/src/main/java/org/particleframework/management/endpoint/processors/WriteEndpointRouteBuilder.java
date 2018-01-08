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
package org.particleframework.management.endpoint.processors;

import org.particleframework.context.ApplicationContext;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.type.Argument;
import org.particleframework.http.MediaType;
import org.particleframework.http.annotation.Parameter;
import org.particleframework.http.uri.UriTemplate;
import org.particleframework.inject.ExecutableMethod;
import org.particleframework.management.endpoint.Endpoint;
import org.particleframework.management.endpoint.Write;

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
        return argument.getAnnotation(Parameter.class) != null;
    }
}
