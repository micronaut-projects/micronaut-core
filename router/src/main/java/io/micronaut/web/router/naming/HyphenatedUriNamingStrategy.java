/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.web.router.naming;

import io.micronaut.context.annotation.Primary;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.naming.conventions.TypeConvention;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.UriMapping;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.web.router.RouteBuilder;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Singleton;

/**
 * The default {@link io.micronaut.web.router.RouteBuilder.UriNamingStrategy} if none is provided by the application.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Primary
public class HyphenatedUriNamingStrategy implements RouteBuilder.UriNamingStrategy {
    @Override
    public String resolveUri(Class type) {
        return '/' + TypeConvention.CONTROLLER.asHyphenatedName(type);
    }

    @Override
    public @NonNull String resolveUri(BeanDefinition<?> beanDefinition) {
        String uri = beanDefinition.stringValue(UriMapping.class).orElseGet(() ->
                beanDefinition.stringValue(Controller.class).orElse(UriMapping.DEFAULT_URI)
        );
        return normalizeUri(uri);
    }

    @Override
    public @NonNull String resolveUri(String property) {
        if (StringUtils.isEmpty(property)) {
            return "/";
        }
        if (property.charAt(0) != '/') {
            return '/' + NameUtils.hyphenate(property, true);
        }
        return property;
    }
}
