/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.management.endpoint.env;

import io.micronaut.core.annotation.NonNull;

/**
 * A bean interface that allows hiding or masking of parts of the environment and its property sources when they are
 * displayed in the {@link EnvironmentEndpoint}.
 *
 * @author Tim Yates
 * @since 3.3.0
 */
public interface EnvironmentEndpointFilter {

    /**
     * Configure the filtering of PropertySources for the environment endpoint.
     *
     * @param specification a specification of which properties are masked or hidden from the endpoint.
     */
    void specifyFiltering(@NonNull EnvironmentFilterSpecification specification);
}
