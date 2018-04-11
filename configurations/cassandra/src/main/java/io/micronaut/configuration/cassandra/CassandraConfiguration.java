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

    private String beanName;

    protected String node;
    protected Integer port;
    protected String clusterName;
    protected String username;
    protected String password;
    protected Integer maxSchemaAgreementWaitSeconds;
    protected Boolean withoutJmxReporting = Boolean.FALSE;
    protected Boolean withoutMetrics = Boolean.FALSE;
    protected Boolean sslEnabled = Boolean.FALSE;

    public CassandraConfiguration(@Parameter String beanName) {
        this.setBeanName(beanName);
    }

    public String getBeanName() {
        return beanName;
    }

    public void setBeanName(String name) {
        this.beanName = name;
    }

    public String getNode() {
        return node;
    }

    public Integer getPort() {
        return port;
    }

    public String getClusterName() {
        return clusterName;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public Integer getMaxSchemaAgreementWaitSeconds() {
        return maxSchemaAgreementWaitSeconds;
    }

    public Boolean getWithoutJmxReporting() {
        return withoutJmxReporting;
    }

    public Boolean getWithoutMetrics() {
        return withoutMetrics;
    }

    public Boolean getSslEnabled() {
        return sslEnabled;
    }
}
