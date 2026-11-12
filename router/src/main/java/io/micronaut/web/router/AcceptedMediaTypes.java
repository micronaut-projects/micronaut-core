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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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
     * produces all types, the first concrete acceptable accepted type. A route that produces all
     * types answers in its default type, JSON, whenever it is acceptable, as it does without
     * accepted types of the quality {@code 0}: the body might not be encodable in another type.
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
        List<Produced> acceptable = acceptableProduced(produces, accepted);
        if (!acceptable.isEmpty()) {
            for (MediaType range : accepted) {
                if (isRejected(range)) {
                    continue;
                }
                for (Produced type : acceptable) {
                    if (covers(range, type.type(), type.parameters())) {
                        return type.type();
                    }
                }
            }
        }
        if (producesAll) {
            Set<String> rejectedKeys = rejectedKeys(accepted);
            for (MediaType range : accepted) {
                if (!isRejected(range) && !isRange(range) && !isOverridden(range, rejectedKeys)) {
                    return withoutQuality(range);
                }
            }
        }
        return null;
    }

    /**
     * The acceptable types of a response in the order of {@link #negotiate}: the first is the
     * negotiated type, the others are the alternatives when the body cannot be written in it.
     *
     * @param produces    The types the route produces, {@code *}{@code /*} producing JSON
     * @param producesAll Whether the route produces all types
     * @param accepted    The accepted types, ordered by the preference of the client
     * @return The acceptable types, empty when no type is acceptable
     * @since 5.3.0
     */
    public static List<MediaType> negotiateAll(List<MediaType> produces, boolean producesAll, @Nullable Collection<MediaType> accepted) {
        if (accepted == null || accepted.isEmpty()) {
            return List.of(produces.isEmpty() ? MediaType.APPLICATION_JSON_TYPE : concrete(produces.get(0)));
        }
        var result = new ArrayList<MediaType>(2);
        // the names of the types in the result, media types being equal by their name
        var names = new HashSet<String>();
        List<Produced> acceptable = acceptableProduced(produces, accepted);
        if (!acceptable.isEmpty()) {
            for (MediaType range : accepted) {
                if (isRejected(range)) {
                    continue;
                }
                for (Produced type : acceptable) {
                    if (covers(range, type.type(), type.parameters()) && names.add(lowerName(type.type()))) {
                        result.add(type.type());
                    }
                }
            }
        }
        if (producesAll) {
            Set<String> rejectedKeys = rejectedKeys(accepted);
            for (MediaType range : accepted) {
                if (!isRejected(range) && !isRange(range) && !isOverridden(range, rejectedKeys)
                    && names.add(lowerName(range))) {
                    result.add(withoutQuality(range));
                }
            }
        }
        return result;
    }

    /**
     * Whether a type, possibly a range, has an acceptable intersection with the accepted types:
     * an accepted range covers it and it is acceptable, or it is a range that covers an accepted
     * type that is acceptable. No default type is substituted for a range.
     *
     * @param type     A type or a range, such as {@code text/*} or {@code *}{@code /*}
     * @param accepted The accepted types
     * @return Whether some type of the intersection is acceptable
     */
    public static boolean intersects(MediaType type, Collection<MediaType> accepted) {
        if (accepted.isEmpty()) {
            return true;
        }
        boolean range = isRange(type);
        Set<String> typeParameters = parameters(type);
        Boolean typeAcceptable = null;
        Set<String> rejectedKeys = null;
        for (MediaType acceptedType : accepted) {
            if (isRejected(acceptedType)) {
                continue;
            }
            if (covers(acceptedType, type, typeParameters)) {
                if (typeAcceptable == null) {
                    typeAcceptable = isAcceptable(type, typeParameters, accepted);
                }
                if (typeAcceptable) {
                    return true;
                }
            } else if (range && type.matches(acceptedType)) {
                if (rejectedKeys == null) {
                    rejectedKeys = rejectedKeys(accepted);
                }
                if (!isOverridden(acceptedType, rejectedKeys)) {
                    return true;
                }
            }
        }
        return false;
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
        return isAcceptable(produced, parameters(produced), accepted);
    }

    private static boolean isAcceptable(MediaType produced, Set<String> producedParameters, Collection<MediaType> accepted) {
        MediaType best = null;
        long bestSpecificity = -1;
        for (MediaType range : accepted) {
            if (!range.matches(produced)) {
                continue;
            }
            Set<String> parameters = parameters(range);
            if (producedParameters.containsAll(parameters)) {
                long specificity = specificity(range, parameters);
                // with the same specificity a rejection wins
                if (specificity > bestSpecificity || (specificity == bestSpecificity && isRejected(range))) {
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
     * @param produces The types the route produces
     * @param accepted The accepted types
     * @return The concrete produced types that are acceptable, in their order
     */
    private static List<Produced> acceptableProduced(List<MediaType> produces, Collection<MediaType> accepted) {
        var acceptable = new ArrayList<Produced>(produces.size());
        for (MediaType type : produces) {
            MediaType concrete = concrete(type);
            Set<String> parameters = parameters(concrete);
            if (isAcceptable(concrete, parameters, accepted)) {
                acceptable.add(new Produced(concrete, parameters));
            }
        }
        return acceptable;
    }

    /**
     * Whether an accepted type of a quality above {@code 0} is not acceptable, without searching
     * all the accepted ranges: the type covers itself and no range that covers it is more
     * specific, so it is not acceptable exactly when a rejected range of the same specificity
     * covers it, one with the same range and the same parameters.
     *
     * @param acceptedType An accepted type of a quality above {@code 0}
     * @param rejectedKeys The {@link #key keys} of the accepted types of the quality {@code 0}
     * @return Whether the type is not acceptable
     */
    private static boolean isOverridden(MediaType acceptedType, Set<String> rejectedKeys) {
        if (rejectedKeys.isEmpty()) {
            return false;
        }
        Set<String> parameters = parameters(acceptedType);
        if (rejectedKeys.contains(key(acceptedType.getType(), acceptedType.getSubtype(), parameters))) {
            return true;
        }
        // */* covers */subtype with the same specificity
        return WILDCARD.equals(acceptedType.getType()) && !WILDCARD.equals(acceptedType.getSubtype())
            && rejectedKeys.contains(key(WILDCARD, WILDCARD, parameters));
    }

    private static Set<String> rejectedKeys(Collection<MediaType> accepted) {
        var keys = new HashSet<String>();
        for (MediaType range : accepted) {
            if (isRejected(range)) {
                keys.add(key(range.getType(), range.getSubtype(), parameters(range)));
            }
        }
        return keys;
    }

    /**
     * @param type       The type of a range
     * @param subtype    The subtype of the range
     * @param parameters The {@link #parameters} of the range
     * @return A key that is equal for the ranges that cover the same types
     */
    private static String key(String type, String subtype, Set<String> parameters) {
        String name = foldCase(type) + '/' + foldCase(subtype);
        var key = new StringBuilder().append(name.length()).append(':').append(name);
        for (String parameter : parameters) {
            key.append(parameter.length()).append(':').append(parameter);
        }
        return key.toString();
    }

    private static boolean isRange(MediaType type) {
        return WILDCARD.equals(type.getType()) || WILDCARD.equals(type.getSubtype());
    }

    private static String lowerName(MediaType type) {
        return type.getName().toLowerCase(Locale.ROOT);
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
     * @param range          The accepted range
     * @param type           A media type
     * @param typeParameters The {@link #parameters} of the type
     * @return Whether the range covers the type: its type and subtype, and the parameters of the
     * range other than the quality
     */
    private static boolean covers(MediaType range, MediaType type, Set<String> typeParameters) {
        return range.matches(type) && typeParameters.containsAll(parameters(range));
    }

    /**
     * The parameters other than the quality as name-value pairs, in one case where they are
     * case-insensitive: the names, and the values of the parameters defined as case-insensitive,
     * such as the charset, RFC 9110 8.3.1.
     *
     * @param type A media type
     * @return The parameters, sorted
     */
    private static Set<String> parameters(MediaType type) {
        Map<CharSequence, String> parameters = type.getParametersMap();
        if (parameters.isEmpty()) {
            return Collections.emptySortedSet();
        }
        TreeSet<String> result = null;
        for (Map.Entry<CharSequence, String> parameter : parameters.entrySet()) {
            String name = parameter.getKey().toString();
            if (QUALITY.equalsIgnoreCase(name)) {
                continue;
            }
            String foldedName = foldCase(name);
            String value = CHARSET.equals(foldedName) ? foldCase(parameter.getValue()) : parameter.getValue();
            if (result == null) {
                result = new TreeSet<>();
            }
            result.add(foldedName.length() + ":" + foldedName + "=" + value);
        }
        return result == null ? Collections.emptySortedSet() : result;
    }

    /**
     * @param value A value
     * @return The value in the case in which {@link String#equalsIgnoreCase} compares it
     */
    private static String foldCase(String value) {
        boolean ascii = true;
        for (int i = 0; i < value.length() && ascii; i++) {
            ascii = value.charAt(i) < 0x80;
        }
        if (ascii) {
            return value.toLowerCase(Locale.ROOT);
        }
        var folded = new StringBuilder(value.length());
        value.codePoints().forEach(c -> folded.appendCodePoint(Character.toLowerCase(Character.toUpperCase(c))));
        return folded.toString();
    }

    /**
     * @param range      The accepted range
     * @param parameters The {@link #parameters} of the range
     * @return The precedence of the range, RFC 9110 12.5.1: a more specific range overrides a
     * less specific one, the parameters other than the quality making it more specific
     */
    private static long specificity(MediaType range, Set<String> parameters) {
        int level;
        if (WILDCARD.equals(range.getType())) {
            level = 0;
        } else if (WILDCARD.equals(range.getSubtype())) {
            level = 1;
        } else {
            level = 2;
        }
        // the wildcard level first, then the parameters within the level: a range covering
        // another one is as specific only with the same key, which isOverridden relies on
        return ((long) level << Integer.SIZE) | parameters.size();
    }

    /**
     * A produced type with its {@link #parameters}.
     *
     * @param type       The type
     * @param parameters The parameters
     */
    private record Produced(MediaType type, Set<String> parameters) {
    }
}
