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
package io.micronaut.multitenancy.tenantresolver;

import io.micronaut.core.util.Toggleable;

/**
 * {@link io.micronaut.multitenancy.tenantresolver.HttpHeaderTenantResolver} configuration.
 *
 * @author Sergio del Amo
 * @since 1.0.0
 */
public interface HttpHeaderTenantResolverConfiguration extends Toggleable {
    String DEFAULT_HEADER_NAME = "tenantId";

    /**
     * Http Header name which should be used to resolve the tenant id from.
     * @return a String containing the Http Header name.
     */
    String getHeaderName();
}
