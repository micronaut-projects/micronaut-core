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
package org.particleframework.function.client.aws;

import com.amazonaws.auth.*;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.handlers.RequestHandler2;
import com.amazonaws.metrics.RequestMetricCollector;
import com.amazonaws.services.lambda.AWSLambdaAsyncClient;
import com.amazonaws.services.lambda.AWSLambdaAsyncClientBuilder;
import org.particleframework.context.annotation.ConfigurationBuilder;
import org.particleframework.context.annotation.ConfigurationProperties;
import org.particleframework.context.env.Environment;
import org.particleframework.core.util.ArrayUtils;

import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * @author graemerocher
 * @since 1.0
 */
@ConfigurationProperties(AWSLambdaConfiguration.PREFIX)
public class AWSLambdaConfiguration {
    public static final String PREFIX = AWSConfiguration.PREFIX + ".lambda";

    private final AWSClientConfiguration clientConfiguration;

    @ConfigurationBuilder(prefixes = "with")
    AWSLambdaAsyncClientBuilder builder = AWSLambdaAsyncClient.asyncBuilder();

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
        this.builder.setClientConfiguration(clientConfiguration.clientConfiguration);
        return builder;
    }

    @Inject
    public void setMetricsCollector( @Nullable RequestMetricCollector metricsCollector) {
        if(metricsCollector != null) {
            builder.setMetricsCollector(metricsCollector);
        }
    }

    @Inject
    public void setEndpointConfiguration( @Nullable AwsClientBuilder.EndpointConfiguration endpointConfiguration) {
        if(endpointConfiguration != null) {
            builder.setEndpointConfiguration(endpointConfiguration);
        }
    }

    @Inject
    public void setRequestHandlers(@Nullable RequestHandler2...handlers) {
        if(ArrayUtils.isNotEmpty(handlers)) {
            builder.setRequestHandlers(handlers);
        }
    }
}
