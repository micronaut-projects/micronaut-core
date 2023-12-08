package io.micronaut.kotlin.processing.inject.configproperties

import io.micronaut.context.annotation.ConfigurationBuilder
import io.micronaut.context.annotation.ConfigurationProperties
import org.neo4j.driver.Config
import java.net.URI

@ConfigurationProperties("neo4j.test")
class Neo4jProperties {
    var uri: URI? = null

    @ConfigurationBuilder(
            prefixes=["with"],
            allowZeroArgs=true
    )
    val options: Config.ConfigBuilder = Config.builder()
}
