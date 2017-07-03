/*
 * Copyright 2017 original authors
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
package org.particleframework.context.router;

import org.particleframework.http.HttpMethod;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * <p>Core Router interface that allows discovery of a route given an HTTP method and URI</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Router {

    /**
     * Finds all of the possible routes for the given HTTP method and URI
     *
     * @param httpMethod The HTTP method
     * @param uri The URI
     * @return A {@link Stream} of possible {@link Route} instances.
     */
    Stream<Route> find(HttpMethod httpMethod, CharSequence uri);
}
