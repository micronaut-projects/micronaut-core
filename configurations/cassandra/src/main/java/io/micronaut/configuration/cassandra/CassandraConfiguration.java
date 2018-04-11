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

    private String node;
    private Integer port;
    private String clusterName;
    private String username;
    private String password;
    private Integer maxSchemaAgreementWaitSeconds;
    private Boolean withoutJmxReporting = Boolean.FALSE;
    private Boolean withoutMetrics = Boolean.FALSE;
    private Boolean sslEnabled = Boolean.FALSE;

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

    public void setNode(String node) {
        this.node = node;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Integer getMaxSchemaAgreementWaitSeconds() {
        return maxSchemaAgreementWaitSeconds;
    }

    public void setMaxSchemaAgreementWaitSeconds(Integer maxSchemaAgreementWaitSeconds) {
        this.maxSchemaAgreementWaitSeconds = maxSchemaAgreementWaitSeconds;
    }

    public Boolean getWithoutJmxReporting() {
        return withoutJmxReporting;
    }

    public void setWithoutJmxReporting(Boolean withoutJmxReporting) {
        this.withoutJmxReporting = withoutJmxReporting;
    }

    public Boolean getWithoutMetrics() {
        return withoutMetrics;
    }

    public void setWithoutMetrics(Boolean withoutMetrics) {
        this.withoutMetrics = withoutMetrics;
    }

    public Boolean getSslEnabled() {
        return sslEnabled;
    }

    public void setSslEnabled(Boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }
}
