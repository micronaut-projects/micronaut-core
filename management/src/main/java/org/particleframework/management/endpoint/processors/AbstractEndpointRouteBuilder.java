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
import org.particleframework.context.exceptions.BeanContextException;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.type.Argument;
import org.particleframework.core.async.subscriber.Completable;
import org.particleframework.http.annotation.Parameter;
import org.particleframework.http.uri.UriTemplate;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.ExecutableMethod;
import org.particleframework.inject.qualifiers.Qualifiers;
import org.particleframework.management.endpoint.Endpoint;
import org.particleframework.management.endpoint.EndpointConfiguration;
import org.particleframework.web.router.DefaultRouteBuilder;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Abstract {@link org.particleframework.web.router.RouteBuilder} implementation for {@link Endpoint} method processors
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class AbstractEndpointRouteBuilder extends DefaultRouteBuilder implements Completable  {
    private static final Pattern ENDPOINT_ID_PATTERN = Pattern.compile("\\w+");

    private Map<Class, Optional<EndpointConfiguration>> endpointIds = new ConcurrentHashMap<>();
    private final ApplicationContext beanContext;

    AbstractEndpointRouteBuilder(ApplicationContext applicationContext, UriNamingStrategy uriNamingStrategy, ConversionService<?> conversionService) {
        super(applicationContext, uriNamingStrategy, conversionService);
        this.beanContext = applicationContext;
    }

    protected Optional<EndpointConfiguration> resolveActiveEndPointId(Class<?> declaringType) {
        return endpointIds.computeIfAbsent(declaringType, aClass -> {
            Optional<? extends BeanDefinition<?>> opt = beanContext.findBeanDefinition(declaringType);
            if (opt.isPresent()) {
                Endpoint endpoint = opt.get().getAnnotation(Endpoint.class);
                if (endpoint != null) {
                    String id = endpoint.value();
                    if (!ENDPOINT_ID_PATTERN.matcher(id).matches()) {
                        throw new BeanContextException("Invalid @Endpoint ID - Should only contain letters: " + id);
                    }

                    Optional<EndpointConfiguration> config = beanContext.findBean(EndpointConfiguration.class, Qualifiers.byName(id));
                    if(config.isPresent()) {
                        EndpointConfiguration endpointConfiguration = config.get();
                        if(endpointConfiguration.isEnabled()) {

                            return Optional.of(endpointConfiguration);
                        }
                    }
                }
            }

            return Optional.empty();
        });
    }

    @Override
    public final void onComplete() {
        endpointIds.clear();
    }

    protected UriTemplate buildUriTemplate(ExecutableMethod<Object, Object> method, String id) {
        UriTemplate template = new UriTemplate(uriNamingStrategy.resolveUri(id));
        for (Argument argument : method.getArguments()) {
            if(isPathParameter(argument)) {
                template = template.nest("/{" + argument.getName() + "}");
            }
        }
        return template;
    }

    protected boolean isPathParameter(Argument argument) {
        return argument.getAnnotations().length == 0 || argument.getAnnotation(Parameter.class) != null;
    }
}
