/*
 * Copyright 2017-2019 original authors
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

import com.amazonaws.services.lambda.AWSLambdaAsync;
import com.amazonaws.services.lambda.AWSLambdaAsyncClientBuilder;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;

/**
 * @author graemerocher
 * @since 1.0
 */
@Factory
@Requires(beans = AWSLambdaConfiguration.class)
public class AWSLambdaAsyncClientFactory {

    private final AWSLambdaConfiguration configuration;

    /**
     * Constructor.
     * @param configuration configuration from properties
     */
    public AWSLambdaAsyncClientFactory(AWSLambdaConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * The client returned from a builder.
     * @return client object
     */
    @Requires(beans = AWSLambdaConfiguration.class)
    AWSLambdaAsync awsLambdaAsyncClient() {
        AWSLambdaAsyncClientBuilder builder = configuration.getBuilder();
        return builder.build();
    }
}
