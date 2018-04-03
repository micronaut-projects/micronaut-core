package io.micronaut.management.endpoint.info.source;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.management.endpoint.info.InfoEndpoint;
import io.micronaut.management.endpoint.info.InfoSource;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;

/**
 * <p>An {@link InfoSource} that retrieves info from Git properties. </p>
 *
 * @author Zachary Klein
 * @since 1.0
 */
//TODO: @Refreshable
@Singleton
@Requires(beans = InfoEndpoint.class)
@Requires(property = "endpoints.info.git.enabled", notEquals = "false")
public class GitInfoSource implements InfoSource {

    private ResourceResolver resourceResolver;

    public GitInfoSource(ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    @Override
    public Publisher<PropertySource> getSource() {
        return Flowable.empty();
    }
}
