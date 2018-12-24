package io.micronaut.web.router.version;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.web.router.Router;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Configuration to decorate {@link Router} with version matching logic.
 */
@Singleton
@Requires(beans = RoutesVersioningConfiguration.class)
public class VersionedRouterDecoratorConfiguration implements BeanCreatedEventListener<Router> {

    private final RouteMatchesFilter versionedUrlFilter;

    /**
     * Creates a configuration to decorate existing {@link Router} beans with a {@link VersionedRouter}.
     *
     * @param filter A {@link RouteMatchesFilter} to delegate routes filtering
     */
    @Inject
    public VersionedRouterDecoratorConfiguration(RouteMatchesFilter filter) {
        this.versionedUrlFilter = filter;
    }

    /**
     * Returns a wrapped {@link Router} to {@link VersionedRouter}.
     *
     * @param event The {@link Router} bean created event
     * @return The wrapper router bean
     */
    @Override
    public Router onCreated(BeanCreatedEvent<Router> event) {
        return new VersionedRouter(
                event.getBean(), versionedUrlFilter
        );
    }
}
