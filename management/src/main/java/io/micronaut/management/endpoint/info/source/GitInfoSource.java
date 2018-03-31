package io.micronaut.management.endpoint.info.source;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.management.endpoint.info.InfoEndpoint;
import io.micronaut.management.endpoint.info.InfoSource;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;

//TODO: @Refreshable
@Singleton
@Requires(beans = InfoEndpoint.class)
@Requires(property = "endpoints.info.git.enabled")//, notEquals = "false")
public class GitInfoSource implements InfoSource {

    private ResourceResolver resourceResolver;

    public GitInfoSource(ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    @Override
    public Publisher<PropertySource> getSource() {


        //TODO: Create a property source from git properties
        //Inject resourceResolver
        return null;
    }
}
