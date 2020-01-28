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
package io.micronaut.management.health.aggregator;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.endpoint.health.HealthLevelOfDetail;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import io.micronaut.runtime.ApplicationConfiguration;
import io.reactivex.Flowable;
import io.reactivex.Single;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>Default implementation of {@link HealthAggregator} that creates a {status: , description: (optional) , details: }
 * response. The top level object represents the most severe status found in the provided health results, or
 * {@link HealthStatus#UNKNOWN} if none found. All registered indicators have their own
 * {status: , description: (optional , details: } object, keyed by the name of the {@link HealthResult} defined inside
 * of the details of the top level object.
 * <p>
 * Example:
 * [status: "UP, details: [diskSpace: [status: UP, details: [:]], cpuUsage: ...]]</p>
 *
 * @author James Kleeh
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Requires(beans = HealthEndpoint.class)
public class RxJavaHealthAggregator implements HealthAggregator<HealthResult> {

    private final ApplicationConfiguration applicationConfiguration;

    /**
     * Default constructor.
     * @param applicationConfiguration The application configuration.
     */
    public RxJavaHealthAggregator(ApplicationConfiguration applicationConfiguration) {
        this.applicationConfiguration = applicationConfiguration;
    }

    @Override
    public Publisher<HealthResult> aggregate(HealthIndicator[] indicators, HealthLevelOfDetail healthLevelOfDetail) {
        Flowable<HealthResult> results = aggregateResults(indicators);
        Single<HealthResult> result = results.toList().map(list -> {
            HealthStatus overallStatus = calculateOverallStatus(list);
            return buildResult(overallStatus, aggregateDetails(list), healthLevelOfDetail);
        });
        return result.toFlowable();
    }

    @Override
    public Publisher<HealthResult> aggregate(String name, Publisher<HealthResult> results) {
        Single<HealthResult> result = Flowable.fromPublisher(results).toList().map((list) -> {
            HealthStatus overallStatus = calculateOverallStatus(list);
            Object details = aggregateDetails(list);
            return HealthResult.builder(name, overallStatus).details(details).build();
        });
        return result.toFlowable();
    }

    /**
     * @param results A list of {@link HealthResult}
     * @return The calculated overall health status
     */
    protected HealthStatus calculateOverallStatus(List<HealthResult> results) {
        return results.stream()
            .map(HealthResult::getStatus)
            .sorted()
            .distinct()
            .reduce((a, b) -> b)
            .orElse(HealthStatus.UNKNOWN);
    }

    /**
     * @param indicators An array of {@link HealthIndicator}
     * @return The aggregated results from all health indicators
     */
    protected Flowable<HealthResult> aggregateResults(HealthIndicator[] indicators) {
        return Flowable.merge(
            Arrays.stream(indicators)
                .map(HealthIndicator::getResult)
                .collect(Collectors.toList())
        );
    }

    /**
     * @param results A list of health results
     * @return The aggregated details for the results
     */
    protected Object aggregateDetails(List<HealthResult> results) {
        Map<String, Object> details = new HashMap<>(results.size());
        results.forEach(r -> details.put(r.getName(), buildResult(r.getStatus(), r.getDetails(), HealthLevelOfDetail.STATUS_DESCRIPTION_DETAILS)));
        return details;
    }

    /**
     * @param status  A {@link HealthStatus}
     * @param details The health status details
     * @param healthLevelOfDetail The {@link HealthLevelOfDetail}
     * @return A {@link Map} with the results from the health status
     */
    @SuppressWarnings("MagicNumber")
    protected HealthResult buildResult(HealthStatus status, Object details, HealthLevelOfDetail healthLevelOfDetail) {
        if (healthLevelOfDetail == HealthLevelOfDetail.STATUS) {
            return HealthResult.builder(null, status).build();
        }

        return HealthResult.builder(
                applicationConfiguration.getName().orElse(Environment.DEFAULT_NAME),
                status
        ).details(details).build();
    }
}
