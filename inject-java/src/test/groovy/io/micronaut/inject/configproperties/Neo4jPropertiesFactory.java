package io.micronaut.inject.configproperties;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;

import javax.inject.Singleton;
import java.net.URI;
import java.net.URISyntaxException;

@Factory
public class Neo4jPropertiesFactory {

    @Singleton
    @Replaces(Neo4jProperties.class)
    @Requires(property = "spec.name", value = "ConfigurationPropertiesFactorySpec")
    Neo4jProperties neo4jProperties() {
        Neo4jProperties props = new Neo4jProperties();
        try {
            props.uri = new URI("https://google.com");
        } catch (URISyntaxException e) {
        }
        return props;
    }
}
