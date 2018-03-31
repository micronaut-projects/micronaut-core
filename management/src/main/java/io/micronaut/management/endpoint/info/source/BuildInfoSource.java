package io.micronaut.management.endpoint.info.source;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.PropertySource;
import io.micronaut.management.endpoint.info.InfoEndpoint;
import io.micronaut.management.endpoint.info.InfoSource;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;

/**
 * <p>An {@link InfoSource} that retrieves info from build properties. </p>
 *
 * @author Zachary Klein
 * @since 1.0
 */
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
