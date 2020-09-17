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
package io.micronaut.tracing.jaeger;

import io.jaegertracing.Configuration;
import io.jaegertracing.spi.MetricsFactory;
import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;
import io.micronaut.runtime.ApplicationConfiguration;

import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;

import static io.jaegertracing.Configuration.JAEGER_SERVICE_NAME;
import static io.micronaut.tracing.jaeger.JaegerConfiguration.PREFIX;

/**
 * Configuration for Jaeger tracing.
 *
 * @author graemerocher
 * @since 1.0
 */
@ConfigurationProperties(PREFIX)
public class JaegerConfiguration implements Toggleable  {
    /**
     * The configuration prefix.
     */
    public static final String PREFIX = "tracing.jaeger";

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = false;

    @ConfigurationBuilder(prefixes = "with", includes = "tracerTags")
    protected final Configuration configuration;

    private boolean enabled = DEFAULT_ENABLED;
    private boolean expandExceptionLogs;
    private boolean zipkinSharedRpcSpan;

    /**
     * Initialize Jaeger with common application configurations.
     *
     * @param applicationConfiguration The common application configurations
     */
    public JaegerConfiguration(
            ApplicationConfiguration applicationConfiguration) {
        if (StringUtils.isEmpty(System.getProperty(JAEGER_SERVICE_NAME))) {
            System.setProperty(JAEGER_SERVICE_NAME, applicationConfiguration.getName().orElse(Environment.DEFAULT_NAME));
        }
        configuration = Configuration.fromEnv();
    }

    /**
     * @return Whether to expand exception logs
     */
    public boolean isExpandExceptionLogs() {
        return expandExceptionLogs;
    }

    /**
     * Whether to expand exception logs.
     *
     * @param expandExceptionLogs True if they should be expanded
     */
    public void setExpandExceptionLogs(boolean expandExceptionLogs) {
        this.expandExceptionLogs = expandExceptionLogs;
    }

    /**
     * @return Whether to use Zipkin shared RPC
     */
    public boolean isZipkinSharedRpcSpan() {
        return zipkinSharedRpcSpan;
    }

    /**
     * Whether to use Zipkin shared RPC.
     *
     * @param zipkinSharedRpcSpan True if Zipkin shared RPC should be used
     */
    public void setZipkinSharedRpcSpan(boolean zipkinSharedRpcSpan) {
        this.zipkinSharedRpcSpan = zipkinSharedRpcSpan;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enable/Disable Jaeger. Default value ({@value #DEFAULT_ENABLED}).
     *
     * @param enabled A boolean to enable/disabled Jaeger
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return The Jaeger {@link Configuration} object
     */
    public Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Sets the sampler configuration.
     *
     * @param samplerConfiguration The sampler configuration
     */
    @Inject
    public void setSamplerConfiguration(@Nullable Configuration.SamplerConfiguration samplerConfiguration) {
        if (samplerConfiguration != null) {
            configuration.withSampler(samplerConfiguration);
        }
    }

    /**
     * Sets the reporter configuration.
     *
     * @param reporterConfiguration The reporter configuration
     */
    @Inject
    public void setReporterConfiguration(@Nullable Configuration.ReporterConfiguration reporterConfiguration) {
        if (reporterConfiguration != null) {
            configuration.withReporter(reporterConfiguration);
        }
    }

    /**
     * Sets the sampler configuration.
     *
     * @param samplerConfiguration The sampler configuration
     */
    @Inject
    public void setSamplerConfiguration(@Nullable JaegerSamplerConfiguration samplerConfiguration) {
        if (samplerConfiguration != null) {
            configuration.withSampler(samplerConfiguration.configuration);
        }
    }

    /**
     * Sets the reporter configuration.
     *
     * @param reporterConfiguration The reporter configuration
     */
    @Inject
    public void setReporterConfiguration(@Nullable JaegerReporterConfiguration reporterConfiguration) {
        if (reporterConfiguration != null) {
            configuration.withReporter(reporterConfiguration.configuration);
        }
    }

    /**
     * Sets the codec configuration.
     *
     * @param codecConfiguration The codec configuration
     */
    @Inject
    public void setCodecConfiguration(@Nullable Configuration.CodecConfiguration codecConfiguration) {
        if (codecConfiguration != null) {
            configuration.withCodec(codecConfiguration);
        }
    }

    /**
     * Sets the metrics factory to use.
     *
     * @param metricsFactory The metrics factory
     */
    @Inject void setMetricsFactory(@Nullable MetricsFactory metricsFactory) {
        if (metricsFactory != null) {
            configuration.withMetricsFactory(metricsFactory);
        }
    }

    /**
     * The sampler configuration bean.
     */
    @ConfigurationProperties("sampler")
    public static class JaegerSamplerConfiguration {

        @ConfigurationBuilder(prefixes = "with")
        protected Configuration.SamplerConfiguration configuration = Configuration.SamplerConfiguration.fromEnv();

        /**
         * @return The {@link io.jaegertracing.Configuration.SamplerConfiguration}
         */
        public Configuration.SamplerConfiguration getSamplerConfiguration() {
            return configuration;
        }

        /**
         * Sets the sampler probability used by the default {@link brave.sampler.CountingSampler}. A value of 1.0
         * indicates to sample all requests. A value of 0.1 indicates to sample 10% of requests.
         *
         * @param probability The sampler probability
         */
        public void setProbability(float probability) {
            configuration.withParam(probability);
        }
    }

    /**
     * The reporter configuration bean.
     */
    @ConfigurationProperties("reporter")
    public static class JaegerReporterConfiguration {

        @ConfigurationBuilder(prefixes = "with")
        protected Configuration.ReporterConfiguration configuration = Configuration.ReporterConfiguration .fromEnv();

        /**
         * @return The reporter configuration.
         */
        public Configuration.ReporterConfiguration getReporterConfiguration() {
            return configuration;
        }

        /**
         * Sets the sender configuration.
         *
         * @param senderConfiguration The sender configuration
         */
        @Inject
        public void setSenderConfiguration(@Nullable Configuration.SenderConfiguration senderConfiguration) {
            if (senderConfiguration != null) {
                configuration.withSender(senderConfiguration);
            }
        }

        /**
         * Sets the sender configuration.
         *
         * @param senderConfiguration The sender configuration
         */
        @Inject
        public void setSenderConfiguration(@Nullable JaegerSenderConfiguration senderConfiguration) {
            if (senderConfiguration != null) {
                configuration.withSender(senderConfiguration.configuration);
            }
        }
    }

    /**
     * The sender configuration bean.
     */
    @ConfigurationProperties("sender")
    public static class JaegerSenderConfiguration {

        @ConfigurationBuilder(prefixes = "with")
        protected Configuration.SenderConfiguration configuration = Configuration.SenderConfiguration.fromEnv();

        /**
         * @return The sender configuration
         */
        public Configuration.SenderConfiguration getSenderConfiguration() {
            return configuration;
        }
    }
}
