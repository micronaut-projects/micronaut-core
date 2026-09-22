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
package io.micronaut.http.uri;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;

/**
 * A prepared matcher of a {@link ParsedRouteTemplate}, created once per route by its
 * {@link io.micronaut.http.uri.spi.RouteTemplateEngine engine}.
 *
 * <p>The input is the path of a request as the router passes it to routes: the raw, not
 * percent-decoded path, which may still have a query and a trailing slash that the pattern must
 * ignore, see {@link UriTemplateMatcher#normalizeForMatching(String)}. The pattern matches the
 * complete path.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface RoutePattern {

    /**
     * @return The template this pattern matches
     */
    ParsedRouteTemplate template();

    /**
     * Match a path.
     *
     * @param path The path of the request
     * @return The captures of the variables, or {@code null} if the path does not match
     */
    @Nullable RouteCaptures match(String path);
}
