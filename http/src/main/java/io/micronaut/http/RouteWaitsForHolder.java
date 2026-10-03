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
package io.micronaut.http;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

/**
 * A request that stores the condition its route waits for (see
 * {@link BasicHttpAttributes#addRouteWaitsFor}) in a typed field instead of the generic
 * {@link HttpMessage#getAttributes() attribute map}, so that binding a body does not have to
 * allocate that map. Like the fields of a {@link RouteMetadataHolder}, the field is the only store
 * of that attribute: the {@link RouteMetadataAttributes} attribute map and attribute accessors of
 * a holder that also implements this interface read and write it through these accessors.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public interface RouteWaitsForHolder {

    /**
     * @return The condition the route waits for, usually an
     * {@link io.micronaut.core.execution.ExecutionFlow}
     */
    @Nullable
    Object getRouteWaitsForMetadata();

    /**
     * @param routeWaitsFor The condition the route waits for, or {@code null} to remove it
     */
    void setRouteWaitsForMetadata(@Nullable Object routeWaitsFor);
}
