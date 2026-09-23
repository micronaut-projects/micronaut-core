/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.filter;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;

import java.net.URI;

/**
 * A {@link HttpRequest#mutate() mutable view} of a request that knows whether its URI was set,
 * see {@link MutableHttpRequest#uri(URI)}, so that a filter method given the view does not parse
 * the URI of the request to find out whether the filter changed it.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public interface UriChangeAwareRequest {

    /**
     * @return Whether the URI of the view was set, which may still be the URI of the request
     */
    boolean isUriSet();
}
