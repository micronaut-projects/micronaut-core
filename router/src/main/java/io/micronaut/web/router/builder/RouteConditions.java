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
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.RouteConditionContext;
import io.micronaut.web.router.builder.RouteCondition.AllOf;
import io.micronaut.web.router.builder.RouteCondition.AnyOf;
import io.micronaut.web.router.builder.RouteCondition.Custom;
import io.micronaut.web.router.builder.RouteCondition.Header;
import io.micronaut.web.router.builder.RouteCondition.Host;
import io.micronaut.web.router.builder.RouteCondition.Method;
import io.micronaut.web.router.builder.RouteCondition.Not;
import io.micronaut.web.router.builder.RouteCondition.Query;
import io.micronaut.web.router.builder.RouteCondition.PeerAddress;
import io.micronaut.web.router.builder.RouteCondition.RemoteAddress;
import io.micronaut.web.router.builder.RouteCondition.TimeWindow;
import org.jspecify.annotations.Nullable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * Normalizes and evaluates {@link RouteCondition route conditions} and {@link ValueMatcher value
 * matchers}.
 *
 * <p>A condition is normalized when its route is built: nested {@link AllOf} and {@link AnyOf}
 * are flattened, a double negation and a duplicate are removed, a combination that is always or
 * never met is folded, and the parts of a combination are ordered cheapest first, stably: the
 * methods, then the lookups of a value ({@link ValueMatcher.Equals}, {@link ValueMatcher.OneOf},
 * {@link ValueMatcher.Present}) and the time, then the affixes ({@link ValueMatcher.StartsWith},
 * {@link ValueMatcher.EndsWith}, {@link ValueMatcher.Contains}), then the regular expressions and
 * the host and the client and peer addresses, then the {@link Custom} conditions,
 * in the order they were declared. The evaluation stops at the first part that decides.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class RouteConditions {

    /**
     * The condition every request meets.
     */
    public static final RouteCondition ALWAYS = new AllOf(List.of());
    /**
     * The condition no request meets.
     */
    public static final RouteCondition NEVER = new AnyOf(List.of());

    private static final int COST_METHOD = 0;
    private static final int COST_LOOKUP = 1;
    private static final int COST_AFFIX = 2;
    private static final int COST_REGEX = 3;
    private static final int COST_RESOLVED = 3;
    private static final int COST_CUSTOM = 4;

    private static final Comparator<RouteCondition> BY_COST = Comparator.comparingInt(RouteConditions::cost);
    private static final Comparator<ValueMatcher> MATCHERS_BY_COST = Comparator.comparingInt(RouteConditions::cost);

    private RouteConditions() {
    }

    /**
     * The conditions of a route: the conditions every request of the route must meet, flattened
     * and ordered, see {@link #normalize(RouteCondition)}.
     *
     * @param conditions The conditions, of the groups, outer group first, then of the route
     * @return The normalized condition, {@link #ALWAYS} if there is none
     */
    public static RouteCondition normalizeAll(List<RouteCondition> conditions) {
        if (conditions.isEmpty()) {
            return ALWAYS;
        }
        return normalize(new AllOf(conditions));
    }

    /**
     * @param condition A condition
     * @return The parts of the condition that must all be met, in the order they are evaluated
     */
    public static List<RouteCondition> conjuncts(RouteCondition condition) {
        return condition instanceof AllOf allOf ? allOf.conditions() : List.of(condition);
    }

    /**
     * Normalize a condition: flatten, fold, remove double negations and duplicates, and order
     * the parts of a combination cheapest first.
     *
     * @param condition The condition
     * @return The normalized condition
     */
    public static RouteCondition normalize(RouteCondition condition) {
        return switch (condition) {
            case AllOf allOf -> normalizeAll(allOf);
            case AnyOf anyOf -> normalizeAny(anyOf);
            case Not not -> {
                RouteCondition inner = normalize(not.condition());
                if (inner instanceof Not doubled) {
                    yield doubled.condition();
                }
                if (inner.equals(ALWAYS)) {
                    yield NEVER;
                }
                if (inner.equals(NEVER)) {
                    yield ALWAYS;
                }
                yield new Not(inner);
            }
            case Header header -> new Header(header.name(), normalize(header.value()));
            case Query query -> new Query(query.name(), normalize(query.value()));
            case RouteCondition.Cookie cookie -> new RouteCondition.Cookie(cookie.name(), normalize(cookie.value()));
            case Host host -> new Host(normalize(host.host()));
            case Method method -> method;
            case RemoteAddress remoteAddress -> remoteAddress;
            case PeerAddress peerAddress -> peerAddress;
            case TimeWindow timeWindow -> timeWindow;
            case Custom custom -> custom.predicate() instanceof RouteCondition wrapped ? normalize(wrapped) : custom;
        };
    }

    private static RouteCondition normalizeAll(AllOf allOf) {
        LinkedHashSet<RouteCondition> parts = new LinkedHashSet<>();
        for (RouteCondition part : allOf.conditions()) {
            RouteCondition normalized = normalize(part);
            if (normalized.equals(NEVER)) {
                return NEVER;
            }
            if (normalized instanceof AllOf nested) {
                // normalized: already flat, ALWAYS adds nothing
                parts.addAll(nested.conditions());
            } else {
                parts.add(normalized);
            }
        }
        return combination(parts, true);
    }

    private static RouteCondition normalizeAny(AnyOf anyOf) {
        LinkedHashSet<RouteCondition> parts = new LinkedHashSet<>();
        for (RouteCondition part : anyOf.conditions()) {
            RouteCondition normalized = normalize(part);
            if (normalized.equals(ALWAYS)) {
                return ALWAYS;
            }
            if (normalized instanceof AnyOf nested) {
                // normalized: already flat, NEVER adds nothing
                parts.addAll(nested.conditions());
            } else {
                parts.add(normalized);
            }
        }
        return combination(parts, false);
    }

    private static RouteCondition combination(LinkedHashSet<RouteCondition> parts, boolean all) {
        if (parts.size() == 1) {
            return parts.iterator().next();
        }
        List<RouteCondition> ordered = new ArrayList<>(parts);
        // a stable sort: the parts of the same cost keep the order they were declared in
        ordered.sort(BY_COST);
        return all ? new AllOf(ordered) : new AnyOf(ordered);
    }

    /**
     * Normalize a matcher: flatten, remove double negations and duplicates, and order the parts
     * of a combination cheapest first.
     *
     * @param matcher The matcher
     * @return The normalized matcher
     */
    public static ValueMatcher normalize(ValueMatcher matcher) {
        return switch (matcher) {
            case ValueMatcher.Not not -> {
                ValueMatcher inner = normalize(not.matcher());
                yield inner instanceof ValueMatcher.Not doubled ? doubled.matcher() : new ValueMatcher.Not(inner);
            }
            case ValueMatcher.AllOf allOf -> {
                LinkedHashSet<ValueMatcher> parts = new LinkedHashSet<>();
                for (ValueMatcher part : allOf.matchers()) {
                    ValueMatcher normalized = normalize(part);
                    if (normalized instanceof ValueMatcher.AllOf nested) {
                        parts.addAll(nested.matchers());
                    } else {
                        parts.add(normalized);
                    }
                }
                yield parts.size() == 1 ? parts.iterator().next() : new ValueMatcher.AllOf(orderedMatchers(parts));
            }
            case ValueMatcher.AnyOf anyOf -> {
                LinkedHashSet<ValueMatcher> parts = new LinkedHashSet<>();
                for (ValueMatcher part : anyOf.matchers()) {
                    ValueMatcher normalized = normalize(part);
                    if (normalized instanceof ValueMatcher.AnyOf nested) {
                        parts.addAll(nested.matchers());
                    } else {
                        parts.add(normalized);
                    }
                }
                yield parts.size() == 1 ? parts.iterator().next() : new ValueMatcher.AnyOf(orderedMatchers(parts));
            }
            default -> matcher;
        };
    }

    private static List<ValueMatcher> orderedMatchers(LinkedHashSet<ValueMatcher> parts) {
        List<ValueMatcher> ordered = new ArrayList<>(parts);
        ordered.sort(MATCHERS_BY_COST);
        return ordered;
    }

    /**
     * @param condition A condition
     * @return Its cost: a cheaper condition is evaluated first
     */
    static int cost(RouteCondition condition) {
        return switch (condition) {
            case Method method -> COST_METHOD;
            case TimeWindow timeWindow -> COST_LOOKUP;
            case Header header -> cost(header.value());
            case Query query -> cost(query.value());
            case RouteCondition.Cookie cookie -> cost(cookie.value());
            case Host host -> Math.max(COST_RESOLVED, cost(host.host()));
            case RemoteAddress remoteAddress -> COST_RESOLVED;
            case PeerAddress peerAddress -> COST_RESOLVED;
            case Not not -> cost(not.condition());
            case AllOf allOf -> maxCost(allOf.conditions());
            case AnyOf anyOf -> maxCost(anyOf.conditions());
            case Custom custom -> COST_CUSTOM;
        };
    }

    private static int maxCost(List<RouteCondition> conditions) {
        int max = COST_METHOD;
        for (RouteCondition condition : conditions) {
            max = Math.max(max, cost(condition));
        }
        return max;
    }

    /**
     * @param matcher A matcher
     * @return Its cost: a cheaper matcher is evaluated first
     */
    static int cost(ValueMatcher matcher) {
        return switch (matcher) {
            case ValueMatcher.Equals equals -> COST_LOOKUP;
            case ValueMatcher.OneOf oneOf -> COST_LOOKUP;
            case ValueMatcher.Present present -> COST_LOOKUP;
            case ValueMatcher.StartsWith startsWith -> COST_AFFIX;
            case ValueMatcher.EndsWith endsWith -> COST_AFFIX;
            case ValueMatcher.Contains contains -> COST_AFFIX;
            case ValueMatcher.Regex regex -> COST_REGEX;
            case ValueMatcher.Not not -> cost(not.matcher());
            case ValueMatcher.AllOf allOf -> maxMatcherCost(allOf.matchers());
            case ValueMatcher.AnyOf anyOf -> maxMatcherCost(anyOf.matchers());
        };
    }

    private static int maxMatcherCost(List<ValueMatcher> matchers) {
        int max = COST_LOOKUP;
        for (ValueMatcher matcher : matchers) {
            max = Math.max(max, cost(matcher));
        }
        return max;
    }

    /**
     * A predicate of a route that evaluates a normalized condition with the context of the
     * server.
     *
     * @param condition The normalized condition
     * @param context   The context
     * @return The predicate
     */
    public static RoutePredicate predicate(RouteCondition condition, RouteConditionContext context) {
        return new RoutePredicate(condition, context);
    }

    /**
     * Whether a request meets a condition.
     *
     * @param condition The condition
     * @param request   The request
     * @param context   What the server resolves for the request
     * @return Whether it meets the condition
     */
    public static boolean matches(RouteCondition condition, HttpRequest<?> request, RouteConditionContext context) {
        return switch (condition) {
            case Method method -> method(method, request);
            case Header header -> anyValue(header.value(), request.getHeaders().getAll(header.name()));
            case Query query -> anyValue(query.value(), request.getParameters().getAll(query.name()));
            case RouteCondition.Cookie cookie -> cookie(cookie, request);
            case Host host -> host(host.host(), context.host(request));
            case RemoteAddress remoteAddress -> inRanges(remoteAddress.ranges(), Cidr.address(context.clientAddress(request)));
            case PeerAddress peerAddress -> peerAddress(peerAddress, request);
            case TimeWindow timeWindow -> timeWindow(timeWindow, context.clock().instant());
            case AllOf allOf -> {
                for (RouteCondition part : allOf.conditions()) {
                    if (!matches(part, request, context)) {
                        yield false;
                    }
                }
                yield true;
            }
            case AnyOf anyOf -> {
                for (RouteCondition part : anyOf.conditions()) {
                    if (matches(part, request, context)) {
                        yield true;
                    }
                }
                yield false;
            }
            case Not not -> !matches(not.condition(), request, context);
            case Custom custom -> custom.predicate().test(request);
        };
    }

    private static boolean method(Method method, HttpRequest<?> request) {
        return method.methods().contains(request.getMethodName())
            || request.getMethod() == HttpMethod.CUSTOM && method.methods().contains(HttpMethod.CUSTOM.name());
    }

    private static boolean anyValue(ValueMatcher matcher, List<String> values) {
        if (values.isEmpty()) {
            return matches(matcher, null);
        }
        for (String value : values) {
            if (matches(matcher, value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Scan the {@code Cookie} headers for the cookies of the name: the other cookies are not
     * decoded. A request without a {@code Cookie} header, e.g. one built in code, is read from
     * its cookies.
     */
    private static boolean cookie(RouteCondition.Cookie cookie, HttpRequest<?> request) {
        List<String> headers = request.getHeaders().getAll(HttpHeaders.COOKIE);
        String name = cookie.name();
        ValueMatcher matcher = cookie.value();
        if (headers.isEmpty()) {
            io.micronaut.http.cookie.Cookie found = request.getCookies().findCookie(name).orElse(null);
            return matches(matcher, found == null ? null : found.getValue());
        }
        boolean found = false;
        for (String header : headers) {
            int length = header.length();
            int start = 0;
            while (start < length) {
                int end = header.indexOf(';', start);
                if (end < 0) {
                    end = length;
                }
                int nameStart = skipSpaces(header, start, end);
                int equals = header.indexOf('=', nameStart);
                int nameEnd = equals < 0 || equals > end ? end : equals;
                nameEnd = trimEnd(header, nameStart, nameEnd);
                if (nameEnd - nameStart == name.length() && header.startsWith(name, nameStart)) {
                    found = true;
                    int valueStart = equals < 0 || equals > end ? end : skipSpaces(header, equals + 1, end);
                    int valueEnd = trimEnd(header, valueStart, end);
                    if (valueEnd - valueStart >= 2 && header.charAt(valueStart) == '"' && header.charAt(valueEnd - 1) == '"') {
                        valueStart++;
                        valueEnd--;
                    }
                    if (matches(matcher, header, valueStart, valueEnd)) {
                        return true;
                    }
                }
                start = end + 1;
            }
        }
        return !found && matches(matcher, null);
    }

    private static int skipSpaces(String value, int start, int end) {
        int i = start;
        while (i < end && (value.charAt(i) == ' ' || value.charAt(i) == '\t')) {
            i++;
        }
        return i;
    }

    private static int trimEnd(String value, int start, int end) {
        int i = end;
        while (i > start && (value.charAt(i - 1) == ' ' || value.charAt(i - 1) == '\t')) {
            i--;
        }
        return i;
    }

    /**
     * Match the host of a resolved value, a host, a host and a port, or a URI with a scheme,
     * without the scheme, the port and a final dot, in place.
     */
    private static boolean host(ValueMatcher matcher, @Nullable String resolved) {
        if (resolved == null) {
            return matches(matcher, null);
        }
        int start = skipSpaces(resolved, 0, resolved.length());
        int end = trimEnd(resolved, start, resolved.length());
        int scheme = resolved.indexOf("://", start);
        if (scheme >= 0 && scheme < end) {
            start = scheme + 3;
            int path = resolved.indexOf('/', start);
            if (path >= 0 && path < end) {
                end = path;
            }
        }
        if (start < end && resolved.charAt(start) == '[') {
            int close = resolved.indexOf(']', start);
            if (close >= 0 && close < end) {
                end = close + 1;
            }
        } else {
            int colon = resolved.indexOf(':', start);
            if (colon >= 0 && colon < end && resolved.lastIndexOf(':', end - 1) == colon) {
                // a host and a port: an IPv6 address without brackets has more than one colon
                end = colon;
            }
        }
        if (end > start && resolved.charAt(end - 1) == '.') {
            end--;
        }
        return end > start ? matches(matcher, resolved, start, end) : matches(matcher, null);
    }

    /**
     * The peer of the connection only: never a resolver, nor a forwarded header.
     */
    private static boolean peerAddress(PeerAddress peerAddress, HttpRequest<?> request) {
        InetSocketAddress remote = request.getRemoteAddress();
        InetAddress address = remote == null ? null : remote.getAddress();
        return address != null && inRanges(peerAddress.ranges(), Cidr.address(address));
    }

    private static boolean inRanges(List<Cidr> ranges, byte @Nullable [] address) {
        if (address == null) {
            return false;
        }
        for (Cidr range : ranges) {
            if (range.contains(address)) {
                return true;
            }
        }
        return false;
    }

    private static boolean timeWindow(TimeWindow timeWindow, Instant now) {
        Instant after = timeWindow.after();
        Instant before = timeWindow.before();
        return (after == null || !now.isBefore(after)) && (before == null || now.isBefore(before));
    }

    /**
     * Whether a matcher matches a value.
     *
     * @param matcher The matcher
     * @param value   The value, or {@code null} if it is absent
     * @return Whether it matches
     */
    public static boolean matches(ValueMatcher matcher, @Nullable CharSequence value) {
        if (value == null) {
            return matchesAbsent(matcher);
        }
        return matches(matcher, value, 0, value.length());
    }

    private static boolean matchesAbsent(ValueMatcher matcher) {
        return switch (matcher) {
            case ValueMatcher.Not not -> !matchesAbsent(not.matcher());
            case ValueMatcher.AllOf allOf -> {
                for (ValueMatcher part : allOf.matchers()) {
                    if (!matchesAbsent(part)) {
                        yield false;
                    }
                }
                yield true;
            }
            case ValueMatcher.AnyOf anyOf -> {
                for (ValueMatcher part : anyOf.matchers()) {
                    if (matchesAbsent(part)) {
                        yield true;
                    }
                }
                yield false;
            }
            default -> false;
        };
    }

    /**
     * Whether a matcher matches a region of a value, compared in place.
     *
     * @param matcher The matcher
     * @param value   The value
     * @param from    The start of the region, included
     * @param to      The end of the region, excluded
     * @return Whether it matches
     */
    public static boolean matches(ValueMatcher matcher, CharSequence value, int from, int to) {
        int length = to - from;
        return switch (matcher) {
            case ValueMatcher.Equals equals -> equals.value().length() == length
                && regionMatches(value, from, equals.value(), equals.ignoreCase());
            case ValueMatcher.StartsWith startsWith -> startsWith.prefix().length() <= length
                && regionMatches(value, from, startsWith.prefix(), startsWith.ignoreCase());
            case ValueMatcher.EndsWith endsWith -> endsWith.suffix().length() <= length
                && regionMatches(value, to - endsWith.suffix().length(), endsWith.suffix(), endsWith.ignoreCase());
            case ValueMatcher.Contains contains -> contains(value, from, to, contains.part(), contains.ignoreCase());
            case ValueMatcher.OneOf oneOf -> oneOf(oneOf, value, from, to);
            case ValueMatcher.Regex regex -> regex.pattern().matcher(value).region(from, to).matches();
            case ValueMatcher.Present present -> true;
            case ValueMatcher.Not not -> !matches(not.matcher(), value, from, to);
            case ValueMatcher.AllOf allOf -> {
                for (ValueMatcher part : allOf.matchers()) {
                    if (!matches(part, value, from, to)) {
                        yield false;
                    }
                }
                yield true;
            }
            case ValueMatcher.AnyOf anyOf -> {
                for (ValueMatcher part : anyOf.matchers()) {
                    if (matches(part, value, from, to)) {
                        yield true;
                    }
                }
                yield false;
            }
        };
    }

    private static boolean contains(CharSequence value, int from, int to, String part, boolean ignoreCase) {
        int last = to - part.length();
        for (int i = from; i <= last; i++) {
            if (regionMatches(value, i, part, ignoreCase)) {
                return true;
            }
        }
        return false;
    }

    private static boolean oneOf(ValueMatcher.OneOf oneOf, CharSequence value, int from, int to) {
        if (!oneOf.ignoreCase() && from == 0 && value instanceof String string && to == string.length()) {
            return oneOf.values().contains(string);
        }
        int length = to - from;
        for (String candidate : oneOf.values()) {
            if (candidate.length() == length && regionMatches(value, from, candidate, oneOf.ignoreCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return Whether the characters of the value at the offset are the other string, whose
     * length the caller checked
     */
    private static boolean regionMatches(CharSequence value, int offset, String other, boolean ignoreCase) {
        if (value instanceof String string) {
            return string.regionMatches(ignoreCase, offset, other, 0, other.length());
        }
        for (int i = 0; i < other.length(); i++) {
            char a = value.charAt(offset + i);
            char b = other.charAt(i);
            if (a != b && (!ignoreCase || !equalsIgnoreCase(a, b))) {
                return false;
            }
        }
        return true;
    }

    private static boolean equalsIgnoreCase(char a, char b) {
        char upperA = Character.toUpperCase(a);
        char upperB = Character.toUpperCase(b);
        return upperA == upperB || Character.toLowerCase(upperA) == Character.toLowerCase(upperB);
    }

    /**
     * The predicate of a route that evaluates its normalized condition with the context of the
     * server.
     *
     * @param condition The normalized condition
     * @param context   The context
     */
    public record RoutePredicate(RouteCondition condition, RouteConditionContext context) implements Predicate<HttpRequest<?>> {
        @Override
        public boolean test(HttpRequest<?> request) {
            return matches(condition, request, context);
        }
    }
}
