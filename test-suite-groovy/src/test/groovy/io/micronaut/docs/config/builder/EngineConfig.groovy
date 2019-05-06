package io.micronaut.docs.config.builder

// tag::imports[]
import io.micronaut.context.annotation.ConfigurationBuilder
import io.micronaut.context.annotation.ConfigurationProperties
// end::imports[]

// tag::class[]
@ConfigurationProperties('my.engine') // <1>
class EngineConfig {

    @ConfigurationBuilder(prefixes = "with") // <2>
    EngineImpl.Builder builder = EngineImpl.builder()

    @ConfigurationBuilder(prefixes = "with", configurationPrefix = "crank-shaft") // <3>
    CrankShaft.Builder crankShaft = CrankShaft.builder()

    SparkPlug.Builder sparkPlug = SparkPlug.builder()

    @ConfigurationBuilder(prefixes = "with", configurationPrefix = "spark-plug") // <4>
    void setSparkPlug(SparkPlug.Builder sparkPlug) {
        this.sparkPlug = sparkPlug
    }
}
// end::class[]
