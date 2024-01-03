/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.inject.test.external;

import io.micronaut.context.annotation.Value;

public class ExternalConfiguration {
    private final String endpoint;
    private final boolean wrapped;
    private final int leaseMinRenewalSeconds;

    public ExternalConfiguration(@Value("${vault.endpoint:}") String endpoint,
                                 @Value("${vault.token.wrapped:false}") boolean wrapped,
                                 @Value("${vault.lease.renewal.minRenewalSeconds:10}") int leaseMinRenewalSeconds) {
        this.endpoint = endpoint;
        this.wrapped = wrapped;
        this.leaseMinRenewalSeconds = leaseMinRenewalSeconds;
    }
}
