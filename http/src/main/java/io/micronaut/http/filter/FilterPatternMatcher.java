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
import io.micronaut.core.util.PathMatcher;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.annotation.Filter;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Matches a request path against the patterns of a filter, the way the {@link PathMatcher} of the
 * {@link FilterPatternStyle} does when it is called for each pattern until one matches, but with
 * the work that does not depend on the path done once:
 *
 * <ul>
 *     <li>the Ant pattern {@link Filter#MATCH_ALL_PATTERN /**} matches every path that starts with
 *     a {@code /} (and no other path), so it is checked without tokenizing the pattern and the
 *     path;</li>
 *     <li>the regular expressions are compiled once, and several of them are combined into a
 *     single alternation when that cannot change what they match, so a request allocates one
 *     {@link java.util.regex.Matcher} instead of one per pattern.</li>
 * </ul>
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Internal
public final class FilterPatternMatcher {

    private static final String PATH_SEPARATOR = "/";

    /**
     * The patterns that are matched one at a time with {@link #pathMatcher}.
     */
    private final String[] patterns;
    private final PathMatcher pathMatcher;
    /**
     * Whether an Ant {@code /**} pattern makes every path starting with {@code /} match.
     */
    private final boolean matchAll;
    /**
     * The regular expressions compiled into one pattern, or {@code null} when they are matched
     * one at a time.
     */
    private final @Nullable Pattern regex;

    private FilterPatternMatcher(String[] patterns, PathMatcher pathMatcher, boolean matchAll, @Nullable Pattern regex) {
        this.patterns = patterns;
        this.pathMatcher = pathMatcher;
        this.matchAll = matchAll;
        this.regex = regex;
    }

    /**
     * Creates a matcher for the given patterns.
     *
     * @param patternStyle The pattern style, {@code null} for the default style
     * @param patterns     The patterns, a path matches when any of them matches
     * @return The matcher
     */
    public static FilterPatternMatcher of(@Nullable FilterPatternStyle patternStyle, Collection<String> patterns) {
        FilterPatternStyle style = patternStyle != null ? patternStyle : FilterPatternStyle.defaultStyle();
        PathMatcher pathMatcher = style.getPathMatcher();
        if (style == FilterPatternStyle.ANT) {
            boolean matchAll = false;
            List<String> remaining = new ArrayList<>(patterns.size());
            for (String pattern : patterns) {
                // only the default Ant matcher is known to treat "/**" as "starts with /"
                if (pathMatcher == PathMatcher.ANT && Filter.MATCH_ALL_PATTERN.equals(pattern)) {
                    matchAll = true;
                } else {
                    remaining.add(pattern);
                }
            }
            return new FilterPatternMatcher(remaining.toArray(StringUtils.EMPTY_STRING_ARRAY), pathMatcher, matchAll, null);
        }
        String[] patternArray = patterns.toArray(StringUtils.EMPTY_STRING_ARRAY);
        Pattern regex = pathMatcher == PathMatcher.REGEX ? compileRegex(patternArray) : null;
        return new FilterPatternMatcher(regex != null ? StringUtils.EMPTY_STRING_ARRAY : patternArray, pathMatcher, false, regex);
    }

    /**
     * Whether the patterns match every path that starts with a {@code /}: the only pattern is the
     * Ant pattern {@link Filter#MATCH_ALL_PATTERN /**}.
     *
     * @return {@code true} if every path starting with {@code /} matches
     */
    public boolean matchesAllPaths() {
        return matchAll && patterns.length == 0;
    }

    /**
     * Whether the given path matches any of the patterns.
     *
     * @param path The request path
     * @return {@code true} if a pattern matches
     */
    public boolean matches(@Nullable String path) {
        if (path == null) {
            return false;
        }
        if (matchAll && path.startsWith(PATH_SEPARATOR)) {
            return true;
        }
        if (regex != null) {
            return regex.matcher(path).matches();
        }
        for (String pattern : patterns) {
            if (pathMatcher.matches(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Compiles the regular expressions into one pattern that fully matches a path if and only if
     * one of them fully matches it.
     *
     * @param patterns The regular expressions
     * @return The compiled pattern, or {@code null} to match the expressions one at a time
     */
    private static @Nullable Pattern compileRegex(String[] patterns) {
        if (patterns.length == 0) {
            return null;
        }
        if (patterns.length == 1) {
            return compileOrNull(patterns[0]);
        }
        var combined = new StringBuilder();
        for (String pattern : patterns) {
            // Each expression is wrapped in a non-capturing group of an alternation. That keeps
            // its meaning unless it can reach outside of the group: an inline flag or construct
            // "(?..." (comments mode could swallow the rest of the alternation), an unterminated
            // quote "\Q", or a capturing group whose number a back reference of a later
            // expression would no longer refer to.
            if (pattern.contains("(?") || pattern.contains("\\Q")) {
                return null;
            }
            Pattern compiled = compileOrNull(pattern);
            if (compiled == null || compiled.matcher("").groupCount() > 0) {
                return null;
            }
            if (!combined.isEmpty()) {
                combined.append('|');
            }
            combined.append("(?:").append(pattern).append(')');
        }
        return compileOrNull(combined.toString());
    }

    private static @Nullable Pattern compileOrNull(String pattern) {
        try {
            return Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            // matched one at a time, which fails when the request is matched, as it always did
            return null;
        }
    }
}
