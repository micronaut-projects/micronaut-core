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

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.MediaType;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The checks of the accepted media types that take a quality of {@code 0} into account: an
 * accepted media range with {@code q=0} means "not acceptable", see RFC 9110, section 12.4.2.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class AcceptedMediaTypes {

    private static final String WILDCARD = "*";
    private static final String QUALITY = "q";
    private static final String CHARSET = "charset";
    private static final int LEVEL = 1000;

    private AcceptedMediaTypes() {
    }

    /**
     * @param type An accepted media type
     * @return Whether the type is accepted with the quality {@code 0}, meaning "not acceptable"
     */
    public static boolean isRejected(MediaType type) {
        return type.getQualityAsNumber().signum() == 0;
    }

    /**
     * @param accepted The accepted media types
     * @return Whether one of the accepted media types has the quality {@code 0}
     */
    public static boolean hasRejected(@Nullable Collection<MediaType> accepted) {
        if (accepted == null) {
            return false;
        }
        for (MediaType type : accepted) {
            if (isRejected(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Negotiates the type of a response: for each accepted range, by the preference of the client,
     * the first produced type that it covers and that is acceptable, else, for a route that
     * produces all types, the accepted type itself when it is concrete and acceptable.
     *
     * @param produces    The types the route produces, {@code *}{@code /*} producing JSON
     * @param producesAll Whether the route produces all types
     * @param accepted    The accepted types, ordered by the preference of the client
     * @return The negotiated type, {@code null} when no type is acceptable, the first produced type
     * without accepted types
     */
    public static @Nullable MediaType negotiate(List<MediaType> produces, boolean producesAll, @Nullable Collection<MediaType> accepted) {
        if (accepted == null || accepted.isEmpty()) {
            return produces.isEmpty() ? MediaType.APPLICATION_JSON_TYPE : concrete(produces.get(0));
        }
        for (MediaType range : accepted) {
            if (isRejected(range)) {
                continue;
            }
            for (MediaType type : produces) {
                MediaType concrete = concrete(type);
                if (covers(range, concrete) && isAcceptable(concrete, accepted)) {
                    return concrete;
                }
            }
            if (producesAll && !WILDCARD.equals(range.getType()) && !WILDCARD.equals(range.getSubtype())
                && isAcceptable(range, accepted)) {
                return withoutQuality(range);
            }
        }
        return null;
    }

    /**
     * @param produced A produced media range, such as {@code text/*}
     * @param accepted The accepted types
     * @return Whether the range covers a concrete accepted type that is acceptable
     */
    public static boolean coversAcceptable(MediaType produced, Collection<MediaType> accepted) {
        if (!WILDCARD.equals(produced.getType()) && !WILDCARD.equals(produced.getSubtype())) {
            return false;
        }
        for (MediaType type : accepted) {
            if (!isRejected(type) && !WILDCARD.equals(type.getType()) && !WILDCARD.equals(type.getSubtype())
                && produced.matches(type) && isAcceptable(type, accepted)) {
                return true;
            }
        }
        return false;
    }

    private static MediaType concrete(MediaType type) {
        return MediaType.ALL_TYPE.equals(type) ? MediaType.APPLICATION_JSON_TYPE : type;
    }

    private static MediaType withoutQuality(MediaType type) {
        Map<CharSequence, String> parameters = type.getParametersMap();
        if (parameters.isEmpty()) {
            return type;
        }
        var withoutQuality = new LinkedHashMap<String, String>();
        for (Map.Entry<CharSequence, String> parameter : parameters.entrySet()) {
            String name = parameter.getKey().toString();
            if (!QUALITY.equalsIgnoreCase(name)) {
                withoutQuality.put(name, parameter.getValue());
            }
        }
        return withoutQuality.size() == parameters.size() ? type : new MediaType(type.getName(), withoutQuality);
    }

    /**
     * Whether a produced media type is acceptable: the most specific accepted media range that
     * covers it decides, a range of the quality {@code 0} rejecting it. A produced media range is
     * acceptable when it covers an accepted type with a quality above {@code 0}.
     *
     * @param produced The produced media type
     * @param accepted The accepted media types
     * @return Whether the produced type is acceptable, {@code true} without accepted types
     */
    public static boolean isAcceptable(MediaType produced, @Nullable Collection<MediaType> accepted) {
        if (accepted == null || accepted.isEmpty()) {
            return true;
        }
        MediaType best = null;
        int bestSpecificity = -1;
        for (MediaType range : accepted) {
            if (covers(range, produced)) {
                int specificity = specificity(range);
                // with the same specificity a rejection wins
                if (specificity > bestSpecificity || specificity == bestSpecificity && isRejected(range)) {
                    best = range;
                    bestSpecificity = specificity;
                }
            }
        }
        if (best != null) {
            return !isRejected(best);
        }
        for (MediaType type : accepted) {
            if (!isRejected(type) && produced.matches(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param range The accepted range
     * @param type  A media type
     * @return Whether the range covers the type: its type and subtype, and the parameters of the
     * range other than the quality
     */
    private static boolean covers(MediaType range, MediaType type) {
        if (!range.matches(type)) {
            return false;
        }
        Map<CharSequence, String> typeParameters = type.getParametersMap();
        for (Map.Entry<CharSequence, String> parameter : range.getParametersMap().entrySet()) {
            String name = parameter.getKey().toString();
            if (QUALITY.equalsIgnoreCase(name)) {
                continue;
            }
            String value = parameterValue(typeParameters, name);
            if (value == null || !sameValue(name, value, parameter.getValue())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parameter values are case-sensitive, except the ones defined as case-insensitive, such as
     * the charset, RFC 9110 8.3.1.
     */
    private static boolean sameValue(String name, String value, String other) {
        return CHARSET.equalsIgnoreCase(name) ? value.equalsIgnoreCase(other) : value.equals(other);
    }

    private static @Nullable String parameterValue(Map<CharSequence, String> parameters, String name) {
        for (Map.Entry<CharSequence, String> parameter : parameters.entrySet()) {
            if (name.equalsIgnoreCase(parameter.getKey().toString())) {
                return parameter.getValue();
            }
        }
        return null;
    }

    /**
     * @param range The accepted range
     * @return The precedence of the range, RFC 9110 12.5.1: a more specific range overrides a
     * less specific one, the parameters other than the quality making it more specific
     */
    private static int specificity(MediaType range) {
        int level;
        if (WILDCARD.equals(range.getType())) {
            level = 0;
        } else if (WILDCARD.equals(range.getSubtype())) {
            level = 1;
        } else {
            level = 2;
        }
        int parameters = 0;
        for (CharSequence name : range.getParametersMap().keySet()) {
            if (!QUALITY.equalsIgnoreCase(name.toString())) {
                parameters++;
            }
        }
        // the wildcard level first, then the parameters within the level
        return level * LEVEL + Math.min(parameters, LEVEL - 1);
    }
}
