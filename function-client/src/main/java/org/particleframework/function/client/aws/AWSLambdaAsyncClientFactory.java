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

import com.amazonaws.services.lambda.AWSLambdaAsync;
import com.amazonaws.services.lambda.AWSLambdaAsyncClientBuilder;
import org.particleframework.context.annotation.Bean;
import org.particleframework.context.annotation.Factory;
import org.particleframework.runtime.context.scope.Refreshable;

import javax.inject.Singleton;

/**
 * @author graemerocher
 * @since 1.0
 */
@Factory
public class AWSLambdaAsyncClientFactory {

    private final AWSLambdaConfiguration configuration;

    public AWSLambdaAsyncClientFactory(AWSLambdaConfiguration configuration) {
        this.configuration = configuration;
    }

    @Bean
    @Refreshable
    AWSLambdaAsync awsLambdaAsyncClient() {
        AWSLambdaAsyncClientBuilder builder = configuration.getBuilder();
        return builder.build();
    }

}
