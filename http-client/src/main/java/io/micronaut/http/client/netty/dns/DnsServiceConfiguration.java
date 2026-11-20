/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.client.netty.dns;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * The DNS discovery of one service: the instances of the service {@code <id>} are the records of
 * {@code micronaut.discovery.dns.services.<id>.hostname}, a fully qualified name.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@EachProperty(DnsServiceConfiguration.PREFIX)
public class DnsServiceConfiguration {

    /**
     * The prefix of the DNS discovery of the services.
     */
    public static final String PREFIX = "micronaut.discovery.dns.services";

    /**
     * The kind of DNS record that lists the instances.
     */
    public enum RecordType {
        /**
         * IPv4 address records, at the configured port.
         */
        A,
        /**
         * IPv6 address records, at the configured port.
         */
        AAAA,
        /**
         * Both IPv4 and IPv6 address records, at the configured port.
         */
        ANY_ADDRESS,
        /**
         * Service records: the target host, the port and the weight of each instance. Only the
         * records of the lowest priority are used.
         */
        SRV
    }

    private final String serviceId;
    private @Nullable String hostname;
    private RecordType recordType = RecordType.A;
    private String scheme = "http";
    private @Nullable Integer port;
    private Duration minTtl = Duration.ofSeconds(1);
    private Duration maxTtl = Duration.ofMinutes(1);
    private @Nullable Duration refreshInterval;
    private Duration queryTimeout = Duration.ofSeconds(5);
    private Duration maxStale = Duration.ofMinutes(5);

    /**
     * @param serviceId The service id
     */
    public DnsServiceConfiguration(@Parameter String serviceId) {
        this.serviceId = serviceId;
    }

    /**
     * @return The service id
     */
    public String getServiceId() {
        return serviceId;
    }

    /**
     * @return The fully qualified name to look up, required
     */
    public @Nullable String getHostname() {
        return hostname;
    }

    /**
     * @param hostname The fully qualified name to look up, for example {@code my-svc.my-ns.svc.cluster.local}
     */
    public void setHostname(@Nullable String hostname) {
        this.hostname = hostname;
    }

    /**
     * @return The kind of record to look up. Defaults to {@code A}.
     */
    public RecordType getRecordType() {
        return recordType;
    }

    /**
     * @param recordType The kind of record to look up
     */
    public void setRecordType(RecordType recordType) {
        this.recordType = recordType;
    }

    /**
     * @return The scheme of the instance URIs. Defaults to {@code http}.
     */
    public String getScheme() {
        return scheme;
    }

    /**
     * @param scheme The scheme of the instance URIs, {@code http} or {@code https}
     */
    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    /**
     * @return The port of the address records, the default port of the scheme when not set
     */
    public int getPort() {
        if (port != null) {
            return port;
        }
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    /**
     * @param port The port of the instances found by address records. SRV records carry their own.
     */
    public void setPort(@Nullable Integer port) {
        this.port = port;
    }

    /**
     * @return The shortest time between two lookups, whatever the TTL. Defaults to 1 second.
     */
    public Duration getMinTtl() {
        return minTtl;
    }

    /**
     * @param minTtl The shortest time between two lookups
     */
    public void setMinTtl(Duration minTtl) {
        this.minTtl = minTtl;
    }

    /**
     * @return The longest time between two lookups, whatever the TTL. Defaults to 1 minute.
     */
    public Duration getMaxTtl() {
        return maxTtl;
    }

    /**
     * @param maxTtl The longest time between two lookups
     */
    public void setMaxTtl(Duration maxTtl) {
        this.maxTtl = maxTtl;
    }

    /**
     * @return A fixed time between two lookups instead of the TTL of the records, or {@code null}
     */
    public @Nullable Duration getRefreshInterval() {
        return refreshInterval;
    }

    /**
     * @param refreshInterval A fixed time between two lookups instead of the TTL of the records
     */
    public void setRefreshInterval(@Nullable Duration refreshInterval) {
        this.refreshInterval = refreshInterval;
    }

    /**
     * @return The timeout of one DNS query. Defaults to 5 seconds.
     */
    public Duration getQueryTimeout() {
        return queryTimeout;
    }

    /**
     * @param queryTimeout The timeout of one DNS query
     */
    public void setQueryTimeout(Duration queryTimeout) {
        this.queryTimeout = queryTimeout;
    }

    /**
     * @return How long the instances of the last successful lookup stay in use while the lookups
     * fail. Defaults to 5 minutes.
     */
    public Duration getMaxStale() {
        return maxStale;
    }

    /**
     * @param maxStale How long the instances of the last successful lookup stay in use while the
     *                 lookups fail
     */
    public void setMaxStale(Duration maxStale) {
        this.maxStale = maxStale;
    }
}
