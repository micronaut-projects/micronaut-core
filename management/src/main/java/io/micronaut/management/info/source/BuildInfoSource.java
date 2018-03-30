package io.micronaut.management.info.source;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.PropertySource;
import io.micronaut.management.endpoint.info.InfoEndpoint;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;

//TODO: @Refreshable
@Singleton
@Requires(beans = InfoEndpoint.class)
@Requires(property = "endpoints.info.build.enabled") //, notEquals = "false")
public class BuildInfoSource implements InfoSource {

    @Override
    public Publisher<PropertySource> getSource() {


        //TODO: Create a property source from build properties
        //Inject resourceResolver
        return null;
    }
}
