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

package io.micronaut.configuration.kafka.health;

import io.micronaut.configuration.kafka.config.KafkaDefaultConfiguration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import io.reactivex.Flowable;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.config.ConfigResource;

import javax.inject.Singleton;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * A {@link HealthIndicator} for Kafka.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Requires(beans = AdminClient.class)
public class KafkaHealthIndicator implements HealthIndicator {

    private static final String ID = "kafka";
    private static final String REPLICATION_PROPERTY = "transaction.state.log.replication.factor";
    private final AdminClient adminClient;
    private final KafkaDefaultConfiguration defaultConfiguration;

    /**
     * Constructs a new Kafka health indicator for the given arguments.
     *
     * @param adminClient          The admin client
     * @param defaultConfiguration The default configuration
     */
    public KafkaHealthIndicator(AdminClient adminClient, KafkaDefaultConfiguration defaultConfiguration) {
        this.adminClient = adminClient;
        this.defaultConfiguration = defaultConfiguration;
    }

    @Override
    public Flowable<HealthResult> getResult() {
        DescribeClusterResult result = adminClient.describeCluster(
                new DescribeClusterOptions().timeoutMs(
                        (int) defaultConfiguration.getHealthTimeout().toMillis()
                )
        );

        Flowable<String> clusterId = Flowable.fromFuture(result.clusterId());
        Flowable<Collection<Node>> nodes = Flowable.fromFuture(result.nodes());
        Flowable<Node> controller = Flowable.fromFuture(result.controller());

        return controller.switchMap(node -> {
            String brokerId = node.idString();
            ConfigResource configResource = new ConfigResource(ConfigResource.Type.BROKER, brokerId);
            DescribeConfigsResult configResult = adminClient.describeConfigs(Collections.singletonList(configResource));
            Flowable<Map<ConfigResource, Config>> configs = Flowable.fromFuture(configResult.all());
            return configs.switchMap(resources -> {
                Config config = resources.get(configResource);
                ConfigEntry ce = config.get(REPLICATION_PROPERTY);
                int replicationFactor = Integer.parseInt(ce.value());
                return nodes.switchMap(nodeList -> clusterId.map(clusterIdString -> {
                    int nodeCount = nodeList.size();
                    HealthResult.Builder builder;
                    if (nodeCount >= replicationFactor) {
                        builder = HealthResult.builder(ID, HealthStatus.UP);
                    } else {
                        builder = HealthResult.builder(ID, HealthStatus.DOWN);
                    }
                    return builder
                            .details(CollectionUtils.mapOf(
                                    "brokerId", brokerId,
                                    "clusterId", clusterIdString,
                                    "nodes", nodeCount
                            )).build();
                }));
            });
        }).onErrorReturn(throwable ->
                HealthResult.builder(ID, HealthStatus.DOWN)
                            .exception(throwable).build()
        );
    }

}
