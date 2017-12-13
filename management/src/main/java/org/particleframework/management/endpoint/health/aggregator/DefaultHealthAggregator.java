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
package org.particleframework.management.endpoint.health.aggregator;

import org.particleframework.context.annotation.Requires;
import org.particleframework.management.endpoint.health.HealthResult;
import org.particleframework.management.endpoint.health.HealthStatus;
import org.particleframework.management.endpoint.health.indicator.HealthIndicator;
import org.particleframework.management.endpoint.health.indicator.HealthIndicatorSubscriber;

import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/**
 * <p>Default implementation of {@link HealthAggregator} that creates
 * a {status: , description: (optional) , details: } response. The top level object
 * represents the most severe status found in the provided health results,
 * or {@link HealthStatus#UNKNOWN} if none found. All registered indicators
 * have their own {status: , description: (optional , details: } object, keyed by the
 * name of the {@link HealthResult} defined inside of the details of the top
 * level object.
 *
 * Example:
 * [status: "UP, details: [diskSpace: [status: UP, details: [:]], cpuUsage: ...]]</p>
 *
 * @author James Kleeh
 * @since 1.0
 */
@Singleton
@Requires(endpoint = "endpoints.health")
public class DefaultHealthAggregator implements HealthAggregator {

    protected HealthStatus calculateOverallStatus(List<HealthResult> results) {
        return results.stream()
                .map(HealthResult::getStatus)
                .distinct()
                .sorted()
                .reduce((a, b) -> b)
                .orElse(HealthStatus.UNKNOWN);
    }

    protected List<HealthResult> aggregateResults(HealthIndicator[] indicators) {
        List<HealthResult> results = Collections.synchronizedList(new ArrayList<>(indicators.length));
        CountDownLatch latch = new CountDownLatch(indicators.length);
        for (HealthIndicator indicator: indicators) {
            indicator.getResult().subscribe(new HealthIndicatorSubscriber(indicator) {
                @Override
                public void onNext(HealthResult result) {
                    results.add(result);
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
            return results;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    protected Object aggregateDetails(List<HealthResult> results) {
        Map<String, Object> details = new HashMap<>(results.size());
        results.forEach( r -> details.put(r.getName(), buildResult(r.getStatus(), r.getDetails())));
        return details;
    }

    protected Object buildResult(HealthStatus status, Object details) {
        Map<String, Object> healthStatus = new LinkedHashMap<>(3);
        healthStatus.put("status", status.getName());
        status.getDescription().ifPresent(description -> healthStatus.put("description", description));
        healthStatus.put("details", details);
        return healthStatus;
    }

    @Override
    public Object aggregate(HealthIndicator[] indicators) {
        List<HealthResult> results = aggregateResults(indicators);
        HealthStatus overallStatus = calculateOverallStatus(results);

        return buildResult(overallStatus, aggregateDetails(results));
    }
}
