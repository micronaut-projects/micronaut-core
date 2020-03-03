/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.multitenancy.writer;

import io.micronaut.http.MutableHttpRequest;
import io.micronaut.multitenancy.MultitenancyConfiguration;
import java.io.Serializable;

/**
 *  Responsible for writing the tenant in the request.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public interface TenantWriter {

    String PREFIX = MultitenancyConfiguration.PREFIX + ".tenantwriter";

    /**
     * Writes the token to the request.
     * @param request The {@link MutableHttpRequest} instance
     * @param tenant tenant Id
     */
    void writeTenant(MutableHttpRequest<?> request, Serializable tenant);
}
