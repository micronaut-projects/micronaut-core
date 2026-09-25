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
package io.micronaut.web.router.spi;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.http.uri.UriTemplateMatcher;

/**
 * The operations the parsers of generated {@link RoutePlan}s are made of. A parser reads the path
 * segment by segment: at a segment that starts at the slash at index {@code i} and ends before
 * {@code end}, it compares the literal segments of the templates and asks the rule of each
 * variable segment.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@UsedByGeneratedCode
public final class RoutePlanSupport {

    private RoutePlanSupport() {
    }

    /**
     * @param path The normalised path
     * @return Whether the path is the root path
     */
    @UsedByGeneratedCode
    public static boolean isRoot(String path) {
        return path.isEmpty() || path.length() == 1 && path.charAt(0) == '/';
    }

    /**
     * The end of the segment that starts with the slash at an index.
     *
     * @param path The normalised path
     * @param i    The index of the slash
     * @return The index of the next slash or the length of the path, or {@code -1} if there is no slash at the index
     */
    @UsedByGeneratedCode
    public static int segmentEnd(String path, int i) {
        if (i >= path.length() || path.charAt(i) != '/') {
            return -1;
        }
        int end = path.indexOf('/', i + 1);
        return end < 0 ? path.length() : end;
    }

    /**
     * @param path    The normalised path
     * @param i       The index of the slash that starts the segment
     * @param end     The end of the segment
     * @param literal The literal segment of a template
     * @return Whether the segment is the literal
     */
    @UsedByGeneratedCode
    public static boolean literal(String path, int i, int end, String literal) {
        return end - i - 1 == literal.length() && path.startsWith(literal, i + 1);
    }

    /**
     * The rule of a whole-segment variable of a Micronaut template, see
     * {@link UriTemplateMatcher#acceptsSegmentVariable(String, int, int)}.
     *
     * @param path The normalised path
     * @param i    The index of the slash that starts the segment
     * @param end  The end of the segment
     * @return Whether the variable accepts the segment
     */
    @UsedByGeneratedCode
    public static boolean micronautVariable(String path, int i, int end) {
        return UriTemplateMatcher.acceptsSegmentVariable(path, i + 1, end);
    }

    /**
     * The rule of a variable that accepts any segment that is not empty.
     *
     * @param path The normalised path
     * @param i    The index of the slash that starts the segment
     * @param end  The end of the segment
     * @return Whether the segment is not empty
     */
    @UsedByGeneratedCode
    public static boolean nonEmptySegment(String path, int i, int end) {
        return end > i + 1;
    }

    /**
     * Record the span of a captured segment.
     *
     * @param spans The spans
     * @param index The index of the path variable among the captures of the slot
     * @param i     The index of the slash that starts the segment
     * @param end   The end of the segment
     */
    @UsedByGeneratedCode
    public static void capture(int[] spans, int index, int i, int end) {
        spans[2 * index] = i + 1;
        spans[2 * index + 1] = end;
    }
}
