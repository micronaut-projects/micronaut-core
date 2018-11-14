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
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;

/**
 * The default Factory for creating Elasticsearch client.
 *
 * @author lishuai
 * @author Puneet Behl
 * @since 1.1.0
 */
@Requires(beans = DefaultElasticsearchConfigurationProperties.class)
@Factory
public class DefaultElasticsearchClientFactory {

    /**
     * Create the {@link RestHighLevelClient} bean for the given configuration.
     *
     * @param elasticsearchConfiguration The {@link DefaultElasticsearchConfigurationProperties} object
     * @return A {@link RestHighLevelClient} bean
     */
    @Bean(preDestroy = "close")
    RestHighLevelClient restHighLevelClient(DefaultElasticsearchConfigurationProperties elasticsearchConfiguration) {
        return new RestHighLevelClient(restClientBuilder(elasticsearchConfiguration));
    }

    /**
     * @param elasticsearchConfiguration The {@link DefaultElasticsearchConfigurationProperties} object
     * @return The Elasticsearch Rest Client
     */
    @Bean(preDestroy = "close")
    RestClient restClient(DefaultElasticsearchConfigurationProperties elasticsearchConfiguration) {
        return restClientBuilder(elasticsearchConfiguration).build();
    }

    /**
     * @param elasticsearchConfiguration The {@link DefaultElasticsearchConfigurationProperties} object
     * @return The {@link RestClientBuilder}
     */
    protected RestClientBuilder restClientBuilder(DefaultElasticsearchConfigurationProperties elasticsearchConfiguration) {
        return RestClient.builder(elasticsearchConfiguration.getHttpHosts())
                .setRequestConfigCallback(requestConfigBuilder -> {
                    requestConfigBuilder = elasticsearchConfiguration.requestConfigBuilder;
                    return requestConfigBuilder;
                })
                .setHttpClientConfigCallback(httpClientBuilder -> {
                    httpClientBuilder = elasticsearchConfiguration.httpAsyncClientBuilder;
                    return httpClientBuilder;
                });
    }

}
