/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.tracing.brave;

import brave.CurrentSpanCustomizer;
import brave.SpanCustomizer;
import brave.Tracing;
import brave.opentracing.BraveTracer;
import io.micronaut.context.annotation.*;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import zipkin2.Span;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Reporter;

import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Singleton;

/**
 * Builds a {@link io.opentracing.Tracer} for Brave using {@link brave.opentracing.BraveTracer}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
@Requires(beans = {BraveTracerConfiguration.class})
public class BraveTracerFactory {

    private final BraveTracerConfiguration braveTracerConfiguration;

    /**
     * Initialize the factory with tracer configuration.
     *
     * @param braveTracerConfiguration The tracer configuration
     */
    public BraveTracerFactory(BraveTracerConfiguration braveTracerConfiguration) {
        this.braveTracerConfiguration = braveTracerConfiguration;
    }

    /**
     * The {@link Tracing} bean.
     *
     * @param reporter An optional {@link Reporter}
     * @return The {@link Tracing} bean
     */
    @Bean(preDestroy = "close")
    @Singleton
    @Requires(classes = Tracing.class)
    Tracing braveTracing(@Nullable Reporter<Span> reporter) {
        Tracing.Builder builder = braveTracerConfiguration.getTracingBuilder();
        if (reporter != null) {
            builder.spanReporter(reporter);
        } else {
            builder.spanReporter(Reporter.NOOP);
        }
        return builder.build();
    }

    /**
     * The {@link SpanCustomizer} bean.
     *
     * @param tracing The {@link Tracing} bean
     * @return The {@link SpanCustomizer} bean
     */
    @Singleton
    @Requires(beans = Tracing.class)
    @Requires(missingBeans = SpanCustomizer.class)
    SpanCustomizer spanCustomizer(Tracing tracing) {
        return CurrentSpanCustomizer.create(tracing);
    }

    /**
     * The Open Tracing {@link Tracer} bean.
     *
     * @param tracing The {@link Tracing} bean
     * @return The Open Tracing {@link Tracer} bean
     */
    @Singleton
    @Requires(classes = {BraveTracer.class, Tracer.class})
    @Primary
    Tracer braveTracer(Tracing tracing) {
        BraveTracer braveTracer = BraveTracer.create(tracing);
        GlobalTracer.registerIfAbsent(braveTracer);
        return braveTracer;
    }

    /**
     * A {@link Reporter} that is configured if no other Reporter is present and {@link AsyncReporterConfiguration} is enabled.
     *
     * @param configuration The configuration
     * @return The {@link AsyncReporter} bean
     */
    @Prototype
    @Requires(beans = AsyncReporterConfiguration.class)
    @Requires(missingBeans = Reporter.class)
    AsyncReporter<Span> asyncReporter(AsyncReporterConfiguration configuration) {
        return configuration.getBuilder().build();
    }
}
