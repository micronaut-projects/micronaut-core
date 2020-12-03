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

import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.naming.conventions.PropertyConvention;
import io.micronaut.inject.BeanDefinition;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Singleton;

/**
 * The configurable {@link io.micronaut.web.router.RouteBuilder.UriNamingStrategy}
 * if property "micronaut.server.context-path" has been set.
 *
 * @author Andrey Tsarenko
 * @since 1.2.0
 */
@Primary
@Singleton
@Replaces(HyphenatedUriNamingStrategy.class)
@Requires(property = "micronaut.server.context-path")
public class ConfigurableUriNamingStrategy extends HyphenatedUriNamingStrategy {

    private final String contextPath;

    /**
     * Constructs a new uri naming strategy for the given property.
     *
     * @param contextPath the "micronaut.server.context-path" property value
     */
    public ConfigurableUriNamingStrategy(@Value("${micronaut.server.context-path}") String contextPath) {
        this.contextPath = normalizeContextPath(contextPath);
    }

    @Override
    public String resolveUri(Class type) {
        return contextPath + super.resolveUri(type);
    }

    @Override
    public @NonNull String resolveUri(BeanDefinition<?> beanDefinition) {
        return contextPath + super.resolveUri(beanDefinition);
    }

    @Override
    public @NonNull String resolveUri(String property) {
        return contextPath + super.resolveUri(property);
    }

    @Override
    public @NonNull String resolveUri(Class type, PropertyConvention id) {
        return contextPath + super.resolveUri(type, id);
    }

    private String normalizeContextPath(String contextPath) {
        if (contextPath.charAt(0) != '/') {
            contextPath = '/' + contextPath;
        }
        if (contextPath.charAt(contextPath.length() - 1) == '/') {
            contextPath = contextPath.substring(0, contextPath.length() - 1);
        }
        return contextPath;
    }
}

