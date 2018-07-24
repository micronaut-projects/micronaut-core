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
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.async.subscriber.Completable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.uri.UriTemplate;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.management.endpoint.Endpoint;
import io.micronaut.management.endpoint.EndpointDefaultConfiguration;
import io.micronaut.web.router.DefaultRouteBuilder;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Abstract {@link io.micronaut.web.router.RouteBuilder} implementation for {@link Endpoint} method processors.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
abstract class AbstractEndpointRouteBuilder extends DefaultRouteBuilder implements ExecutableMethodProcessor<Endpoint>, Completable {

    private static final Pattern ENDPOINT_ID_PATTERN = Pattern.compile("\\w+");

    private Map<Class, Optional<String>> endpointIds = new ConcurrentHashMap<>();

    private final ApplicationContext beanContext;

    private final List<String> nonPathTypes;

    private final EndpointDefaultConfiguration endpointDefaultConfiguration;

    /**
     * @param applicationContext The application context
     * @param uriNamingStrategy  The URI naming strategy
     * @param conversionService  The conversion service
     * @param nonPathTypesProviders A list of providers which defines paths not to be used as Path paramters
     * @param endpointDefaultConfiguration Endpoints default Configuration
     */
    AbstractEndpointRouteBuilder(ApplicationContext applicationContext,
                                 UriNamingStrategy uriNamingStrategy,
                                 ConversionService<?> conversionService,
                                 Collection<NonPathTypesProvider> nonPathTypesProviders,
                                 EndpointDefaultConfiguration endpointDefaultConfiguration) {
        super(applicationContext, uriNamingStrategy, conversionService);
        this.beanContext = applicationContext;
        nonPathTypes = nonPathTypesProviders.stream()
                .map(NonPathTypesProvider::nonPathTypes)
                .flatMap(List::stream)
                .map(Class::getName)
                .collect(Collectors.toList());
        this.endpointDefaultConfiguration = endpointDefaultConfiguration;
    }

    /**
     * @return The class
     */
    protected abstract Class<? extends Annotation> getSupportedAnnotation();

    /**
     * Register a route.
     *
     * @param method The {@link ExecutableMethod}
     * @param id     The route id
     */
    protected abstract void registerRoute(ExecutableMethod<?, ?> method, String id);

    /**
     * Clears endpoint ids information.
     */
    @Override
    public final void onComplete() {
        endpointIds.clear();
    }

    /**
     * @param beanDefinition The bean definition to process
     * @param method         The executable method
     */
    @Override
    public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        Class<?> declaringType = method.getDeclaringType();
        if (method.hasStereotype(getSupportedAnnotation())) {
            Optional<String> endPointId = resolveActiveEndPointId(declaringType);
            endPointId.ifPresent(id -> registerRoute(method, id));
        }
    }

    /**
     * @param declaringType The type
     * @return An optional string with the endpoint id
     */
    protected Optional<String> resolveActiveEndPointId(Class<?> declaringType) {
        return endpointIds.computeIfAbsent(declaringType, aClass -> {
            Optional<? extends BeanDefinition<?>> opt = beanContext.findBeanDefinition(declaringType);
            if (opt.isPresent()) {
                BeanDefinition<?> beanDefinition = opt.get();
                if (beanDefinition.hasStereotype(Endpoint.class)) {
                    String id = beanDefinition.getValue(Endpoint.class, String.class).orElse(null);
                    if (id == null || !ENDPOINT_ID_PATTERN.matcher(id).matches()) {
                        id = NameUtils.hyphenate(beanDefinition.getName());
                    }

                    return Optional.ofNullable(id);
                }
            }

            return Optional.empty();
        });
    }

    /**
     * @param method The {@link ExecutableMethod}
     * @param id     The route id
     * @return An {@link UriTemplate}
     */
    protected UriTemplate buildUriTemplate(ExecutableMethod<?, ?> method, String id) {
        UriTemplate template = new UriTemplate(resolveUriByRouteId(id));
        for (Argument argument : method.getArguments()) {
            if (isPathParameter(argument)) {
                template = template.nest("/{" + argument.getName() + "}");
            }
        }
        return template;
    }

    /**
     * @param id The route id
     * @return {@link EndpointDefaultConfiguration#path} + resolved Uri based on UriNamingStrategy
     */
    String resolveUriByRouteId(String id) {
        String uri = uriNamingStrategy.resolveUri(id);
        if (endpointDefaultConfiguration.getPath().equals("/") && uri.charAt(0) == '/') {
            return uri;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(endpointDefaultConfiguration.getPath());
        sb.append(uri);
        return sb.toString();
    }

    /**
     * @param argument An {@link Argument}
     * @return Whether the argument is a path parameter
     */
    protected boolean isPathParameter(Argument argument) {
        if (nonPathTypes.contains(argument.getType().getName())) {
            return false;
        }
        AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();
        return annotationMetadata.isEmpty() || annotationMetadata.hasDeclaredAnnotation(QueryValue.class);
    }
}
