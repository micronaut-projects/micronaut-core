package io.micronaut.docs.config.env

import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter

// tag::eachProperty[]

@EachProperty("test.datasource")
// <1>
class DataSourceConfiguration {

    final String name
    URI url = new URI("localhost")

    DataSourceConfiguration(@Parameter String name) // <2>
            throws URISyntaxException {
        this.name = name
    }
}
// end::eachProperty[]