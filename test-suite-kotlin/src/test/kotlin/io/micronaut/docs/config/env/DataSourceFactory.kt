package io.micronaut.docs.config.env

import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.Factory

import java.net.URI
import java.sql.Connection

// tag::eachBean[]
@Factory // <1>
class DataSourceFactory {

    @EachBean(DataSourceConfiguration::class) // <2>
    internal fun dataSource(configuration: DataSourceConfiguration): DataSource { // <3>
        val url = configuration.url
        return DataSource(url)
    }

    // end::eachBean[]
    internal class DataSource(private val uri: URI) {

        fun connect(): Connection {
            throw UnsupportedOperationException("Can't really connect. I'm not a real data source")
        }
    }
}
