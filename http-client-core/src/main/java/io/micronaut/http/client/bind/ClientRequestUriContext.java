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

import java.util.ArrayList;
import java.util.List;
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
    private final Map<String, List<String>> queryParameters;
    private final UriMatchTemplate uriTemplate;

    @Internal
    public ClientRequestUriContext(UriMatchTemplate uriTemplate,
                                   Map<String, Object> pathParameters,
                                   Map<String, List<String>> queryParameters) {
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
    public Map<String, List<String>> getQueryParameters() {
        return queryParameters;
    }

    /**
     * Add a new query parameter given its name. If parameter with name already exists, the parameter
     * will be duplicated in query.
     *
     * @param name - the name of the parameter
     * @param value - that value to add
     */
    public void addQueryParameter(String name, String value) {
        List<String> values = queryParameters.computeIfAbsent(name, k -> new ArrayList<>());
        values.add(value);
    }

    /**
     * Set all the values of query parameters.
     *
     * @param name - the name of the parameter
     * @param values - all the values of the parameter
     */
    public void setQueryParameter(String name, List<String> values) {
        queryParameters.put(name, values);
    }

    /**
     * Set the value of a path parameter.
     *
     * @param name - the name of the parameter
     * @param value - the value of the parameter
     */
    public void setPathParameter(String name, Object value) {
        pathParameters.put(name, value);
    }
}
