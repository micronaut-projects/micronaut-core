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
package io.micronaut.tracing.jaeger;

import io.jaegertracing.Configuration;
import io.jaegertracing.internal.JaegerTracer;
import io.jaegertracing.spi.Reporter;
import io.jaegertracing.spi.Sampler;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;

import edu.umd.cs.findbugs.annotations.Nullable;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.Closeable;
import java.io.IOException;

/**
 * Registers a Jaeger tracer based on the jaeger configuration.
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
@Requires(classes = JaegerTracer.Builder.class)
@Requires(beans = JaegerConfiguration.class)
public class JaegerTracerFactory implements Closeable {

    private final JaegerConfiguration configuration;
    private Reporter reporter;
    private Sampler sampler;

    /**
     * Initialize Jaeger trace factory with the configurations.
     *
     * @param configuration Jaeger configurations
     */
    public JaegerTracerFactory(JaegerConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Allows setting a custom reporter.
     *
     * @param reporter The {@link Reporter}
     */
    @Inject
    public void setReporter(@Nullable Reporter reporter) {
        this.reporter = reporter;
    }

    /**
     * Allows setting a custom sampler.
     *
     * @param sampler {@link Sampler}
     */
    @Inject
    public void setSampler(@Nullable Sampler sampler) {
        this.sampler = sampler;
    }


    /**
     * The Jaeger configuration bean.
     *
     * @return The configuration
     */
    @Singleton
    @Primary
    @Requires(classes = JaegerTracer.Builder.class)
    Configuration jaegerConfiguration() {
        return this.configuration.getConfiguration();
    }

    /**
     * The Jaeger Tracer builder bean.
     *
     * @param configuration The configuration
     * @return The builder
     */
    @Singleton
    @Primary
    @Requires(classes = JaegerTracer.Builder.class)
    JaegerTracer.Builder jaegerTracerBuilder(Configuration configuration) {
        JaegerTracer.Builder tracerBuilder = resolveBuilder(configuration);
        if (this.configuration.isExpandExceptionLogs()) {
            tracerBuilder.withExpandExceptionLogs();
        }
        if (this.configuration.isZipkinSharedRpcSpan()) {
            tracerBuilder.withZipkinSharedRpcSpan();
        }
        if (reporter != null) {
            tracerBuilder.withReporter(reporter);
        }
        if (sampler != null) {
            tracerBuilder.withSampler(sampler);
        }
        return tracerBuilder;
    }

    /**
     * Adds a Jaeger based Open Tracing {@link Tracer}.
     *
     * @param tracerBuilder The builder
     * @return The {@link Tracer}
     */
    @Singleton
    @Primary
    @Requires(classes = JaegerTracer.Builder.class)
    Tracer jaegerTracer(JaegerTracer.Builder tracerBuilder) {
        Tracer tracer = tracerBuilder.build();
        if (!GlobalTracer.isRegistered()) {
            GlobalTracer.register(tracer);
        }
        return tracer;
    }

    @Override
    @PreDestroy
    public void close() throws IOException {
        configuration.getConfiguration().closeTracer();
    }

    /**
     * Hooks for sub classes to override.
     *
     * @param configuration The configuration
     */
    @SuppressWarnings("WeakerAccess")
    protected void customizeConfiguration(Configuration configuration) {
        // no-op
    }

    private JaegerTracer.Builder resolveBuilder(Configuration configuration) {
        customizeConfiguration(configuration);
        return configuration.getTracerBuilder();
    }

}
