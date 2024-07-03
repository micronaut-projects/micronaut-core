/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.web.router;

import io.micronaut.context.BeanContext;
import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.context.ServerContextPathProvider;
import io.micronaut.http.filter.BaseFilterProcessor;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.function.Supplier;

/**
 * {@link RouteBuilder} for {@link ServerFilter}s.
 *
 * @since 4.0.0
 * @author Jonas Konrad
 */
@Singleton
@Experimental
public class ServerFilterRouteBuilder extends DefaultRouteBuilder implements ExecutableMethodProcessor<ServerFilter> {
    private final BaseFilterProcessor<ServerFilter> delegate;

    /**
     * @param executionHandleLocator The execution handler locator
     * @param uriNamingStrategy      The URI naming strategy
     * @param conversionService      The conversion service
     * @param beanContext            The bean context
     * @param contextPathProvider    The server context path provider
     */
    public ServerFilterRouteBuilder(
        ExecutionHandleLocator executionHandleLocator,
        UriNamingStrategy uriNamingStrategy,
        ConversionService conversionService,
        BeanContext beanContext,
        @Nullable ServerContextPathProvider contextPathProvider
    ) {
        super(executionHandleLocator, uriNamingStrategy, conversionService);
        delegate = new BaseFilterProcessor<>(beanContext, ServerFilter.class) {
            @NonNull
            @Override
            protected List<String> prependContextPath(@NonNull List<String> patterns) {
                String contextPath = contextPathProvider != null ? contextPathProvider.getContextPath() : null;
                if (contextPath != null) {
                    patterns = patterns.stream()
                        .map(pattern -> {
                            if (!pattern.startsWith(contextPath)) {
                                String newValue = StringUtils.prependUri(contextPath, pattern);
                                if (newValue.charAt(0) != '/') {
                                    newValue = "/" + newValue;
                                }
                                return newValue;
                            } else {
                                return pattern;
                            }
                        })
                        .toList();
                }
                return patterns;
            }

            @Override
            protected void addFilter(Supplier<GenericHttpFilter> factory, AnnotationMetadata methodAnnotations, FilterMetadata metadata) {
                applyMetadata(ServerFilterRouteBuilder.this.addFilter(factory, methodAnnotations), metadata);
            }

            private void applyMetadata(FilterRoute route, FilterMetadata metadata) {
                route.patternStyle(metadata.patternStyle());
                if (metadata.patterns() == null || metadata.patterns().isEmpty()) {
                    throw new IllegalArgumentException("A filter pattern is required");
                }
                for (String pattern : metadata.patterns()) {
                    route.pattern(pattern);
                }
                if (metadata.methods() != null) {
                    route.methods(metadata.methods().toArray(new HttpMethod[0]));
                }
            }
        };
    }

    @Override
    public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        delegate.process(beanDefinition, method);
    }
}
