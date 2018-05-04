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

import io.micronaut.context.annotation.Value;

import javax.annotation.Nullable;
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

    protected final boolean securityEnabled;

    public HealthLevelOfDetailResolver(@Value("${micronaut.security.enabled:false}") boolean securityEnabled) {
        this.securityEnabled = securityEnabled;
    }

    HealthLevelOfDetail levelOfDetail(@Nullable Principal principal) {
        if ( securityEnabled && principal == null) {
            return HealthLevelOfDetail.STATUS;
        }

        return HealthLevelOfDetail.STATUS_DESCRIPTION_DETAILS;
    }
}
