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

package io.micronaut.configuration.elasticsearch.health;

import io.micronaut.context.annotation.Requires;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import io.reactivex.Flowable;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.io.IOException;

import static java.util.Collections.emptyMap;

/**
 * A  {@link HealthIndicator} for Elasticsearch High Level REST client.
 *
 * @author puneetbehl
 * @since 1.1.0
 */
@Requires(beans = HealthEndpoint.class)
@Requires(property = HealthEndpoint.PREFIX + ".elasticsearch.rest.high.level.enabled", notEquals = "false")
@Singleton
public class ElasticsearchHealthIndicator implements HealthIndicator {

    public static final String NAME = "elasticsearch-rest-high-level";
    private final RestHighLevelClient restHighLevelClient;

    /**
     * Constructor.
     *
     * @param restHighLevelClient The Elasticsearch high level REST client.
     */
    public ElasticsearchHealthIndicator(RestHighLevelClient restHighLevelClient) {
        this.restHighLevelClient = restHighLevelClient;
    }

    @Override
    public Publisher<HealthResult> getResult() {
        HealthResult result = HealthResult.builder(NAME)
                .status(HealthStatus.UNKNOWN)
                .build();
        try {
            ClusterHealthRequest request = new ClusterHealthRequest();
            ClusterHealthResponse response = restHighLevelClient.cluster().health(request, RequestOptions.DEFAULT);
            HealthResult.Builder builder = HealthResult.builder(NAME);
            ClusterHealthStatus clusterHealthStatus = response.getStatus();
            if (clusterHealthStatus == ClusterHealthStatus.GREEN) {
                XContentBuilder xContentBuilder = XContentFactory.jsonBuilder();
                response.toXContent(xContentBuilder, new ToXContent.MapParams(emptyMap()));
                String details = Strings.toString(xContentBuilder);
                builder.status(HealthStatus.UP)
                        .details(details);
                result = builder.build();
            }
        } catch (IOException e) {
            result = buildErrorResult(e);
        }
        return Flowable.just(result);
    }

    private HealthResult buildErrorResult(Throwable throwable) {
        return HealthResult.builder(NAME, HealthStatus.DOWN).exception(throwable).build();
    }
}
