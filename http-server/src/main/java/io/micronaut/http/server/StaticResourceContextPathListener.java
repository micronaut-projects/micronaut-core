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
package io.micronaut.http.server;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.util.StringUtils;
import io.micronaut.web.router.resource.StaticResourceConfiguration;

import javax.inject.Singleton;

/**
 * Prepends the server context path to any static resource paths.
 *
 * @author James Kleeh
 * @since 1.2.7
 */
@Singleton
class StaticResourceContextPathListener implements BeanCreatedEventListener<StaticResourceConfiguration> {

    private final HttpServerConfiguration httpServerConfiguration;

    /**
     * @param httpServerConfiguration The server configuration
     */
    StaticResourceContextPathListener(HttpServerConfiguration httpServerConfiguration) {
        this.httpServerConfiguration = httpServerConfiguration;
    }

    @Override
    public StaticResourceConfiguration onCreated(BeanCreatedEvent<StaticResourceConfiguration> event) {
        String contextPath = httpServerConfiguration.getContextPath();
        if (contextPath == null) {
            return event.getBean();
        } else {
            StaticResourceConfiguration configuration = event.getBean();
            configuration.setMapping(StringUtils.prependUri(contextPath, configuration.getMapping()));
            return configuration;
        }
    }
}
