/*
 * Copyright 2017 original authors
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
package io.micronaut.configuration.cassandra;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;

/**
 * Allows the configuration of a Cassandra Cluster connection using the datastax driver.
 *
 * The client is able to be configured to multiple clusters.
 *
 * @author Nirav Assar
 * @since 1.0
 */
@EachProperty(value = "cassandra")
public class CassandraConfiguration {

    private String clusterName;
    private String node;
    private Integer port;

    public CassandraConfiguration(@Parameter String clusterName) {
        this.setClusterName(clusterName);
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String name) {
        this.clusterName = name;
    }

    public String getNode() {
        return node;
    }

    public void setNode(String node) {
        this.node = node;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }
}
