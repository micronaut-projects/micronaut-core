package io.micronaut.docs;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;

import javax.inject.Singleton;
import java.util.Arrays;

@Factory
public class MeterFilterFactory {

    /**
     * Exclude metrics starting with jvm
     *
     * @return meter filter
     */
    @Bean
    @Singleton
    MeterFilter jvmExclusionFilter() {
        return MeterFilter.denyNameStartsWith("jvm");
    }

    /**
     * Add global tags to all metrics
     *
     * @return meter filter
     */
    @Bean
    @Singleton
    MeterFilter addCommonTagFilter() {
        return MeterFilter.commonTags(Arrays.asList(Tag.of("scope", "demo")));
    }

    /**
     * Rename a tag key for every metric beginning with a given prefix.
     * <p>
     * This will rename the metric name http.server.requests tag value called `method` to `httpmethod`
     * <p>
     * OLD: http.server.requests ['method':'GET", ...]
     * NEW: http.server.requests ['httpmethod':'GET", ...]
     *
     * @return meter filter
     */
    @Bean
    @Singleton
    MeterFilter renameFilter() {
        return MeterFilter.renameTag("http.server.requests", "method", "httpmethod");
    }
}