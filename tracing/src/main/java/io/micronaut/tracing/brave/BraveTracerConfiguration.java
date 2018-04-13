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

import brave.Clock;
import brave.ErrorParser;
import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.Propagation;
import brave.sampler.Sampler;
import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.Toggleable;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.LoadBalancerResolver;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.tracing.brave.sender.HttpClientSender;

import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * A Configuration properties for Brave
 *
 * @author graemerocher
 * @since 1.0
 */
@Requires(classes = { Tracing.class})
@Requires(property = BraveTracerConfiguration.PREFIX + ".enabled", value = "true")
@ConfigurationProperties(BraveTracerConfiguration.PREFIX)
public class BraveTracerConfiguration implements Toggleable {

    public static final String PREFIX = "tracing.zipkin";

    @ConfigurationBuilder(prefixes = "", excludes = {"errorParser","clock","endpoint", "spanReporter", "propagationFactory", "currentTraceContext", "sampler"})
    protected Tracing.Builder tracingBuilder = Tracing.newBuilder();


    private boolean enabled = false;

    /**
     * Constructs a new {@link BraveTracerConfiguration}
     *
     * @param configuration The application configuration
     */
    public BraveTracerConfiguration(ApplicationConfiguration configuration) {
        if(configuration != null) {
            tracingBuilder.localServiceName(configuration.getName().orElse(Environment.DEFAULT_NAME));
        }
        else {
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
     * @param enabled True if tracing is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
        if(sampler != null) {
            tracingBuilder.sampler(sampler);
        }
    }

    /**
     * @param errorParser The {@link ErrorParser} to use
     */
    @Inject
    public void setErrorParser(@Nullable ErrorParser errorParser) {
        if(errorParser != null) {
            tracingBuilder.errorParser(errorParser);
        }
    }
    /**
     * @param propagationFactory The {@link Propagation.Factory} to use
     */
    @Inject
    public void setPropagationFactory(@Nullable Propagation.Factory propagationFactory) {
        if(propagationFactory != null) {
            tracingBuilder.propagationFactory(propagationFactory);
        }
    }

    /**
     * @param clock The {@link Clock} to use
     */
    @Inject
    public void setClock(@Nullable Clock clock) {
        if(clock != null) {
            tracingBuilder.clock(clock);
        }
    }

    /**
     * Sets the current trace context
     *
     * @param traceContext The trace context
     */
    @Inject
    public void setCurrentTraceContext(CurrentTraceContext traceContext) {
        if(traceContext != null) {
            tracingBuilder.currentTraceContext(traceContext);
        }
    }


    @ConfigurationProperties(HttpClientSenderConfiguration.PREFIX)
    @Requires(property = HttpClientSenderConfiguration.PREFIX)
    @Requires(classes = { Tracing.class})
    public static class HttpClientSenderConfiguration extends HttpClientConfiguration {
        public static final String PREFIX = BraveTracerConfiguration.PREFIX + ".http";
        @ConfigurationBuilder(prefixes = "")
        protected final HttpClientSender.Builder clientSenderBuilder;

        public HttpClientSenderConfiguration() {
            this.clientSenderBuilder = new HttpClientSender.Builder(this);
        }

        public HttpClientSender.Builder getBuilder() {
            return clientSenderBuilder;
        }
    }
}
