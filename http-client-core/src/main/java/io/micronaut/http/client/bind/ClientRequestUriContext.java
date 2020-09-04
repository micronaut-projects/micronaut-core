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
package io.micronaut.http.client.bind;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.uri.UriMatchTemplate;

import java.util.Map;

/**
 * A class that exposes information about the URI to {@link ClientArgumentRequestBinder} instances.
 * The binders can mutate the path and query parameters to allow control over the resulting URI.
 *
 * @author James Kleeh
 * @since 2.1.0
 */
public class ClientRequestUriContext {

    private final Map<String, Object> pathParameters;
    private final Map<String, String> queryParameters;
    private final UriMatchTemplate uriTemplate;

    @Internal
    public ClientRequestUriContext(UriMatchTemplate uriTemplate,
                                   Map<String, Object> pathParameters,
                                   Map<String, String> queryParameters) {
        this.uriTemplate = uriTemplate;
        this.pathParameters = pathParameters;
        this.queryParameters = queryParameters;
    }

    /**
     * @return The URI template for the client method
     */
    public UriMatchTemplate getUriTemplate() {
        return uriTemplate;
    }

    /**
     * @see UriMatchTemplate#expand(Map)
     * @return The parameters used to expand the URI template.
     */
    public Map<String, Object> getPathParameters() {
        return pathParameters;
    }

    /**
     * @return The parameters to be appended to the URI template as query parameters
     */
    public Map<String, String> getQueryParameters() {
        return queryParameters;
    }
}
