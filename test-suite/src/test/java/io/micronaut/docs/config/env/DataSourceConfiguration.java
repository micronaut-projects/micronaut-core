package io.micronaut.docs.config.env;

import java.net.URI;
import java.net.URISyntaxException;

// tag::eachProperty[]
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.EachProperty;

@EachProperty("test.datasource")  // <1>
public class DataSourceConfiguration {

    private final String name;
    private URI url = new URI("localhost");

    public DataSourceConfiguration(@Parameter String name) // <2>
            throws URISyntaxException {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public URI getUrl() { // <3>
        return url;
    }

    public void setUrl(URI url) {
        this.url = url;
    }
}
// end::eachProperty[]