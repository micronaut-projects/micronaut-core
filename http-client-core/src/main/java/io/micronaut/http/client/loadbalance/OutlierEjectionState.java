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
 * A snapshot of the outlier detection state of one instance of a service, see
 * {@link OutlierDetectionConfiguration} and {@link io.micronaut.http.client.LoadBalancer#getOutlierEjectionStates()}.
 *
 * @param uri                     The URI of the instance
 * @param ejected                 Whether the instance is ejected now
 * @param ejectionCount           The ejections since the instance last recovered, which multiply the ejection time
 * @param ejectedUntil            When the current ejection ends, or {@code null} when the instance is not ejected
 * @param consecutiveFailures     The consecutive connect failures, timeouts and resets
 * @param consecutiveServerErrors The consecutive server errors
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
    int consecutiveServerErrors
) {
}
