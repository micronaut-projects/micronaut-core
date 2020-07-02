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

import brave.Clock;
import brave.ErrorParser;
import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.Propagation;
import brave.sampler.CountingSampler;
import brave.sampler.Sampler;
import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.tracing.brave.sender.HttpClientSender;

import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;

/**
 * A Configuration properties for Brave.
 *
 * @author graemerocher
 * @since 1.0
 */
@Requires(classes = {Tracing.class})
@Requires(property = BraveTracerConfiguration.PREFIX + ".enabled", value = StringUtils.TRUE)
@ConfigurationProperties(BraveTracerConfiguration.PREFIX)
public class BraveTracerConfiguration implements Toggleable {

    public static final String PREFIX = "tracing.zipkin";
    public static final float DEFAULT_SAMPLER_PROBABILITY = 0.1f;

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = false;

    @ConfigurationBuilder(prefixes = "", excludes = {"errorParser", "clock", "endpoint", "spanReporter", "propagationFactory", "currentTraceContext", "sampler"})
    protected Tracing.Builder tracingBuilder = Tracing.newBuilder();

    private boolean enabled = DEFAULT_ENABLED;
    private float samplerProbability = DEFAULT_SAMPLER_PROBABILITY;

    /**
     * Constructs a new {@link BraveTracerConfiguration}.
     *
     * @param configuration The application configuration
     */
    public BraveTracerConfiguration(ApplicationConfiguration configuration) {
        tracingBuilder.sampler(CountingSampler.create(samplerProbability));
        if (configuration != null) {
            tracingBuilder.localServiceName(configuration.getName().orElse(Environment.DEFAULT_NAME));
        } else {
            tracingBuilder.localServiceName(Environment.DEFAULT_NAME);
        }
    }

    /**
     * @return Is tracing enabled
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Default value ({@value #DEFAULT_ENABLED}).
     * @param enabled True if tracing is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @param samplerConfiguration The sampler configuration
     */
    @Inject
    public void setSamplerConfiguration(@Nullable SamplerConfiguration samplerConfiguration) {
        if (samplerConfiguration != null) {
            tracingBuilder.sampler(CountingSampler.create(samplerConfiguration.getProbability()));
        }
    }

    /**
     * @return The {@link Tracing} builder
     */
    public Tracing.Builder getTracingBuilder() {
        return tracingBuilder;
    }

    /**
     * @param sampler The {@link Sampler} to use
     */
    @Inject
    public void setSampler(@Nullable Sampler sampler) {
        if (sampler != null) {
            tracingBuilder.sampler(sampler);
        }
    }

    /**
     * @param errorParser The {@link ErrorParser} to use
     */
    @Inject
    public void setErrorParser(@Nullable ErrorParser errorParser) {
        if (errorParser != null) {
            tracingBuilder.errorParser(errorParser);
        }
    }

    /**
     * @param propagationFactory The {@link brave.propagation.Propagation.Factory} to use
     */
    @Inject
    public void setPropagationFactory(@Nullable Propagation.Factory propagationFactory) {
        if (propagationFactory != null) {
            tracingBuilder.propagationFactory(propagationFactory);
        }
    }

    /**
     * @param clock The {@link Clock} to use
     */
    @Inject
    public void setClock(@Nullable Clock clock) {
        if (clock != null) {
            tracingBuilder.clock(clock);
        }
    }

    /**
     * Sets the current trace context.
     *
     * @param traceContext The trace context
     */
    @Inject
    public void setCurrentTraceContext(CurrentTraceContext traceContext) {
        if (traceContext != null) {
            tracingBuilder.currentTraceContext(traceContext);
        }
    }

    /**
     * Used to configure HTTP trace sending under the {@code tracing.zipkin.http} namespace.
     */
    @ConfigurationProperties("http")
    @Requires(property = HttpClientSenderConfiguration.PREFIX)
    @Requires(classes = {Tracing.class})
    public static class HttpClientSenderConfiguration extends HttpClientConfiguration {
        public static final String PREFIX = BraveTracerConfiguration.PREFIX + ".http";
        @ConfigurationBuilder(prefixes = "")
        protected final HttpClientSender.Builder clientSenderBuilder;

        /**
         * Initialize the builder with client configurations.
         */
        public HttpClientSenderConfiguration() {
            this.clientSenderBuilder = new HttpClientSender.Builder(this);
        }

        @Override
        public ConnectionPoolConfiguration getConnectionPoolConfiguration() {
            return new ConnectionPoolConfiguration();
        }

        /**
         * Creates builder.
         *
         * @return The builder to construct the {@link HttpClientSender}
         */
        public HttpClientSender.Builder getBuilder() {
            return clientSenderBuilder;
        }
    }

    /**
     * The sampler configuration.
     */
    @ConfigurationProperties("sampler")
    @Requires(classes = CountingSampler.class)
    @Requires(missingBeans = Sampler.class)
    public static class SamplerConfiguration {
        private float probability = DEFAULT_SAMPLER_PROBABILITY;

        /**
         * Get sampler probability. A value of 1.0
         * indicates to sample all requests. A value of 0.1 indicates to sample 10% of requests.
         *
         * @return probability
         */
        public float getProbability() {
            return probability;
        }

        /**
         * Sets the sampler probability used by the default {@link brave.sampler.CountingSampler}. A value of 1.0
         * indicates to sample all requests. A value of 0.1 indicates to sample 10% of requests.
         *
         * @param probability The probability
         */
        public void setProbability(float probability) {
            this.probability = probability;
        }
    }
}
