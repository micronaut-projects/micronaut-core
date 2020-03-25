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
package io.micronaut.function.client.aws;

import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.EC2ContainerCredentialsProviderWrapper;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.SystemPropertiesCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.handlers.RequestHandler2;
import com.amazonaws.metrics.RequestMetricCollector;
import com.amazonaws.services.lambda.AWSLambdaAsyncClient;
import com.amazonaws.services.lambda.AWSLambdaAsyncClientBuilder;
import io.micronaut.configuration.aws.AWSClientConfiguration;
import io.micronaut.configuration.aws.AWSConfiguration;
import io.micronaut.configuration.aws.EnvironmentAWSCredentialsProvider;
import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.ArrayUtils;

import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;

/**
 * Configuration options for AWS Lambda.
 *
 * @author graemerocher
 * @since 1.0
 */
@ConfigurationProperties(AWSLambdaConfiguration.PREFIX)
@Requires(classes = AWSLambdaAsyncClientBuilder.class)
@Requires(property = AWSLambdaConfiguration.PREFIX)
public class AWSLambdaConfiguration {

    /**
     * Prefix for AWS Lambda settings.
     */
    public static final String PREFIX = AWSConfiguration.PREFIX + ".lambda";

    @ConfigurationBuilder(prefixes = "with")
    AWSLambdaAsyncClientBuilder builder = AWSLambdaAsyncClient.asyncBuilder();

    private final AWSClientConfiguration clientConfiguration;

    /**
     * Constructor.
     * @param clientConfiguration clientConfiguration
     * @param environment environment
     */
    public AWSLambdaConfiguration(AWSClientConfiguration clientConfiguration, Environment environment) {
        this.clientConfiguration = clientConfiguration;

        this.builder.setCredentials(new AWSCredentialsProviderChain(
            new EnvironmentAWSCredentialsProvider(environment),
            new EnvironmentVariableCredentialsProvider(),
            new SystemPropertiesCredentialsProvider(),
            new ProfileCredentialsProvider(),
            new EC2ContainerCredentialsProviderWrapper()
        ));
    }

    /**
     * @return The builder for the {@link com.amazonaws.services.lambda.AWSLambdaAsync} instance
     */
    public AWSLambdaAsyncClientBuilder getBuilder() {
        this.builder.setClientConfiguration(clientConfiguration.getClientConfiguration());
        return builder;
    }

    /**
     * @param metricsCollector The {@link RequestMetricCollector}
     */
    @Inject
    public void setMetricsCollector(@Nullable RequestMetricCollector metricsCollector) {
        if (metricsCollector != null) {
            builder.setMetricsCollector(metricsCollector);
        }
    }

    /**
     * @param endpointConfiguration The {@link AwsClientBuilder#getEndpoint()}
     */
    @Inject
    public void setEndpointConfiguration(@Nullable AwsClientBuilder.EndpointConfiguration endpointConfiguration) {
        if (endpointConfiguration != null) {
            builder.setEndpointConfiguration(endpointConfiguration);
        }
    }

    /**
     * @param handlers The {@link RequestHandler2}
     */
    @Inject
    public void setRequestHandlers(@Nullable RequestHandler2... handlers) {
        if (ArrayUtils.isNotEmpty(handlers)) {
            builder.setRequestHandlers(handlers);
        }
    }
}
