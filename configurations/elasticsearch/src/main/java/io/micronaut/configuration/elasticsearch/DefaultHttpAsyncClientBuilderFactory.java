/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.configuration.elasticsearch;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;

import javax.inject.Singleton;

/**
 * The default factory used to create {@link HttpAsyncClientBuilder} for HTTP client configurations.
 *
 * @author puneetbehl
 * @since 1.1.0
 */
@Requires(classes = {RestHighLevelClient.class})
@Factory
public class DefaultHttpAsyncClientBuilderFactory {

    /**
     * The http client configuration (e.g. encrypted communication over ssl, or anything that
     * the {@link HttpAsyncClientBuilder} allows to set).
     *
     * @return The {@link HttpAsyncClientBuilder} bean with default configurations.
     */
    @Bean
    @Singleton
    HttpAsyncClientBuilder httpAsyncClientBuilder() {
        return HttpAsyncClientBuilder.create();
    }
}
