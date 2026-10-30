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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A declarative condition on a request, for {@link RouteSpec#where(RequestCondition)}: on its
 * headers, query parameters, cookies, method, host, client and peer addresses and the time it arrives, and
 * their combinations. A condition is data: the router reads it, flattens and orders it
 * cheapest first when the route is built, and shows it in the route info, which a lambda does
 * not allow. {@link #custom(Predicate)} takes a lambda for what the others do not express.
 *
 * <pre>{@code
 * import static io.micronaut.web.router.builder.RequestCondition.*;
 * import static io.micronaut.web.router.builder.ValueMatcher.*;
 *
 * routes.GET("/search")
 *     .where(header("X-Beta").or(query("beta", equalTo("true"))))
 *     .order(-1)
 *     .handle(betaHandler);
 * routes.path("/admin", admin -> admin.where(peerAddress("10.0.0.0/8")));
 * }</pre>
 *
 * <p>A condition is data, not a predicate: only the router evaluates it, with the host and the
 * client address as the server resolves them, see {@link Host} and {@link RemoteAddress}, and
 * the clock of the application. Conditions combine with {@link #and(RequestCondition)},
 * {@link #or(RequestCondition)} and {@link #negate()}; a lambda is a {@link #custom(Predicate)}
 * condition.</p>
 *
 * <p>A {@link Body} condition, see {@link #body(Class, Predicate)}, is met by the body of the
 * request: the server reads the body, from a copy, for the routes the request matched without
 * it, and evaluates the body conditions last, see {@link Body}. A body condition is a part every
 * request of the route meets: a condition of a route or a group, or a part of
 * {@link #all(RequestCondition...)} or {@link #and(RequestCondition)}. It cannot be a part of
 * {@link #any(RequestCondition...)}, {@link #or(RequestCondition)} or {@link #negate()}, which fail
 * with an {@link IllegalArgumentException}.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface RequestCondition {

    /**
     * A request with a header of the name, e.g. {@code header("X-Beta")}.
     *
     * @param name The name of the header, case-insensitive
     * @return The condition
     */
    static RequestCondition header(String name) {
        return new Header(name, ValueMatcher.present());
    }

    /**
     * A request with a header of the name that has the value, one of its values if the header
     * is repeated.
     *
     * @param name  The name of the header, case-insensitive
     * @param value The value, case-sensitive
     * @return The condition
     */
    static RequestCondition header(String name, String value) {
        return new Header(name, ValueMatcher.equalTo(value));
    }

    /**
     * A request with a header of the name whose value the matcher matches, one of its values if
     * the header is repeated, e.g. {@code header("X-Channel", oneOf("beta", "canary"))}. A
     * request without the header is given to the matcher as an absent value.
     *
     * @param name  The name of the header, case-insensitive
     * @param value The matcher of the value
     * @return The condition
     */
    static RequestCondition header(String name, ValueMatcher value) {
        return new Header(name, value);
    }

    /**
     * A request with a query parameter of the name, with or without a value, e.g.
     * {@code query("debug")} for {@code ?debug}.
     *
     * @param name The name of the query parameter, case-sensitive
     * @return The condition
     */
    static RequestCondition query(String name) {
        return new Query(name, ValueMatcher.present());
    }

    /**
     * A request with a query parameter of the name that has the value, one of its values if the
     * parameter is repeated, e.g. {@code query("format", "csv")} for {@code ?format=csv}.
     *
     * @param name  The name of the query parameter, case-sensitive
     * @param value The decoded value, case-sensitive
     * @return The condition
     */
    static RequestCondition query(String name, String value) {
        return new Query(name, ValueMatcher.equalTo(value));
    }

    /**
     * A request with a query parameter of the name whose decoded value the matcher matches, one
     * of its values if the parameter is repeated.
     *
     * @param name  The name of the query parameter, case-sensitive
     * @param value The matcher of the value
     * @return The condition
     */
    static RequestCondition query(String name, ValueMatcher value) {
        return new Query(name, value);
    }

    /**
     * A request with a cookie of the name, e.g. {@code cookie("SESSION")}.
     *
     * @param name The name of the cookie, case-sensitive
     * @return The condition
     */
    static RequestCondition cookie(String name) {
        return new Cookie(name, ValueMatcher.present());
    }

    /**
     * A request with a cookie of the name whose value the matcher matches, e.g.
     * {@code cookie("variant", regex("[a-c]"))}.
     *
     * @param name  The name of the cookie, case-sensitive
     * @param value The matcher of the value, without the quotes of a quoted value
     * @return The condition
     */
    static RequestCondition cookie(String name, ValueMatcher value) {
        return new Cookie(name, value);
    }

    /**
     * A request of one of the HTTP methods, e.g. for the routes of a group:
     * {@code group.where(method(HttpMethod.GET).or(header("X-Write-Token")))}. A custom method
     * is {@link HttpMethod#CUSTOM}, which stands for every custom method.
     *
     * @param methods The methods, at least one
     * @return The condition
     */
    static RequestCondition method(HttpMethod... methods) {
        Objects.requireNonNull(methods, "methods");
        String[] names = new String[methods.length];
        for (int i = 0; i < methods.length; i++) {
            names[i] = Objects.requireNonNull(methods[i], "method").name();
        }
        return method(names);
    }

    /**
     * A request of one of the HTTP methods, by name, e.g. {@code method("GET", "PURGE")}.
     *
     * @param methods The names of the methods, case-sensitive, at least one
     * @return The condition
     */
    static RequestCondition method(String... methods) {
        Objects.requireNonNull(methods, "methods");
        if (methods.length == 0) {
            throw new IllegalArgumentException("A method is required");
        }
        return new Method(Set.copyOf(Arrays.asList(methods)));
    }

    /**
     * A request to a host, e.g. {@code host(endsWith(".example.com"))}: the host of the request
     * as the server resolves it, without the port and a final dot, compared ignoring case. See
     * {@link Host}.
     *
     * @param host The matcher of the host, made to ignore case
     * @return The condition
     */
    static RequestCondition host(ValueMatcher host) {
        return new Host(host);
    }

    /**
     * A request to one of the hosts, e.g. {@code host("api.example.com", "localhost")}, compared
     * ignoring case. An IPv6 address is written in brackets, e.g. {@code [::1]}.
     *
     * @param hosts The hosts, at least one, without a port
     * @return The condition
     */
    static RequestCondition host(String... hosts) {
        Objects.requireNonNull(hosts, "hosts");
        return new Host(hosts.length == 1 ? ValueMatcher.equalTo(hosts[0]) : ValueMatcher.oneOf(hosts));
    }

    /**
     * A request from a client address in one of the ranges, IPv4 or IPv6 in CIDR notation, e.g.
     * {@code remoteAddress("10.0.0.0/8", "192.168.1.7", "fd00::/8")}: the address of the client
     * as the server resolves it, see {@link RemoteAddress}. An address without a prefix length is
     * the range of that address alone. No name is ever looked up.
     *
     * <p>The server resolves the address with its {@code HttpClientAddressResolver}, which trusts
     * the {@code X-Forwarded-For} and {@code Forwarded} headers of the request unless
     * {@code micronaut.server.client-address-header} is configured: a client can then send any
     * address. Use it behind a trusted proxy that sets the configured header, and
     * {@link #peerAddress(String...)} for access control.</p>
     *
     * @param cidrs The ranges of addresses, at least one
     * @return The condition
     * @throws IllegalArgumentException if a range is not a range of addresses
     */
    static RequestCondition remoteAddress(String... cidrs) {
        return new RemoteAddress(ranges(cidrs));
    }

    /**
     * A request whose connection comes from an address in one of the ranges, IPv4 or IPv6 in
     * CIDR notation, e.g. {@code peerAddress("10.0.0.0/8", "::1")}: the address of the peer of
     * the connection, {@link HttpRequest#getRemoteAddress()}, never a forwarded address, so a
     * client cannot spoof it with a header. This is the condition for access control, e.g. an
     * allow or deny list. Behind a proxy the peer is the proxy: see
     * {@link #remoteAddress(String...)} for the address of the client the proxy forwards. A
     * request without a peer address, or whose peer address is unresolved, does not meet the
     * condition. No name is ever looked up.
     *
     * @param cidrs The ranges of addresses, at least one
     * @return The condition
     * @throws IllegalArgumentException if a range is not a range of addresses
     */
    static RequestCondition peerAddress(String... cidrs) {
        return new PeerAddress(ranges(cidrs));
    }

    private static List<Cidr> ranges(String[] cidrs) {
        Objects.requireNonNull(cidrs, "cidrs");
        List<Cidr> ranges = new ArrayList<>(cidrs.length);
        for (String cidr : cidrs) {
            ranges.add(Cidr.parse(cidr));
        }
        return ranges;
    }

    /**
     * A request that arrives before an instant, e.g. until a campaign ends.
     *
     * @param instant The instant, excluded
     * @return The condition
     */
    static RequestCondition before(Instant instant) {
        return new TimeWindow(null, Objects.requireNonNull(instant, "instant"));
    }

    /**
     * A request that arrives at or after an instant, e.g. once a release goes live.
     *
     * @param instant The instant, included
     * @return The condition
     */
    static RequestCondition after(Instant instant) {
        return new TimeWindow(Objects.requireNonNull(instant, "instant"), null);
    }

    /**
     * A request that arrives at or after the start and before the end.
     *
     * @param start The start, included
     * @param end   The end, excluded
     * @return The condition
     * @throws IllegalArgumentException if the end is before the start
     */
    static RequestCondition between(Instant start, Instant end) {
        return new TimeWindow(Objects.requireNonNull(start, "start"), Objects.requireNonNull(end, "end"));
    }

    /**
     * A request that meets every condition.
     *
     * @param conditions The conditions
     * @return The condition, met by every request if there are none
     */
    static RequestCondition all(RequestCondition... conditions) {
        return new AllOf(List.of(conditions));
    }

    /**
     * A request that meets one of the conditions.
     *
     * @param conditions The conditions
     * @return The condition, met by no request if there are none
     */
    static RequestCondition any(RequestCondition... conditions) {
        return new AnyOf(List.of(conditions));
    }

    /**
     * A request that does not meet the condition.
     *
     * @param condition The condition
     * @return The condition
     */
    static RequestCondition not(RequestCondition condition) {
        return condition.negate();
    }

    /**
     * A condition of a lambda, for what the other conditions do not express. The router
     * evaluates it after the others it is combined with, and cannot read it.
     *
     * @param predicate The predicate, which must be fast, must not block and must not read the body
     * @return The condition
     */
    static RequestCondition custom(Predicate<? super HttpRequest<?>> predicate) {
        return new Custom(Objects.requireNonNull(predicate, "predicate"));
    }

    /**
     * A request whose body, decoded to the type, meets the predicate, e.g.
     * {@code body(Order.class, order -> order.total() > 1000)}. The body is decoded like a
     * {@code @Body} argument, by the message body readers of its content type; a body that does
     * not decode, or is larger than the limit, see {@link Body#maxBytes(int)}, does not meet it.
     *
     * @param type      The type
     * @param predicate The predicate, which must be fast and must not block
     * @param <T>       The type
     * @return The condition
     */
    static <T> Body<T> body(Class<T> type, Predicate<? super T> predicate) {
        return body(Argument.of(Objects.requireNonNull(type, "type")), predicate);
    }

    /**
     * A request whose body, decoded to the type, meets the predicate, see
     * {@link #body(Class, Predicate)}.
     *
     * @param type      The type
     * @param predicate The predicate, which must be fast and must not block
     * @param <T>       The type
     * @return The condition
     */
    static <T> Body<T> body(Argument<T> type, Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate);
        return new Body<>(type, new BodyPredicate<>(predicate), Body.SERVER_LIMIT);
    }

    /**
     * A request whose body, decoded to the type, meets a condition decided later, e.g. after a
     * lookup in a database: {@code bodyAsync(Order.class, order -> customers.isPremiumAsync(order.customer()))}.
     * The route is chosen once the stage completes, without blocking, and a stage that fails
     * does not meet the condition, which is logged as a warning.
     *
     * @param type      The type
     * @param condition The condition, which must not block: it returns a stage of whether the body meets it
     * @param <T>       The type
     * @return The condition
     */
    static <T> Body<T> bodyAsync(Class<T> type, Function<? super T, ? extends CompletionStage<Boolean>> condition) {
        return bodyAsync(Argument.of(Objects.requireNonNull(type, "type")), condition);
    }

    /**
     * A request whose body, decoded to the type, meets a condition decided later, see
     * {@link #bodyAsync(Class, Function)}.
     *
     * @param type      The type
     * @param condition The condition, which must not block: it returns a stage of whether the body meets it
     * @param <T>       The type
     * @return The condition
     */
    static <T> Body<T> bodyAsync(Argument<T> type, Function<? super T, ? extends CompletionStage<Boolean>> condition) {
        return new Body<>(type, condition, Body.SERVER_LIMIT);
    }

    /**
     * A request whose body, as text in the charset of its content type, meets the predicate.
     *
     * @param predicate The predicate, which must be fast and must not block
     * @return The condition
     */
    static Body<String> bodyText(Predicate<? super String> predicate) {
        return body(Argument.STRING, predicate);
    }

    /**
     * A request whose body, as bytes, meets the predicate.
     *
     * @param predicate The predicate, which must be fast and must not block
     * @return The condition
     */
    static Body<byte[]> bodyBytes(Predicate<? super byte[]> predicate) {
        return body(Argument.of(byte[].class), predicate);
    }

    /**
     * @param other Another condition, see {@link #custom(Predicate)} for a lambda
     * @return A condition met when both are
     */
    default RequestCondition and(RequestCondition other) {
        return new AllOf(List.of(this, Objects.requireNonNull(other, "other")));
    }

    /**
     * @param other Another condition, see {@link #custom(Predicate)} for a lambda
     * @return A condition met when one of them is
     */
    default RequestCondition or(RequestCondition other) {
        return new AnyOf(List.of(this, Objects.requireNonNull(other, "other")));
    }

    /**
     * @return A condition met when this one is not
     */
    default RequestCondition negate() {
        return this instanceof Not not ? not.condition() : new Not(this);
    }

    /**
     * Checks the components of a condition on a named value.
     *
     * @param name  The name
     * @param value The matcher of the value
     */
    /**
     * Checks that a part of a combination a request of the route may not meet has no body condition.
     *
     * @param condition   The part
     * @param combination The combination, for the message
     */
    private static void requireNoBody(RequestCondition condition, String combination) {
        if (condition instanceof Body<?>) {
            throw new IllegalArgumentException("A body condition is met by every request of its route: it can be a part of "
                + "all(...) or and(...), not of " + combination + ": " + condition);
        }
        if (condition instanceof AllOf allOf) {
            for (RequestCondition part : allOf.conditions()) {
                requireNoBody(part, combination);
            }
        }
        // the parts of any(...) and not(...) were checked when they were created
    }

    private static void requireNameAndValue(String name, ValueMatcher value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
    }

    /**
     * A request with a header whose value the matcher matches, one of its values if the header
     * is repeated. A request without the header is given to the matcher as an absent value.
     *
     * @param name  The name of the header, case-insensitive
     * @param value The matcher of the value
     */
    record Header(String name, ValueMatcher value) implements RequestCondition {
        public Header {
            requireNameAndValue(name, value);
        }
    }

    /**
     * A request with a query parameter whose decoded value the matcher matches, one of its
     * values if the parameter is repeated. A parameter without a value, e.g. {@code ?debug}, has
     * an empty value.
     *
     * @param name  The name of the query parameter, case-sensitive
     * @param value The matcher of the value
     */
    record Query(String name, ValueMatcher value) implements RequestCondition {
        public Query {
            requireNameAndValue(name, value);
        }
    }

    /**
     * A request with a cookie whose value the matcher matches. The {@code Cookie} header is
     * scanned for the cookie: the other cookies are not decoded.
     *
     * @param name  The name of the cookie, case-sensitive
     * @param value The matcher of the value, without the quotes of a quoted value
     */
    record Cookie(String name, ValueMatcher value) implements RequestCondition {
        public Cookie {
            requireNameAndValue(name, value);
        }
    }

    /**
     * A request of one of the methods.
     *
     * @param methods The names of the methods, copied; {@code CUSTOM} stands for every custom method
     */
    record Method(Set<String> methods) implements RequestCondition {
        public Method {
            methods = Set.copyOf(methods);
        }
    }

    /**
     * A request to a host that the matcher matches, as the server resolves it: with the host
     * resolver of the server, which reads a forwarded host only as
     * {@code micronaut.server.host-resolution} configures it. The host is compared without the
     * scheme, the port and a final dot, ignoring case.
     *
     * @param host The matcher of the host, made to ignore case
     */
    record Host(ValueMatcher host) implements RequestCondition {
        public Host {
            host = Objects.requireNonNull(host, "host").ignoringCase();
        }
    }

    /**
     * A request from a client address in one of the ranges, as the server resolves it: with the
     * client address resolver of the server, which reads the header that
     * {@code micronaut.server.client-address-header} names, or else the forwarded headers of
     * the request, or else the peer of the connection. The peer of the connection is the
     * address when the resolver resolves none. A request whose address is not an IPv4 or IPv6
     * address, e.g. an obfuscated identifier of a {@code Forwarded} header, does not meet the
     * condition.
     *
     * <p>Without {@code micronaut.server.client-address-header} the resolver trusts the
     * {@code X-Forwarded-For} and {@code Forwarded} headers, which any client can send: use
     * {@link PeerAddress} for access control.</p>
     *
     * @param ranges The ranges, copied, at least one
     */
    record RemoteAddress(List<Cidr> ranges) implements RequestCondition {
        public RemoteAddress {
            ranges = List.copyOf(ranges);
            if (ranges.isEmpty()) {
                throw new IllegalArgumentException("An address range is required");
            }
        }
    }

    /**
     * A request whose connection comes from an address in one of the ranges: the peer of the
     * connection, {@link HttpRequest#getRemoteAddress()}, never a forwarded address. A client
     * cannot spoof it with a header, so this is the condition for access control. A request
     * without a peer address, or whose peer address is unresolved, does not meet the condition.
     *
     * @param ranges The ranges, copied, at least one
     */
    record PeerAddress(List<Cidr> ranges) implements RequestCondition {
        public PeerAddress {
            ranges = List.copyOf(ranges);
            if (ranges.isEmpty()) {
                throw new IllegalArgumentException("An address range is required");
            }
        }
    }

    /**
     * A request that arrives in a period, as the clock of the server tells: the
     * {@link java.time.Clock} bean if there is one, otherwise the system clock.
     *
     * @param after  The start, included, or {@code null} for none
     * @param before The end, excluded, or {@code null} for none
     */
    record TimeWindow(@Nullable Instant after, @Nullable Instant before) implements RequestCondition {
        public TimeWindow {
            if (after == null && before == null) {
                throw new IllegalArgumentException("A start or an end is required");
            }
            if (after != null && before != null && before.isBefore(after)) {
                throw new IllegalArgumentException("The end " + before + " is before the start " + after);
            }
        }
    }

    /**
     * A request that meets every condition, met by every request if there are none.
     *
     * @param conditions The conditions, copied
     */
    record AllOf(List<RequestCondition> conditions) implements RequestCondition {
        public AllOf {
            conditions = List.copyOf(conditions);
        }
    }

    /**
     * A request that meets one of the conditions, met by no request if there are none.
     *
     * @param conditions The conditions, copied
     */
    record AnyOf(List<RequestCondition> conditions) implements RequestCondition {
        public AnyOf {
            conditions = List.copyOf(conditions);
            for (RequestCondition condition : conditions) {
                requireNoBody(condition, "any(...) or or(...)");
            }
        }
    }

    /**
     * A request that does not meet the condition.
     *
     * @param condition The negated condition
     */
    record Not(RequestCondition condition) implements RequestCondition {
        public Not {
            Objects.requireNonNull(condition, "condition");
            requireNoBody(condition, "not(...) or negate()");
        }
    }

    /**
     * A condition of a lambda. The router cannot read it, and evaluates it after the conditions
     * it is combined with.
     *
     * @param predicate The predicate
     */
    record Custom(Predicate<? super HttpRequest<?>> predicate) implements RequestCondition {
        public Custom {
            Objects.requireNonNull(predicate, "predicate");
        }
    }

    /**
     * A request whose body, read as the type, meets the condition: the body as text for
     * {@code String}, as bytes for {@code byte[]}, and otherwise decoded by the message body
     * readers of its content type, like a {@code @Body} argument.
     *
     * <p>The router matches a request without its body, and leaves the routes whose body
     * conditions decide the match pending. The server reads the body once, without blocking, from
     * a copy, so the chosen route still reads the body in any way, evaluates the body conditions,
     * and chooses among the routes whose body conditions the body meets. A request whose body
     * meets the conditions of none of its best routes falls through to the next routes, and two
     * equally good routes whose conditions the body both meets make the request ambiguous.</p>
     *
     * <p>The body is held in memory, up to {@code micronaut.server.max-request-buffer-size}, or up
     * to the lower limit of {@link #maxBytes(int)}: a larger body does not meet the condition. A
     * limit lowers the limit of the server and cannot raise it: a route with a condition whose
     * limit is above it fails the startup of the server.</p>
     *
     * @param type      The type the body is read as
     * @param condition Whether the body meets the condition, as a stage, which must not block: a
     *                  synchronous predicate, see {@link #body(Class, Predicate)}, is a completed stage
     * @param maxBytes  The limit of the body, or {@link #SERVER_LIMIT} for the limit of the server
     * @param <T>       The type
     */
    record Body<T>(Argument<T> type, Function<? super T, ? extends CompletionStage<Boolean>> condition, int maxBytes) implements RequestCondition {

        /**
         * The limit of the server for buffered request content, {@code micronaut.server.max-request-buffer-size}.
         */
        public static final int SERVER_LIMIT = -1;

        public Body {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(condition, "condition");
            if (maxBytes < 0 && maxBytes != SERVER_LIMIT) {
                throw new IllegalArgumentException("The limit of a body condition must not be negative: " + maxBytes);
            }
        }

        /**
         * Read at most a number of bytes of the body for this condition: a larger body does not
         * meet it. The limit lowers the limit of the server, {@code micronaut.server.max-request-buffer-size},
         * and cannot raise it: a route with a limit above it fails the startup of the server.
         *
         * @param maxBytes The limit, or {@link #SERVER_LIMIT} for the limit of the server
         * @return The condition with the limit
         */
        public Body<T> maxBytes(int maxBytes) {
            if (maxBytes < 0 && maxBytes != SERVER_LIMIT) {
                throw new IllegalArgumentException("The limit of a body condition must not be negative: " + maxBytes);
            }
            return new Body<>(type, condition, maxBytes);
        }

        /**
         * @return The predicate of the condition, when it is a synchronous predicate, see
         * {@link #body(Argument, Predicate)}, which is tested without a stage, otherwise
         * {@code null}: its stage decides
         */
        @Internal
        @SuppressWarnings("unchecked")
        public @Nullable Predicate<? super T> predicate() {
            return condition instanceof BodyPredicate<?> predicate ? ((BodyPredicate<T>) predicate).predicate() : null;
        }

        /**
         * @param serverLimit The limit of the server, {@code micronaut.server.max-request-buffer-size}
         * @return The number of bytes the condition reads at most
         */
        @Internal
        public int effectiveMaxBytes(int serverLimit) {
            return maxBytes == SERVER_LIMIT ? serverLimit : Math.min(maxBytes, serverLimit);
        }
    }
}
