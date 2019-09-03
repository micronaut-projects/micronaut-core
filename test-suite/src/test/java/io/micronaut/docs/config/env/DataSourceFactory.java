package io.micronaut.docs.config.env;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;

import java.net.URI;
import java.sql.Connection;

// tag::eachBean[]
@Factory // <1>
public class DataSourceFactory {

    @EachBean(DataSourceConfiguration.class) // <2>
    DataSource dataSource(DataSourceConfiguration configuration) { // <3>
        URI url = configuration.getUrl();
        return new DataSource(url);
    }

// end::eachBean[]
    static class DataSource {
        private final URI uri;

        public DataSource(URI uri) {
            this.uri = uri;
        }

        Connection connect() {
            throw new UnsupportedOperationException("Can't really connect. I'm not a real data source");
        }
    }
}
