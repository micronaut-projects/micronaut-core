/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.management.endpoint.health.filter;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.health.HealthStatus;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.OncePerRequestHttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.management.endpoint.EndpointDefaultConfiguration;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.indicator.HealthResult;
import org.reactivestreams.Publisher;

/**
 * A filter that matches the {@link io.micronaut.management.endpoint.health.HealthEndpoint}
 * and returns an appropriate HTTP status code.
 *
 * @author graemerocher
 * @since 1.0
 *
 */
@Filter({
    HealthResultFilter.DEFAULT_MAPPING,
    HealthResultFilter.LIVENESS_PROBE_MAPPING,
    HealthResultFilter.READINESS_PROBE_MAPPING
})
@Requires(beans = HealthEndpoint.class)
public class HealthResultFilter extends OncePerRequestHttpServerFilter {

    /**
     * Configurable default mapping for filter.
     */
    public static final String DEFAULT_MAPPING =
            "${" + EndpointDefaultConfiguration.PREFIX + ".path:" +
                    EndpointDefaultConfiguration.DEFAULT_ENDPOINT_BASE_PATH + "}${" +
                    HealthEndpoint.PREFIX + ".id:health}";
    public static final String LIVENESS_PROBE_MAPPING = DEFAULT_MAPPING + "/liveness";
    public static final String READINESS_PROBE_MAPPING = DEFAULT_MAPPING + "/readiness";

    private final HealthEndpoint healthEndpoint;

    /**
     * Default constructor.
     *
     * @param healthEndpoint The health endpoint
     */
    protected HealthResultFilter(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    @Override
    protected Publisher<MutableHttpResponse<?>> doFilterOnce(HttpRequest<?> request, ServerFilterChain chain) {
        return Publishers.map(chain.proceed(request), response -> {
            Object body = response.body();
            if (body instanceof HealthResult) {
                HealthResult healthResult = (HealthResult) body;
                HealthStatus status = healthResult.getStatus();

                HttpStatus httpStatus = healthEndpoint
                                            .getStatusConfiguration()
                                            .getHttpMapping()
                                            .get(status.getName());
                if (httpStatus != null) {
                    response.status(httpStatus);
                } else {
                    boolean operational = status.getOperational().orElse(true);
                    if (!operational) {
                        response.status(HttpStatus.SERVICE_UNAVAILABLE);
                    }
                }
            }
            return response;
        });
    }
}
