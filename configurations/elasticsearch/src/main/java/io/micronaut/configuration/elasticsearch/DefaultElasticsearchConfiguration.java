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

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import static io.micronaut.configuration.elasticsearch.ElasticsearchSettings.DEFAULT_URI;

/**
 * Configuration for Elasticsearch RestHighLevelClient.
 *
 * @author lishuai
 * @since 1.0.1
 */
@Requires(classes = RestClientBuilder.class)
@ConfigurationProperties(ElasticsearchSettings.PREFIX)
public class DefaultElasticsearchConfiguration {

    protected HttpHost[] httpHosts;
    protected Header[] defaultHeaders;

    protected int connectTimeout;
    protected int socketTimeout;

    private List<URI> uris = Collections.singletonList(URI.create(DEFAULT_URI));

    /**
     * @return The elasticsearch URIs
     */
    public List<URI> getUris() {
        return uris;
    }

    /**
     * Set the elasticsearch URIs.
     *
     * @param uris The list of URIs
     */
    public void setUris(List<URI> uris) {
        this.uris = uris;
        this.httpHosts = toHttpHosts();
    }

    /**
     *
     * @param connectTimeout The connection timeout.
     */
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    /**
     *
     * @param socketTimeout The socket timeout.
     */
    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    /**
     * Convert {@link URI} to {@link HttpHost}.
     * @return HttpHost Array
     */
    HttpHost[] toHttpHosts() {
        return uris.stream()
                .map(host -> new HttpHost(host.getHost(), host.getPort(), host.getScheme())).distinct().toArray(HttpHost[]::new);
    }


    /**
     * @return RestClientBuilder
     */
    @ConfigurationBuilder
    public RestClientBuilder builder() {
        return RestClient.builder(httpHosts)
                .setRequestConfigCallback(
                        requestConfigBuilder -> requestConfigBuilder
                                .setConnectTimeout(connectTimeout)
                                .setSocketTimeout(socketTimeout));
    }

    @Override
    public String toString() {
        return "DefaultElasticsearchConfiguration{" +
                ", hosts=" + uris +
                '}';
    }
}
