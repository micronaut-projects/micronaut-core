package io.micronaut.configuration.elasticsearch;


/**
 * @author lishuai
 * @since 1.0.1
 */
public interface ElasticsearchSettings {

    /**
     * The prefix to use for all Elasticsearch settings.
     */
    String PREFIX = "elasticsearch";

    /**
     * Default Elasticsearch URI.
     */
    String DEFAULT_URI = "http://127.0.0.1:9200";

}
