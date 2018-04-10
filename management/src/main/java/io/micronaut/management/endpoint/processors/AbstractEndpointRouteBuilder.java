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
import io.micronaut.core.async.subscriber.Completable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.uri.UriTemplate;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.management.endpoint.Endpoint;
import io.micronaut.web.router.DefaultRouteBuilder;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Abstract {@link io.micronaut.web.router.RouteBuilder} implementation for {@link Endpoint} method processors
 *
 * @author Graeme Rocher
 * @since 1.0
 */
abstract class AbstractEndpointRouteBuilder extends DefaultRouteBuilder implements ExecutableMethodProcessor<Endpoint>, Completable {

    private static final Pattern ENDPOINT_ID_PATTERN = Pattern.compile("\\w+");

    private Map<Class, Optional<String>> endpointIds = new ConcurrentHashMap<>();
    private final ApplicationContext beanContext;

    AbstractEndpointRouteBuilder(ApplicationContext applicationContext, UriNamingStrategy uriNamingStrategy, ConversionService<?> conversionService) {
        super(applicationContext, uriNamingStrategy, conversionService);
        this.beanContext = applicationContext;
    }

    @Override
    public final void onComplete() {
        endpointIds.clear();
    }

    @Override
    public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        Class<?> declaringType = method.getDeclaringType();
        if (method.hasStereotype(getSupportedAnnotation())) {
            Optional<String> endPointId = resolveActiveEndPointId(declaringType);
            endPointId.ifPresent(id -> registerRoute(method, id));
        }
    }

    abstract protected Class<? extends Annotation> getSupportedAnnotation();

    abstract protected void registerRoute(ExecutableMethod<?, ?> method, String id);

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

    protected UriTemplate buildUriTemplate(ExecutableMethod<?, ?> method, String id) {
        UriTemplate template = new UriTemplate(uriNamingStrategy.resolveUri(id));
        for (Argument argument : method.getArguments()) {
            if (isPathParameter(argument)) {
                template = template.nest("/{" + argument.getName() + "}");
            }
        }
        return template;
    }

    protected boolean isPathParameter(Argument argument) {
        return argument.getAnnotations().length == 0 || argument.getAnnotation(QueryValue.class) != null;
    }
}
