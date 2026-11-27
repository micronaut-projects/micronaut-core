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
package io.micronaut.http.client.loadbalance;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.time.Instant;

/**
 * A snapshot of the outlier detection state of one instance of a service.
 *
 * <p>Outlier detection, when enabled with {@link OutlierDetectionConfiguration}, counts the
 * consecutive failures of each instance a load balancer selects. An instance whose failures
 * reach the configured threshold is <em>ejected</em>: the load balancer stops selecting it
 * for the ejection time, which grows with each ejection, then tries it again. This record tells
 * whether an instance is ejected now, until when, and how close it is to the next ejection, for
 * example for a management endpoint. Obtain it from
 * {@link io.micronaut.http.client.LoadBalancer#getOutlierEjectionStates()}.</p>
 *
 * <p>The active health check, when enabled, is part of the same view: an instance it found
 * unhealthy is {@link #unhealthy()} and left out of the selection like an ejected one.</p>
 *
 * @param uri                     The URI of the instance
 * @param ejected                 Whether the instance is ejected now
 * @param ejectionCount           The ejections since the instance last recovered, which multiply the ejection time
 * @param ejectedUntil            When the current ejection ends, or {@code null} when the instance is not ejected
 * @param consecutiveFailures     The consecutive connect failures, timeouts and resets
 * @param consecutiveServerErrors The consecutive server errors
 * @param unhealthy               Whether the active health check found the instance unhealthy, see
 *                                {@link io.micronaut.http.client.ServiceHttpClientConfiguration#isHealthCheck()}
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public record OutlierEjectionState(
    URI uri,
    boolean ejected,
    int ejectionCount,
    @Nullable Instant ejectedUntil,
    int consecutiveFailures,
    int consecutiveServerErrors,
    boolean unhealthy
) {

    /**
     * The state of an instance that the active health check did not find unhealthy.
     *
     * @param uri                     The URI of the instance
     * @param ejected                 Whether it is ejected now
     * @param ejectionCount           The number of ejections
     * @param ejectedUntil            Until when it is ejected, if it is
     * @param consecutiveFailures     The consecutive transport failures
     * @param consecutiveServerErrors The consecutive server errors
     */
    public OutlierEjectionState(URI uri, boolean ejected, int ejectionCount, @Nullable Instant ejectedUntil,
                                int consecutiveFailures, int consecutiveServerErrors) {
        this(uri, ejected, ejectionCount, ejectedUntil, consecutiveFailures, consecutiveServerErrors, false);
    }

    /**
     * @return Whether the instance is left out of the selection now, ejected by the outlier
     * detection or found unhealthy by the active health check. When every instance is left out,
     * all of them are selected anyway.
     */
    public boolean excluded() {
        return ejected || unhealthy;
    }
}
