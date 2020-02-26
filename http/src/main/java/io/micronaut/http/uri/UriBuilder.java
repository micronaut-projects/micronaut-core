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
package io.micronaut.http.uri;

import io.micronaut.core.util.ArgumentUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.net.URI;
import java.util.Map;

/**
 * Helper class for building URIs and handle encoding correctly.
 *
 * @author graemerocher
 * @since 1.0.2
 */
public interface UriBuilder {

    /**
     * Sets the URI fragment.
     *
     * @param fragment The fragument
     * @return This builder
     */
    @NonNull UriBuilder fragment(@Nullable String fragment);

    /**
     * Sets the URI scheme.
     *
     * @param scheme The scheme
     * @return This builder
     */
    @NonNull UriBuilder scheme(@Nullable String scheme);

    /**
     * Sets the URI user info.
     *
     * @param userInfo The use info
     * @return This builder
     */
    @NonNull UriBuilder userInfo(@Nullable String userInfo);

    /**
     * Sets the URI host.
     *
     * @param host The host to use
     * @return This builder
     */
    @NonNull UriBuilder host(@Nullable String host);

    /**
     * Sets the URI port.
     *
     * @param port The port to use
     * @return This builder
     */
    @NonNull UriBuilder port(int port);

    /**
     * Appends the given path to the existing path correctly handling '/'. If path is null it is simply ignored.
     *
     * @param path The path
     * @return This builder
     */
    @NonNull UriBuilder path(@Nullable String path);

    /**
     * Replaces the existing path if any. If path is null it is simply ignored.
     *
     * @param path The path
     * @return This builder
     */
    @NonNull UriBuilder replacePath(@Nullable String path);

    /**
     * Adds a query parameter for the give name and values. The values will be URI encoded.
     * If either name or values are null the value will be ignored.
     *
     * @param name The name
     * @param values The values
     * @return This builder
     */
    @NonNull UriBuilder queryParam(String name, Object...values);

    /**
     * The constructed URI.
     *
     * @return Build the URI
     * @throws io.micronaut.http.exceptions.UriSyntaxException if the URI to be constructed is invalid
     */
    @NonNull URI build();

    /**
     * Expands the URI if it is a template, using the given values.
     *
     * @param values Expands the URI with the given values.
     * @return Build the URI
     */
    @NonNull URI expand(Map<String, ? super Object> values);

    /**
     * Create a {@link UriBuilder} with the given base URI as a starting point.
     *
     * @param uri The URI
     * @return The builder
     */
    static @NonNull UriBuilder of(@NonNull URI uri) {
        ArgumentUtils.requireNonNull("uri", uri);
        return new DefaultUriBuilder(uri);
    }

    /**
     * Create a {@link UriBuilder} with the given base URI as a starting point.
     *
     * @param uri The URI
     * @return The builder
     */
    static @NonNull UriBuilder of(@NonNull CharSequence uri) {
        ArgumentUtils.requireNonNull("uri", uri);
        return new DefaultUriBuilder(uri);
    }
}
