package io.micronaut.docs.config.env

import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter

import java.net.URI
import java.net.URISyntaxException

// tag::eachProperty[]

@EachProperty("test.datasource")  // <1>
class DataSourceConfiguration // <2>
@Throws(URISyntaxException::class)
constructor(@param:Parameter val name: String) {
    // <3>
    var url = URI("localhost")
}
// end::eachProperty[]