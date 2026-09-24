/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.http;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

/**
 * A request or response that stores the metadata of the matched route in typed fields instead of
 * the generic {@link HttpMessage#getAttributes() attribute map}, so that the per-request routing
 * does not have to allocate that map. The fields back the {@link HttpAttributes#ROUTE_MATCH},
 * {@link HttpAttributes#ROUTE_INFO} and {@link HttpAttributes#URI_TEMPLATE} attributes: the
 * message must keep those attributes and the fields consistent, e.g. by moving the fields into
 * the attribute map once {@link HttpMessage#getAttributes()} is called.
 * <p>
 * The route match and route info are typed as {@link Object} because their types live in the
 * router module, which this module cannot depend on. The accessors in
 * {@code io.micronaut.web.router.RouteAttributes} do the casts.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@SuppressWarnings("removal")
public interface RouteMetadataHolder {

    /**
     * @return The route match, backing {@link HttpAttributes#ROUTE_MATCH}
     */
    @Nullable
    Object getRouteMatchMetadata();

    /**
     * @param routeMatch The route match, or {@code null} to remove it
     */
    void setRouteMatchMetadata(@Nullable Object routeMatch);

    /**
     * @return The route info, backing {@link HttpAttributes#ROUTE_INFO}
     */
    @Nullable
    Object getRouteInfoMetadata();

    /**
     * @param routeInfo The route info, or {@code null} to remove it
     */
    void setRouteInfoMetadata(@Nullable Object routeInfo);

    /**
     * @return The URI template of the route, backing {@link HttpAttributes#URI_TEMPLATE}
     */
    @Nullable
    String getUriTemplateMetadata();

    /**
     * @param uriTemplate The URI template, or {@code null} to remove it
     */
    void setUriTemplateMetadata(@Nullable String uriTemplate);
}
