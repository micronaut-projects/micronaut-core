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
package io.micronaut.web.router;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.builder.RouteSettings;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * What the routes a locator route locates inherit from the groups of the locator routes, like
 * the routes declared in those groups: their annotations, attributes, media types and executor.
 * The settings of a located route, and of the groups of its {@link io.micronaut.web.router.builder.LocatedRoutes},
 * override them. A location keeps the same instance for every request, so the routes built with
 * it, see {@link DefaultUrlRouteInfo#inheriting(LocationInheritance)}, are built once.
 *
 * @param annotationMetadata The annotations, of the outer locator routes first
 * @param attributes         The attributes, of the outer locator routes first
 * @param consumes           The media types the routes consume, or {@code null} if no group sets them
 * @param produces           The media types the routes produce, or {@code null} if no group sets them
 * @param executorName       The executor the routes run on, or {@code null}
 * @param nonBlocking        Whether the routes run on the event loop, see {@code nonBlocking()}
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record LocationInheritance(AnnotationMetadata annotationMetadata,
                           Map<String, Object> attributes,
                           @Nullable List<MediaType> consumes,
                           @Nullable List<MediaType> produces,
                           @Nullable String executorName,
                           boolean nonBlocking) {

    /**
     * Nothing to inherit.
     */
    static final LocationInheritance NONE = new LocationInheritance(AnnotationMetadata.EMPTY_METADATA, Map.of(), null, null, null, false);

    /**
     * What the routes of a locator route inherit.
     *
     * @param annotationMetadata The annotations of the locator route, with the ones it inherits
     * @param attributes         The attributes of the locator route, with the ones it inherits
     * @param groupSettings      The media types and the executor of the groups of the locator route, or {@code null}
     * @param outer              What the locator route inherits from the locator routes that located it, or {@code null}
     * @return The inheritance
     */
    static LocationInheritance of(AnnotationMetadata annotationMetadata,
                                  Map<String, Object> attributes,
                                  @Nullable RouteSettings groupSettings,
                                  @Nullable LocationInheritance outer) {
        List<MediaType> consumes = groupSettings == null ? null : groupSettings.getConsumes();
        List<MediaType> produces = groupSettings == null ? null : groupSettings.getProduces();
        String executorName = groupSettings == null ? null : groupSettings.getExecutorName();
        boolean nonBlocking = groupSettings != null && groupSettings.isNonBlocking();
        if (outer != null) {
            if (consumes == null) {
                consumes = outer.consumes;
            }
            if (produces == null) {
                produces = outer.produces;
            }
            if (executorName == null && !nonBlocking) {
                executorName = outer.executorName;
                nonBlocking = outer.nonBlocking;
            }
        }
        LocationInheritance inheritance = new LocationInheritance(annotationMetadata, attributes, consumes, produces, executorName, nonBlocking);
        return inheritance.isEmpty() ? NONE : inheritance;
    }

    /**
     * @return Whether there is nothing to inherit
     */
    boolean isEmpty() {
        return annotationMetadata.isEmpty() && attributes.isEmpty() && consumes == null && produces == null && !hasExecutor();
    }

    /**
     * @return Whether a group sets the executor
     */
    boolean hasExecutor() {
        return executorName != null || nonBlocking;
    }
}
