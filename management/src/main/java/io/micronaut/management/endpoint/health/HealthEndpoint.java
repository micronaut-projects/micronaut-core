/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.management.endpoint.health;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.health.HealthStatus;
import io.micronaut.http.HttpStatus;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.EndpointConfiguration;
import io.micronaut.management.endpoint.annotation.Read;
import io.micronaut.management.health.aggregator.HealthAggregator;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import io.reactivex.Single;

import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;
import java.security.Principal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * <p>Exposes an {@link Endpoint} to provide information about the health of the application.</p>
 *
 * @author James Kleeh
 * @since 1.0
 */
@Endpoint(value = HealthEndpoint.NAME, defaultSensitive = HealthEndpoint.DEFAULT_SENSITIVE)
public class HealthEndpoint {

    /**
     * If the endpoint is sensitive if no configuration is provided.
     */
    public static final boolean DEFAULT_SENSITIVE = false;

    /**
     * Constant for health.
     */
    public static final String NAME = "health";

    /**
     * Prefix for health endpoint.
     */
    public static final String PREFIX = EndpointConfiguration.PREFIX + "." + NAME;

    private HealthAggregator<HealthResult> healthAggregator;
    private HealthIndicator[] healthIndicators;
    private DetailsVisibility detailsVisible = DetailsVisibility.AUTHENTICATED;
    private StatusConfiguration statusConfiguration;

    /**
     * @param healthAggregator            The {@link HealthAggregator}
     * @param healthIndicators            The {@link HealthIndicator}
     */
    public HealthEndpoint(HealthAggregator<HealthResult> healthAggregator,
                          HealthIndicator[] healthIndicators) {
        this.healthAggregator = healthAggregator;
        this.healthIndicators = healthIndicators;
    }

    /**
     * @param principal Authenticated user
     * @return The health information as a {@link Single}
     */
    @Read
    public Single<HealthResult> getHealth(@Nullable Principal principal) {
        HealthLevelOfDetail detail = levelOfDetail(principal);
        return Single.fromPublisher(
                healthAggregator.aggregate(healthIndicators, detail)
        );
    }

    /**
     * @return The visibility policy for health information.
     */
    public DetailsVisibility getDetailsVisible() {
        return detailsVisible;
    }

    /**
     * Sets the visibility policy for health information.
     * @param detailsVisible The {@link DetailsVisibility}
     */
    public void setDetailsVisible(DetailsVisibility detailsVisible) {
        this.detailsVisible = detailsVisible;
    }

    /**
     * @return The status configuration
     */
    public StatusConfiguration getStatusConfiguration() {
        return statusConfiguration;
    }

    /**
     * Sets the status configuration.
     *
     * @param statusConfiguration The status configuration
     */
    @Inject
    public void setStatusConfiguration(StatusConfiguration statusConfiguration) {
        if (statusConfiguration != null) {
            this.statusConfiguration = statusConfiguration;
        }
    }

    /**
     * Returns the level of detail that should be returned by the endpoint.
     *
     * @param principal Authenticated user
     * @return The {@link HealthLevelOfDetail}
     */
    protected HealthLevelOfDetail levelOfDetail(@Nullable Principal principal) {
        boolean showDetails = false;
        switch (detailsVisible) {
            case AUTHENTICATED:
                showDetails = principal != null;
                break;
            case ANONYMOUS:
                showDetails = true;
            default:
                // no-op
        }
        if (showDetails) {
            return HealthLevelOfDetail.STATUS_DESCRIPTION_DETAILS;
        } else {
            return HealthLevelOfDetail.STATUS;
        }
    }

    /**
     * Configuration related to handling of the {@link io.micronaut.health.HealthStatus}.
     *
     * @author graemerocher
     * @since 1.0
     */
    @ConfigurationProperties("status")
    public static class StatusConfiguration {
        private Map<String, HttpStatus> httpMapping = new HashMap<>();

        /**
         * Default constructor.
         */
        public StatusConfiguration() {
            httpMapping.put(HealthStatus.NAME_DOWN, HttpStatus.SERVICE_UNAVAILABLE);
            httpMapping.put(HealthStatus.NAME_UP, HttpStatus.OK);
            httpMapping.put(HealthStatus.UNKNOWN.getName(), HttpStatus.OK);
        }

        /**
         * @return How {@link io.micronaut.health.HealthStatus} map to {@link io.micronaut.http.HttpStatus} codes.
         */
        public Map<String, HttpStatus> getHttpMapping() {
            return httpMapping;
        }

        /**
         * Set how {@link io.micronaut.health.HealthStatus} map to {@link io.micronaut.http.HttpStatus} codes.
         *
         * @param httpMapping The http mappings
         */
        public void setHttpMapping(Map<String, HttpStatus> httpMapping) {
            if (httpMapping != null) {
                for (Map.Entry<String, HttpStatus> entry : httpMapping.entrySet()) {
                    this.httpMapping.put(
                            entry.getKey().toUpperCase(Locale.ENGLISH),
                            entry.getValue()
                    );
                }
            }
        }
    }
}
