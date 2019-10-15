/*
 * Copyright 2017-2019 original authors
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

package io.micronaut.http.client;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.filter.HttpClientFilter;

import java.net.URI;
import java.util.List;

/**
 * Resolver that responsible to finding all the http client filters that should be applied to given request.
 *
 * @since 1.2.4
 * @author svishnyakoff
 */
@Internal
public interface FilterResolver {

    /**
     * Resolves given request to a set of http filters.
     *
     * @param request    http request
     * @param requestURI request uri
     * @return List of http filters that should be applied to a given request.
     */
    List<HttpClientFilter> resolveFilters(io.micronaut.http.HttpRequest<?> request, URI requestURI);
}
