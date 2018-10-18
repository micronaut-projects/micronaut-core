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

package io.micronaut.configuration.elasticsearch6;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import org.apache.http.HttpHost;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import static io.micronaut.configuration.elasticsearch6.ElasticsearchSettings.DEFAULT_URI;

/**
 * Configuration for Elasticsearch6 RestHighLevelClient.
 *
 * @author lishuai
 * @since 1.0.1
 */
@Requires(property = ElasticsearchSettings.PREFIX)
@ConfigurationProperties(ElasticsearchSettings.PREFIX)
public class DefaultElasticsearchConfiguration {

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
    }

    /**
     * Convert {@link URI} to {@link HttpHost}.
     * @return HttpHost Array
     */
    HttpHost[] toHttpHosts() {

        return uris.stream()
                .map(host -> new HttpHost(host.getHost(), host.getPort(), host.getScheme())).distinct().toArray(HttpHost[]::new);
    }

    @Override
    public String toString() {
        return "DefaultElasticsearchConfiguration{" +
                ", hosts=" + uris +
                '}';
    }
}
