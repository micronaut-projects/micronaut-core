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

import io.micronaut.http.MediaType;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The negotiation of {@link AcceptedMediaTypes} gives the results of a direct evaluation of the
 * precedence of the accepted media ranges, in time linear in the number of accepted ranges.
 */
class AcceptedMediaTypesTest {

    private static final String[] TYPES = {"text", "TEXT", "application", "*"};
    private static final String[] SUBTYPES = {"plain", "Plain", "json", "html", "*"};
    private static final String[] PARAMETER_NAMES = {"charset", "format", "level"};
    private static final String[] PARAMETER_VALUES = {"utf-8", "UTF-8", "a", "A", "\"a\""};
    private static final String[] QUALITIES = {"", "", ";q=0", ";Q=0", ";q=0.5", ";q=1"};

    @Test
    void negotiationMatchesTheDirectEvaluation() {
        Random random = new Random(42);
        for (int i = 0; i < 20_000; i++) {
            List<MediaType> accepted = new ArrayList<>();
            int acceptedCount = random.nextInt(7);
            for (int j = 0; j < acceptedCount; j++) {
                accepted.add(randomType(random, true));
            }
            accepted = MediaType.orderedOf(accepted.stream().map(MediaType::toString).toList());
            List<MediaType> produces = new ArrayList<>();
            int producesCount = random.nextInt(4);
            for (int j = 0; j < producesCount; j++) {
                produces.add(random.nextInt(5) == 0 ? MediaType.ALL_TYPE : randomConcreteType(random));
            }
            boolean producesAll = random.nextBoolean();
            String context = "produces " + produces + " (all: " + producesAll + "), accepted " + accepted;

            assertEquals(describe(DirectEvaluation.negotiate(produces, producesAll, accepted)),
                describe(AcceptedMediaTypes.negotiate(produces, producesAll, accepted)), context);
            assertEquals(DirectEvaluation.negotiateAll(produces, producesAll, accepted).stream().map(AcceptedMediaTypesTest::describe).toList(),
                AcceptedMediaTypes.negotiateAll(produces, producesAll, accepted).stream().map(AcceptedMediaTypesTest::describe).toList(), context);
            MediaType type = randomType(random, false);
            assertEquals(DirectEvaluation.intersects(type, accepted), AcceptedMediaTypes.intersects(type, accepted), type + " " + context);
            MediaType concrete = randomConcreteType(random);
            assertEquals(DirectEvaluation.isAcceptable(concrete, accepted), AcceptedMediaTypes.isAcceptable(concrete, accepted), concrete + " " + context);
        }
    }

