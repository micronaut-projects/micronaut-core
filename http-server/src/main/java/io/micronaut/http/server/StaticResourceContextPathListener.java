package io.micronaut.http.server;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.util.StringUtils;
import io.micronaut.web.router.resource.StaticResourceConfiguration;

import javax.inject.Singleton;

@Singleton
class StaticResourceContextPathListener implements BeanCreatedEventListener<StaticResourceConfiguration> {

    private final HttpServerConfiguration httpServerConfiguration;

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
