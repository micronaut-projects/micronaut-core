/*
 * Copyright 2018 original authors
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

import brave.Tracing;
import brave.opentracing.BraveTracer;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import zipkin2.reporter.AsyncReporter;

import javax.inject.Singleton;

/**
 * Builds a {@link io.opentracing.Tracer} for Brave using {@link brave.opentracing.BraveTracer}
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
@Requires(classes = { BraveTracer.class, Tracing.class})
@Requires(beans = {BraveTracerConfiguration.class, AsyncReporterConfiguration.class})
public class BraveTracerFactory {

    private final BraveTracerConfiguration braveTracerConfiguration;
    private final AsyncReporterConfiguration asyncReporterConfiguration;

    public BraveTracerFactory(BraveTracerConfiguration braveTracerConfiguration, AsyncReporterConfiguration asyncReporterConfiguration) {
        this.braveTracerConfiguration = braveTracerConfiguration;
        this.asyncReporterConfiguration = asyncReporterConfiguration;
    }

    @Bean(preDestroy = "close")
    @Singleton
    Tracing braveTracing() {
        Tracing.Builder builder = braveTracerConfiguration.getTracingBuilder();
        AsyncReporter.Builder reporterBuilder = asyncReporterConfiguration.getBuilder();
        builder.spanReporter(reporterBuilder.build());
        return builder.build();
    }

    @Bean
    @Singleton
    Tracer braveTracer(Tracing tracing) {
        BraveTracer braveTracer = BraveTracer.create(tracing);
        GlobalTracer.register(braveTracer);
        return braveTracer;
    }
}
