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
package io.micronaut.web.router.naming;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.naming.conventions.TypeConvention;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.UriMapping;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.web.router.RouteBuilder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * The default {@link io.micronaut.web.router.RouteBuilder.UriNamingStrategy} if none is provided by the application.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class HyphenatedUriNamingStrategy implements RouteBuilder.UriNamingStrategy {
    private final String contextPath;

    /**
     * Constructor without context path.
     */
    public HyphenatedUriNamingStrategy() {
        this(null);
    }

    /**
     * Constructor with optional context path.
     *
     * @param contextPath The context path to prefix to all URIs
     */
    @Inject
    public HyphenatedUriNamingStrategy(@Nullable @Value("${micronaut.server.context-path}") String contextPath) {
        if (contextPath == null) {
            contextPath = "";
        }
        this.contextPath = normalizeContextPath(contextPath);
    }

    @Override
    public String resolveUri(Class type) {
        return contextPath + '/' + TypeConvention.CONTROLLER.asHyphenatedName(type);
    }

    @Override
    public @NonNull String resolveUri(BeanDefinition<?> beanDefinition) {
        String uri = beanDefinition.stringValue(UriMapping.class).orElseGet(() ->
                beanDefinition.stringValue(Controller.class).orElse(UriMapping.DEFAULT_URI)
        );
        return contextPath + normalizeUri(uri);
    }

    @Override
    public @NonNull String resolveUri(String property) {
        if (StringUtils.isEmpty(property)) {
            return contextPath + "/";
        }
        if (property.charAt(0) != '/') {
            return contextPath + '/' + NameUtils.hyphenate(property, true);
        }
        return contextPath + property;
    }

    static String normalizeContextPath(String contextPath) {
        if (StringUtils.isNotEmpty(contextPath)) {
            if (contextPath.charAt(0) != '/') {
                contextPath = '/' + contextPath;
            }
            if (contextPath.charAt(contextPath.length() - 1) == '/') {
                contextPath = contextPath.substring(0, contextPath.length() - 1);
            }
        }
        return contextPath;
    }
}
