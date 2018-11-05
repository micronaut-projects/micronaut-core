/**
 * Configuration for Elasticsearch6 RestHighLevelClient.
 * refer to https://www.elastic.co/guide/en/elasticsearch/client/java-rest/6.3/java-rest-high.html
 *
 * @author lishuai
 * @since 1.0.1
 */
@Configuration
@Requires(beans = RestHighLevelClient.class)
@Requires(property = ElasticsearchSettings.PREFIX)
package io.micronaut.configuration.elasticsearch6;

import io.micronaut.context.annotation.Configuration;
import io.micronaut.context.annotation.Requires;
import org.elasticsearch.client.RestHighLevelClient;
