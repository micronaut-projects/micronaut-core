package io.micronaut.docs.config.builder;

// tag::imports[]
import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;

// end::imports[]

/**
 * @author Will Buck
 * @since 1.1
 */
// tag::class[]
@ConfigurationProperties("my.engine") // <1>
class EngineConfig {

    @ConfigurationBuilder(prefixes = "with") // <2>
    EngineImpl.Builder builder = EngineImpl.builder();

    public EngineImpl.Builder getBuilder() { return builder; }

    public void setBuilder(EngineImpl.Builder builder) { this.builder = builder; }

    @ConfigurationBuilder(prefixes = "with", configurationPrefix = "crank-shaft") // <3>
    CrankShaft.Builder crankShaft = CrankShaft.builder();

    public CrankShaft.Builder getCrankShaft() { return crankShaft; }

    public void setCrankShaft(CrankShaft.Builder crankShaft) { this.crankShaft = crankShaft; }

    SparkPlug.Builder sparkPlug = SparkPlug.builder();

    SparkPlug.Builder getSparkPlug() { return this.sparkPlug; }

    @ConfigurationBuilder(prefixes = "with", configurationPrefix = "spark-plug") // <4>
    void setSparkPlug(SparkPlug.Builder sparkPlug) {
        this.sparkPlug = sparkPlug;
    }
}
// end::class[]