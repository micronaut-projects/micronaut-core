package io.micronaut.kotlin.processing.beans.configproperties

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import java.net.URI
import java.net.URISyntaxException

@Factory
class Neo4jPropertiesFactory {

    @Singleton
    @Replaces(Neo4jProperties::class)
    @Requires(property = "spec.name", value = "ConfigurationPropertiesFactorySpec")
    fun neo4jProperties(): Neo4jProperties {
        val props = Neo4jProperties()
        try {
            props.uri = URI("https://google.com")
        } catch (e: URISyntaxException) {
        }
        return props
    }
}
