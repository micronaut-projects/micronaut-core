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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.RandomAccess;
import java.util.Set;
import java.util.TreeSet;

/**
 * The checks of the accepted media types that take a quality of {@code 0} into account: an
 * accepted media range with {@code q=0} means "not acceptable", see RFC 9110, section 12.4.2.
 *
 * <p>The work is linear in the number of accepted media ranges: the acceptability of a produced
 * type is evaluated at most once per call, and the acceptability of an accepted type is a lookup
 * among the rejected ranges.
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
        var produced = new ProducedTypes(produces, accepted);
        for (MediaType range : accepted) {
            if (isRejected(range)) {
                continue;
            }
            int index = produced.nextAcceptable(range, 0);
            if (index >= 0) {
                return produced.type(index);
            }
        }
        if (producesAll) {
            var rejections = new Rejections(accepted);
            for (MediaType range : accepted) {
                if (!isRejected(range) && !isRange(range) && !rejections.isOverridden(range)) {
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
        var result = new Result();
        var produced = new ProducedTypes(produces, accepted);
        for (MediaType range : accepted) {
            if (isRejected(range)) {
                continue;
            }
            for (int index = produced.nextAcceptable(range, 0); index >= 0; index = produced.nextAcceptable(range, index + 1)) {
                result.add(produced.type(index));
            }
        }
        if (producesAll) {
            var rejections = new Rejections(accepted);
            for (MediaType range : accepted) {
                if (!isRejected(range) && !isRange(range) && !rejections.isOverridden(range)) {
                    result.add(withoutQuality(range));
                }
            }
        }
        return result.types;
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
        Set<String> typeParameters = null;
        Boolean typeAcceptable = null;
        Rejections rejections = null;
        for (MediaType acceptedType : accepted) {
            if (isRejected(acceptedType)) {
                continue;
            }
            if (acceptedType.matches(type)) {
                if (typeParameters == null) {
                    typeParameters = typeParameters(type);
                }
                if (coversParameters(acceptedType, typeParameters)) {
                    // the accepted range covers the type
                    if (typeAcceptable == null) {
                        typeAcceptable = isAcceptable(type, typeParameters, accepted);
                    }
                    if (typeAcceptable) {
                        return true;
                    }
                    continue;
                }
            }
            if (range && type.matches(acceptedType)) {
                if (rejections == null) {
                    rejections = new Rejections(accepted);
                }
                if (!rejections.isOverridden(acceptedType)) {
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
        return isAcceptable(produced, typeParameters(produced), accepted);
    }

    /**
     * Searches all the accepted ranges for the one that decides.
     *
     * @param produced           The produced media type
     * @param producedParameters The {@link #typeParameters} of the produced type
     * @param accepted           The accepted media types, not empty
     * @return Whether the produced type is acceptable
     */
    private static boolean isAcceptable(MediaType produced, Set<String> producedParameters, Collection<MediaType> accepted) {
        MediaType best = null;
        long bestSpecificity = -1;
        for (MediaType range : accepted) {
            if (!range.matches(produced)) {
                continue;
            }
            Set<String> parameters;
            if (producedParameters.isEmpty()) {
                if (hasParameters(range)) {
                    continue;
                }
                parameters = Collections.emptySet();
            } else {
                parameters = parameters(range);
                if (!producedParameters.containsAll(parameters)) {
                    continue;
                }
            }
            long specificity = specificity(range, parameters);
            // with the same specificity a rejection wins
            if (specificity > bestSpecificity || (specificity == bestSpecificity && isRejected(range))) {
                best = range;
                bestSpecificity = specificity;
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
     * @param range          A range that matches a type by its type and subtype
     * @param typeParameters The {@link #typeParameters} of the type
     * @return Whether the range covers the type
     */
    private static boolean coversParameters(MediaType range, Set<String> typeParameters) {
        // a type without parameters is covered only by a range without parameters
        return typeParameters.isEmpty() ? !hasParameters(range) : typeParameters.containsAll(parameters(range));
    }

    /**
     * @param range A media range
     * @return Whether the range has a parameter other than the quality
     */
    private static boolean hasParameters(MediaType range) {
        for (CharSequence name : range.getParametersMap().keySet()) {
            if (!QUALITY.equalsIgnoreCase(name.toString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRange(MediaType type) {
        return WILDCARD.equals(type.getType()) || WILDCARD.equals(type.getSubtype());
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
     * The parameters other than the quality as name-value pairs, in one case where they are
     * case-insensitive: the names, and the values of the parameters defined as case-insensitive,
     * such as the charset, RFC 9110 8.3.1. A range requires all of them.
     *
     * @param range A media range
     * @return The parameters
     */
    private static Set<String> parameters(MediaType range) {
        return parameters(range, false);
    }

    /**
     * The {@link #parameters} that a range is compared with: of a parameter repeated under names
     * that differ in case, only the value of the first in the parameter map.
     *
     * @param type A media type
     * @return The parameters
     */
    private static Set<String> typeParameters(MediaType type) {
        return parameters(type, true);
    }

    private static Set<String> parameters(MediaType type, boolean firstValues) {
        Map<CharSequence, String> parameters = type.getParametersMap();
        if (parameters.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> result = null;
        Set<String> names = null;
        for (Map.Entry<CharSequence, String> parameter : parameters.entrySet()) {
            String name = parameter.getKey().toString();
            if (QUALITY.equalsIgnoreCase(name)) {
                continue;
            }
            String foldedName = foldCase(name);
            if (firstValues && parameters.size() > 1) {
                if (names == null) {
                    names = new HashSet<>();
                }
                if (!names.add(foldedName)) {
                    continue;
                }
            }
            String value = CHARSET.equals(foldedName) ? foldCase(parameter.getValue()) : parameter.getValue();
            if (result == null) {
                result = new TreeSet<>();
            }
            // the length keeps the pair unambiguous for any name
            result.add(foldedName.length() + ":" + foldedName + "=" + value);
        }
        return result == null ? Collections.emptySet() : result;
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
     * less specific one, the distinct parameters other than the quality making it more specific
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
        // another one is as specific only with the same name and parameters, which
        // Rejections.isOverridden relies on
        return ((long) level << Integer.SIZE) | parameters.size();
    }

    /**
     * A concrete produced type, with its parameters and its acceptability once evaluated.
     */
    private static final class Produced {

        private final Set<String> parameters;
        private @Nullable Boolean acceptable;

        Produced(MediaType type) {
            this.parameters = typeParameters(type);
        }
    }

    /**
     * The produced types of a negotiation, each searched for in the accepted ranges at most once,
     * when an accepted range first covers it.
     */
    private static final class ProducedTypes {

        private final List<MediaType> produces;
        private final Collection<MediaType> accepted;
        private final @Nullable Produced[] types;

        ProducedTypes(List<MediaType> produces, Collection<MediaType> accepted) {
            // the produced types are read by index
            this.produces = produces instanceof RandomAccess ? produces : new ArrayList<>(produces);
            this.accepted = accepted;
            this.types = new Produced[produces.size()];
        }

        MediaType type(int index) {
            return concrete(produces.get(index));
        }

        /**
         * @param range An accepted range of a quality above {@code 0}
         * @param from  The index of the first produced type to consider
         * @return The index of the first produced type from the given one that the range covers
         * and that is acceptable, else {@code -1}
         */
        int nextAcceptable(MediaType range, int from) {
            Boolean rangeHasParameters = null;
            Set<String> rangeParameters = null;
            for (int i = from; i < types.length; i++) {
                MediaType type = type(i);
                if (!range.matches(type)) {
                    continue;
                }
                Produced produced = types[i];
                if (produced == null) {
                    produced = new Produced(type);
                    types[i] = produced;
                }
                boolean covers;
                if (produced.parameters.isEmpty()) {
                    // covered only by a range without parameters
                    if (rangeHasParameters == null) {
                        rangeHasParameters = hasParameters(range);
                    }
                    covers = !rangeHasParameters;
                } else {
                    if (rangeParameters == null) {
                        rangeParameters = parameters(range);
                    }
                    covers = produced.parameters.containsAll(rangeParameters);
                }
                if (!covers) {
                    continue;
                }
                Boolean acceptable = produced.acceptable;
                if (acceptable == null) {
                    acceptable = isAcceptable(type, produced.parameters, accepted);
                    produced.acceptable = acceptable;
                }
                if (acceptable) {
                    return i;
                }
            }
            return -1;
        }
    }

    /**
     * Decides whether accepted types of a quality above {@code 0} are acceptable without
     * searching all the accepted ranges for each of them.
     */
    private static final class Rejections {

        private final Collection<MediaType> accepted;
        /**
         * The accepted types of the quality {@code 0} by their {@link #name}, computed when first
         * needed.
         */
        private @Nullable Map<String, List<MediaType>> rejectedByName;
        /**
         * The {@link #key keys} of the parameters of the rejected types of a name, computed when
         * an accepted type of the name is first decided.
         */
        private final Map<String, Set<String>> rejectedKeysByName = new HashMap<>();

        Rejections(Collection<MediaType> accepted) {
            this.accepted = accepted;
        }

        /**
         * Whether an accepted type of a quality above {@code 0} is not acceptable. The type
         * covers itself, and no range that covers it is more specific, so it is not acceptable
         * exactly when a rejected range of the same specificity covers it: one with the same name
         * and parameters, or {@code *}{@code /*} with the same parameters for
         * {@code *}{@code /subtype}.
         *
         * <p>A type that repeats a parameter under names that differ in case with different
         * values does not cover itself, and finding the range that decides would search all the
         * accepted ranges for each such type. It is not a meaningful response type: it is not
         * acceptable when a rejected range of its type and subtype, or a range of them, might
         * cover it. Otherwise no rejected range covers it and it is acceptable.
         *
         * @param acceptedType An accepted type of a quality above {@code 0}
         * @return Whether the type is not acceptable
         */
        boolean isOverridden(MediaType acceptedType) {
            Map<String, List<MediaType>> byName = rejectedByName();
            if (byName.isEmpty()) {
                return false;
            }
            Set<String> typeParameters = typeParameters(acceptedType);
            if (repeatsWithAnotherValue(acceptedType, typeParameters)) {
                return mayBeRejected(byName, acceptedType);
            }
            if (isRejectedWith(byName, name(acceptedType.getType(), acceptedType.getSubtype()), typeParameters)) {
                return true;
            }
            // */* covers */subtype with the same specificity
            return WILDCARD.equals(acceptedType.getType()) && !WILDCARD.equals(acceptedType.getSubtype())
                && isRejectedWith(byName, name(WILDCARD, WILDCARD), typeParameters);
        }

        private Map<String, List<MediaType>> rejectedByName() {
            Map<String, List<MediaType>> byName = rejectedByName;
            if (byName == null) {
                byName = new HashMap<>();
                for (MediaType range : accepted) {
                    if (isRejected(range)) {
                        byName.computeIfAbsent(name(range.getType(), range.getSubtype()), k -> new ArrayList<>(1)).add(range);
                    }
                }
                rejectedByName = byName;
            }
            return byName;
        }

        /**
         * @param byName     The rejected types by name
         * @param name       A name
         * @param parameters Parameters
         * @return Whether a rejected type has the name and the parameters
         */
        private boolean isRejectedWith(Map<String, List<MediaType>> byName, String name, Set<String> parameters) {
            List<MediaType> ranges = byName.get(name);
            if (ranges == null) {
                return false;
            }
            Set<String> keys = rejectedKeysByName.get(name);
            if (keys == null) {
                keys = new HashSet<>();
                for (MediaType range : ranges) {
                    keys.add(key(parameters(range)));
                }
                rejectedKeysByName.put(name, keys);
            }
            return keys.contains(key(parameters));
        }

        /**
         * @param byName The rejected types by name
         * @param type   A media type
         * @return Whether a rejected range might cover the type, by the type and subtype only
         */
        private static boolean mayBeRejected(Map<String, List<MediaType>> byName, MediaType type) {
            return byName.containsKey(name(type.getType(), type.getSubtype()))
                || byName.containsKey(name(type.getType(), WILDCARD))
                || byName.containsKey(name(WILDCARD, type.getSubtype()))
                || byName.containsKey(name(WILDCARD, WILDCARD));
        }

        /**
         * @param parameters The {@link AcceptedMediaTypes#parameters} of a range, sorted
         * @return A key that is equal for the same parameters, a string so that colliding keys
         * stay ordered in a hash set
         */
        private static String key(Set<String> parameters) {
            if (parameters.isEmpty()) {
                return "";
            }
            var key = new StringBuilder();
            for (String parameter : parameters) {
                key.append(parameter.length()).append(':').append(parameter);
            }
            return key.toString();
        }

        /**
         * @param type           A media type
         * @param typeParameters The {@link #typeParameters} of the type
         * @return Whether the type repeats a parameter under names that differ in case with
         * different values: it does not cover itself, being compared by the first value
         */
        private static boolean repeatsWithAnotherValue(MediaType type, Set<String> typeParameters) {
            int count = 0;
            for (CharSequence name : type.getParametersMap().keySet()) {
                if (!QUALITY.equalsIgnoreCase(name.toString())) {
                    count++;
                }
            }
            return count > typeParameters.size() && parameters(type).size() > typeParameters.size();
        }

        /**
         * @param type    The type of a range
         * @param subtype The subtype of the range
         * @return The name of the range, in one case
         */
        private static String name(String type, String subtype) {
            return foldCase(type) + '/' + foldCase(subtype);
        }
    }

    /**
     * The types of a result, each once by {@link MediaType#equals}.
     */
    private static final class Result {

        private final List<MediaType> types = new ArrayList<>(2);
        /**
         * The types by their name in one case, as {@link MediaType#equals} compares it: a string,
         * so that colliding names stay ordered in a hash map.
         */
        private final Map<String, List<MediaType>> byName = new HashMap<>();

        void add(MediaType type) {
            List<MediaType> sameName = byName.computeIfAbsent(type.getName().toLowerCase(Locale.ROOT), k -> new ArrayList<>(1));
            // MediaType.equals also compares the class
            if (!sameName.contains(type)) {
                sameName.add(type);
                types.add(type);
            }
        }
    }
}
