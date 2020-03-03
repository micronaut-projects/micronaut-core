/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.tracing.brave;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.ReporterMetrics;
import zipkin2.reporter.Sender;

import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * A configuration for async Reporting on {@link zipkin2.Span} instances.
 *
 * @author graemerocher
 * @since 1.0
 */
@ConfigurationProperties(AsyncReporterConfiguration.PREFIX)
@Requires(beans = BraveTracerConfiguration.class)
@Requires(beans = Sender.class)
public class AsyncReporterConfiguration {

    public static final String PREFIX = BraveTracerConfiguration.PREFIX + ".reporter";

    @ConfigurationBuilder(prefixes = "")
    private final AsyncReporter.Builder builder;

    /**
     * Create a configuration for async Reporting on {@link zipkin2.Span} instances.
     *
     * @param configuration BraveTracer configuration
     * @param sender        For sending list of spans to a transport such as http or Kafka
     */
    public AsyncReporterConfiguration(BraveTracerConfiguration configuration, Sender sender) {
        if (configuration == null) {
            throw new IllegalArgumentException("Argument [configuration] cannot be null");
        }
        this.builder = AsyncReporter.builder(sender);
    }

    /**
     * Obtains the {@link zipkin2.reporter.AsyncReporter.Builder}.
     *
     * @return The builder
     */
    public AsyncReporter.Builder getBuilder() {
        return builder;
    }

    /**
     * Sets the {@link ReporterMetrics} instance to use.
     *
     * @param metrics The metrics instance
     */
    @Inject
    public void setReporterMetrics(@Nullable ReporterMetrics metrics) {
        if (metrics != null) {
            builder.metrics(metrics);
        }
    }
}
