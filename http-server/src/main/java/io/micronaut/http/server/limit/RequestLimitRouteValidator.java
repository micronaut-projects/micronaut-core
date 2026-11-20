/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.server.limit;

import io.micronaut.context.BeanContext;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteInfo;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Fails the startup when a route selects a {@link RequestLimiter} that does not exist, with
 * {@link RequestLimit} or the {@link RequestLimit#ATTRIBUTE} attribute, so that a route never runs
 * uncapped because its limiter is not configured, e.g. in an environment that has no limiter at
 * all. The routes that a route locator finds later are checked when they are matched.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Singleton
@Internal
final class RequestLimitRouteValidator implements ApplicationEventListener<StartupEvent> {

    private final BeanContext beanContext;

    RequestLimitRouteValidator(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        Router router = beanContext.findBean(Router.class).orElse(null);
        if (router == null) {
            return;
        }
        Set<String> known = null;
        List<String> unknown = new ArrayList<>();
        for (UriRouteInfo<?, ?> route : (Iterable<UriRouteInfo<?, ?>>) router.uriRoutes()::iterator) {
            for (String name : names(route)) {
                if (known == null) {
                    known = new TreeSet<>();
                    for (RequestLimiter limiter : beanContext.getBeansOfType(RequestLimiter.class)) {
                        known.add(limiter.getName());
                    }
                }
                if (!known.contains(name)) {
                    unknown.add(route.getHttpMethodName() + " " + route.getUriMatchTemplate() + " -> " + name);
                }
            }
        }
        if (!unknown.isEmpty()) {
            throw new ConfigurationException("Routes select request limiters that are not configured: " + unknown
                + ". Known limiters: " + known + ". Configure them under " + RequestLimiterConfiguration.PREFIX + ".<name>");
        }
    }

    private static List<String> names(UriRouteInfo<?, ?> route) {
        List<String> names = new ArrayList<>(List.of(route.getAnnotationMetadata().stringValues(RequestLimit.class)));
        Object attribute = route.getAttribute(RequestLimit.ATTRIBUTE).orElse(null);
        if (attribute instanceof CharSequence name) {
            names.add(name.toString());
        } else if (attribute instanceof String[] values) {
            names.addAll(List.of(values));
        } else if (attribute instanceof Collection<?> values) {
            for (Object value : values) {
                names.add(String.valueOf(value));
            }
        }
        return names;
    }
}
