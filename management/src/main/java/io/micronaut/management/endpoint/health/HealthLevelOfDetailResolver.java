/*
 * Copyright 2017-2018 original authors
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

import io.micronaut.management.endpoint.EndpointConfiguration;
import io.micronaut.management.endpoint.EndpointDefaultConfiguration;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.inject.Singleton;
import java.security.Principal;

/**
 * Resolves the {@link HealthLevelOfDetail} to be used based on the {@link Principal} existence.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
public class HealthLevelOfDetailResolver {

    /**
     * True if the health endpoint is sensitive.
     */
    protected final boolean sensitive;

    /**
     * @param healthConfiguration Health endpoint configuration
     * @param defaultConfiguration Default endpoint configuration
     */
    public HealthLevelOfDetailResolver(@Nullable @Named("health") EndpointConfiguration healthConfiguration,
                                       EndpointDefaultConfiguration defaultConfiguration) {
        EndpointConfiguration configuration = healthConfiguration == null ? new EndpointConfiguration("health", defaultConfiguration) : healthConfiguration;
        this.sensitive = configuration.isSensitive().orElse(HealthEndpoint.DEFAULT_SENSITIVE);
    }

    /**
     * Returns the level of detail that should be returned by the endpoint.
     *
     * @param principal Authenticated user
     * @return The {@link HealthLevelOfDetail}
     */
    public HealthLevelOfDetail levelOfDetail(@Nullable Principal principal) {
        if (principal == null) {
            return HealthLevelOfDetail.STATUS;
        }
        return HealthLevelOfDetail.STATUS_DESCRIPTION_DETAILS;
    }
}
