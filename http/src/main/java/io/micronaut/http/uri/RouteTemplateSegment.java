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

import java.util.Objects;

/**
 * A path segment of a {@link ParsedRouteTemplate} as the router sees it when it decides whether two
 * templates can match the same path: a literal, a variable that is exactly one segment, or a part
 * that can match any number of segments.
 *
 * @param kind    The kind of segment
 * @param literal The literal text of a {@link Kind#LITERAL} segment, otherwise an empty string
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public record RouteTemplateSegment(Kind kind, String literal) {

    /**
     * A variable that matches exactly one non-empty segment.
     */
    public static final RouteTemplateSegment VARIABLE = new RouteTemplateSegment(Kind.VARIABLE, "");

    /**
     * A part that may match any number of segments, including none.
     */
    public static final RouteTemplateSegment ANY = new RouteTemplateSegment(Kind.ANY, "");

    /**
     * @param kind    The kind of segment
     * @param literal The literal text
     */
    public RouteTemplateSegment {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(literal, "literal");
    }

    /**
     * @param literal The text of the segment, without slashes
     * @return A literal segment
     */
    public static RouteTemplateSegment literal(String literal) {
        return new RouteTemplateSegment(Kind.LITERAL, literal);
    }

    /**
     * The kinds of segment.
     */
    public enum Kind {
        /**
         * Literal text that must be equal.
         */
        LITERAL,
        /**
         * A variable that is exactly one segment.
         */
        VARIABLE,
        /**
         * Anything else: may match any number of segments.
         */
        ANY
    }
}