    @Test
    void negotiationIsLinearInTheNumberOfAcceptedRanges() {
        // with the quadratic evaluation each of these takes many seconds
        int count = 20_000;
        List<MediaType> sameRanges = new ArrayList<>();
        sameRanges.add(MediaType.of("application/json;q=0"));
        for (int i = 0; i < count; i++) {
            sameRanges.add(MediaType.ALL_TYPE);
        }
        // every concrete accepted type is rejected by a range of the same precedence
        List<MediaType> rejectedRanges = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rejectedRanges.add(MediaType.of("text/plain;level=" + i));
        }
        for (int i = 0; i < count; i++) {
            rejectedRanges.add(MediaType.of("text/plain;level=" + i + ";q=0"));
        }
        rejectedRanges.add(MediaType.of("application/json;q=0"));
        // accepted types that do not cover themselves, rejected by a less specific range
        List<MediaType> repeatedParameters = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            repeatedParameters.add(MediaType.of("text/plain;a=1;A=2;level=" + i));
        }
        repeatedParameters.add(MediaType.of("text/plain;a=1;q=0"));
        List<MediaType> json = List.of(MediaType.APPLICATION_JSON_TYPE);

        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            assertNull(AcceptedMediaTypes.negotiate(json, false, sameRanges));
            assertTrue(AcceptedMediaTypes.negotiateAll(json, false, sameRanges).isEmpty());
            assertNull(AcceptedMediaTypes.negotiate(json, true, rejectedRanges));
            assertTrue(AcceptedMediaTypes.negotiateAll(json, true, rejectedRanges).isEmpty());
            assertFalse(AcceptedMediaTypes.intersects(MediaType.of("text/*"), rejectedRanges));
            assertFalse(AcceptedMediaTypes.intersects(MediaType.APPLICATION_JSON_TYPE, rejectedRanges));
            assertNull(AcceptedMediaTypes.negotiate(json, true, repeatedParameters));
            assertTrue(AcceptedMediaTypes.negotiateAll(json, true, repeatedParameters).isEmpty());
            assertFalse(AcceptedMediaTypes.intersects(MediaType.of("text/*"), repeatedParameters));
        });
    }

    @Test
    void onlyAFewTypesWithAParameterRepeatedWithAnotherValueAreDecided() {
        List<String> types = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            types.add("text/plain;a=1;A=2;level=" + i);
        }
        for (int i = 0; i < 8; i++) {
            types.add("text/plain;level=" + i + ";q=0");
        }
        // the first 8 are rejected, the 9th is no longer decided
        assertNull(AcceptedMediaTypes.negotiate(List.of(), true, MediaType.orderedOf(types)));
        assertFalse(AcceptedMediaTypes.intersects(MediaType.of("text/*"), MediaType.orderedOf(types)));
        types.add("text/html");
        assertEquals("text/html", AcceptedMediaTypes.negotiate(List.of(), true, MediaType.orderedOf(types)).getName());
        types.remove(0);
        assertEquals("text/plain", AcceptedMediaTypes.negotiate(List.of(), true, MediaType.orderedOf(types)).getName());

        // a rejection that cannot cover them leaves them acceptable
        List<String> unrelated = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            unrelated.add("text/p" + i + ";a=1;A=2");
        }
        unrelated.add("image/png;q=0");
        assertEquals(9, AcceptedMediaTypes.negotiateAll(List.of(), true, MediaType.orderedOf(unrelated)).size());

        // every check decides the same types, ranges included
        List<String> ranges = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            ranges.add("text/*;a=1;A=2;level=" + i);
        }
        ranges.add("text/plain;b=1;B=2");
        ranges.add("text/*;a=1;q=0");
        List<MediaType> inOrder = ranges.stream().map(MediaType::of).toList();
        assertNull(AcceptedMediaTypes.negotiate(List.of(), true, inOrder));
        assertFalse(AcceptedMediaTypes.intersects(MediaType.ALL_TYPE, inOrder));
    }

    @Test
    void aParameterRepeatedInAnotherCaseCountsOnce() {
        MediaType produced = MediaType.of("text/plain;level=1");
        // the parameter names are case-insensitive: both ranges have the same specificity
        assertFalse(AcceptedMediaTypes.isAcceptable(produced, MediaType.orderedOf("text/plain;level=1;LEVEL=1", "text/plain;level=1;q=0")));
        assertTrue(AcceptedMediaTypes.isAcceptable(produced, MediaType.orderedOf("text/plain;level=1;LEVEL=1", "text/plain;q=0")));
    }

    @Test
    void aParameterRepeatedWithAnotherValueIsComparedByItsFirstValue() {
        // the accepted type does not cover itself, the rejection decides
        List<MediaType> accepted = MediaType.orderedOf("text/plain;a=1;A=2", "text/plain;a=1;q=0");
        assertNull(AcceptedMediaTypes.negotiate(List.of(), true, accepted));
        assertTrue(AcceptedMediaTypes.negotiateAll(List.of(), true, accepted).isEmpty());
        assertFalse(AcceptedMediaTypes.intersects(MediaType.of("text/*"), accepted));
        assertEquals("text/plain", AcceptedMediaTypes.negotiate(List.of(), true, MediaType.orderedOf("text/plain;a=1;A=2", "text/plain;a=2;q=0")).getName());

        MediaType produced = MediaType.of("text/plain;a=1;A=2");
        assertTrue(AcceptedMediaTypes.isAcceptable(produced, MediaType.orderedOf("text/plain;a=2;q=0", "*/*")));
        assertFalse(AcceptedMediaTypes.isAcceptable(produced, MediaType.orderedOf("text/plain;a=1;q=0", "*/*")));
        assertNull(AcceptedMediaTypes.negotiate(List.of(produced), false, MediaType.orderedOf("text/plain;a=1;A=2", "*/*;q=0")));
    }

    private static MediaType randomType(Random random, boolean quality) {
        String name = TYPES[random.nextInt(TYPES.length)] + "/" + SUBTYPES[random.nextInt(SUBTYPES.length)];
        return MediaType.of(name + randomParameters(random) + (quality ? QUALITIES[random.nextInt(QUALITIES.length)] : ""));
    }

    private static MediaType randomConcreteType(Random random) {
        String name = TYPES[random.nextInt(TYPES.length - 1)] + "/" + SUBTYPES[random.nextInt(SUBTYPES.length - 1)];
        return MediaType.of(name + randomParameters(random));
    }

    private static String randomParameters(Random random) {
        var parameters = new StringBuilder();
        for (String name : PARAMETER_NAMES) {
            if (random.nextInt(3) == 0) {
                String spelled = random.nextBoolean() ? name : name.toUpperCase(Locale.ROOT);
                parameters.append(';').append(spelled).append('=').append(PARAMETER_VALUES[random.nextInt(PARAMETER_VALUES.length)]);
                if (random.nextInt(4) == 0) {
                    // the same parameter in another case with another value
                    String other = spelled.equals(name) ? name.toUpperCase(Locale.ROOT) : name;
                    parameters.append(';').append(other).append("=b");
                }
            }
        }
        return parameters.toString();
    }

    private static @Nullable String describe(@Nullable MediaType type) {
        return type == null ? null : type.getName() + type.getParametersMap();
    }

    /**
     * The direct evaluation: the deciding range is searched among all accepted ranges for every
     * candidate type.
     */
    private static final class DirectEvaluation {

        private static final String WILDCARD = "*";
        private static final String QUALITY = "q";
        private static final String CHARSET = "charset";

        static @Nullable MediaType negotiate(List<MediaType> produces, boolean producesAll, @Nullable Collection<MediaType> accepted) {
            List<MediaType> all = negotiateAll(produces, producesAll, accepted);
            return all.isEmpty() ? null : all.get(0);
        }

        static List<MediaType> negotiateAll(List<MediaType> produces, boolean producesAll, @Nullable Collection<MediaType> accepted) {
            if (accepted == null || accepted.isEmpty()) {
                return List.of(produces.isEmpty() ? MediaType.APPLICATION_JSON_TYPE : concrete(produces.get(0)));
            }
            var result = new ArrayList<MediaType>();
            for (MediaType range : accepted) {
                if (isRejected(range)) {
                    continue;
                }
                for (MediaType type : produces) {
                    MediaType concrete = concrete(type);
                    if (covers(range, concrete) && isAcceptable(concrete, accepted) && !result.contains(concrete)) {
                        result.add(concrete);
                    }
                }
            }
            if (producesAll) {
                for (MediaType range : accepted) {
                    if (!isRejected(range) && !WILDCARD.equals(range.getType()) && !WILDCARD.equals(range.getSubtype())
                        && isAcceptable(range, accepted)) {
                        MediaType type = withoutQuality(range);
                        if (!result.contains(type)) {
                            result.add(type);
                        }
                    }
                }
            }
            return result;
        }

        static boolean intersects(MediaType type, Collection<MediaType> accepted) {
            if (accepted.isEmpty()) {
                return true;
            }
            boolean range = WILDCARD.equals(type.getType()) || WILDCARD.equals(type.getSubtype());
            for (MediaType acceptedType : accepted) {
                MediaType candidate = null;
                if (!isRejected(acceptedType) && covers(acceptedType, type)) {
                    candidate = type;
                } else if (!isRejected(acceptedType) && range && type.matches(acceptedType)) {
                    candidate = acceptedType;
                }
                if (candidate != null && isAcceptable(candidate, accepted)) {
                    return true;
                }
            }
            return false;
        }

        static boolean isAcceptable(MediaType produced, @Nullable Collection<MediaType> accepted) {
            if (accepted == null || accepted.isEmpty()) {
                return true;
            }
            MediaType best = null;
            int bestSpecificity = -1;
            for (MediaType range : accepted) {
                if (covers(range, produced)) {
                    int specificity = specificity(range);
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

        private static boolean isRejected(MediaType type) {
            return type.getQualityAsNumber().signum() == 0;
        }

        private static MediaType concrete(MediaType type) {
            return MediaType.ALL_TYPE.equals(type) ? MediaType.APPLICATION_JSON_TYPE : type;
        }

        private static MediaType withoutQuality(MediaType type) {
            var withoutQuality = new LinkedHashMap<String, String>();
            for (Map.Entry<CharSequence, String> parameter : type.getParametersMap().entrySet()) {
                if (!QUALITY.equalsIgnoreCase(parameter.getKey().toString())) {
                    withoutQuality.put(parameter.getKey().toString(), parameter.getValue());
                }
            }
            return withoutQuality.size() == type.getParametersMap().size() ? type : new MediaType(type.getName(), withoutQuality);
        }

        private static boolean covers(MediaType range, MediaType type) {
            if (!range.matches(type)) {
                return false;
            }
            for (Map.Entry<CharSequence, String> parameter : range.getParametersMap().entrySet()) {
                String name = parameter.getKey().toString();
                if (QUALITY.equalsIgnoreCase(name)) {
                    continue;
                }
                String value = null;
                for (Map.Entry<CharSequence, String> typeParameter : type.getParametersMap().entrySet()) {
                    if (name.equalsIgnoreCase(typeParameter.getKey().toString())) {
                        value = typeParameter.getValue();
                        break;
                    }
                }
                boolean same = value != null && (CHARSET.equalsIgnoreCase(name) ? value.equalsIgnoreCase(parameter.getValue()) : Objects.equals(value, parameter.getValue()));
                if (!same) {
                    return false;
                }
            }
            return true;
        }

        private static int specificity(MediaType range) {
            int level = WILDCARD.equals(range.getType()) ? 0 : WILDCARD.equals(range.getSubtype()) ? 1 : 2;
            int parameters = 0;
            for (CharSequence name : range.getParametersMap().keySet()) {
                if (!QUALITY.equalsIgnoreCase(name.toString())) {
                    parameters++;
                }
            }
            return level * 1000 + parameters;
        }
    }
}
