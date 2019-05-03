package io.micronaut.docs.config.builder

// tag::imports[]
import io.micronaut.context.annotation.ConfigurationBuilder
import io.micronaut.context.annotation.ConfigurationProperties
// end::imports[]

// tag::class[]
@ConfigurationProperties("my.engine") // <1>
internal class EngineConfig {
    @ConfigurationBuilder(prefixes = ["with"])  // <2>
    val builder = EngineImpl.builder()

    @ConfigurationBuilder(prefixes = ["with"], configurationPrefix = "crank-shaft") // <3>
    val crankShaft = CrankShaft.builder()

    @set:ConfigurationBuilder(prefixes = ["with"], configurationPrefix = "spark-plug") // <4>
    var sparkPlug = SparkPlug.builder()
}
// end::class[]