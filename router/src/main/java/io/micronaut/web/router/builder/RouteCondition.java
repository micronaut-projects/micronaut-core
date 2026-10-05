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
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A declarative condition on a request, for {@link RouteSpec#where(RouteCondition)}: on its
 * headers, query parameters, cookies, method, host, client and peer addresses and the time it arrives, and
 * their combinations. A condition is data: the router reads it, flattens and orders it
 * cheapest first when the route is built, and shows it in the route info, which a lambda does
 * not allow. {@link #custom(Predicate)} takes a lambda for what the others do not express.
 *
 * <pre>{@code
 * import static io.micronaut.web.router.builder.RouteCondition.*;
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
 * the clock of the application. Conditions combine with {@link #and(RouteCondition)},
 * {@link #or(RouteCondition)} and {@link #negate()}; a lambda is a {@link #custom(Predicate)}
 * condition.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface RouteCondition {

    /**
     * A request with a header of the name, e.g. {@code header("X-Beta")}.
     *
     * @param name The name of the header, case-insensitive
     * @return The condition
     */
    static RouteCondition header(String name) {
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
    static RouteCondition header(String name, String value) {
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
    static RouteCondition header(String name, ValueMatcher value) {
        return new Header(name, value);
    }

    /**
     * A request with a query parameter of the name, with or without a value, e.g.
     * {@code query("debug")} for {@code ?debug}.
     *
     * @param name The name of the query parameter, case-sensitive
     * @return The condition
     */
    static RouteCondition query(String name) {
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
    static RouteCondition query(String name, String value) {
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
    static RouteCondition query(String name, ValueMatcher value) {
        return new Query(name, value);
    }

    /**
     * A request with a cookie of the name, e.g. {@code cookie("SESSION")}.
     *
     * @param name The name of the cookie, case-sensitive
     * @return The condition
     */
    static RouteCondition cookie(String name) {
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
    static RouteCondition cookie(String name, ValueMatcher value) {
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
    static RouteCondition method(HttpMethod... methods) {
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
    static RouteCondition method(String... methods) {
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
    static RouteCondition host(ValueMatcher host) {
        return new Host(host);
    }

    /**
     * A request to one of the hosts, e.g. {@code host("api.example.com", "localhost")}, compared
     * ignoring case. An IPv6 address is written in brackets, e.g. {@code [::1]}.
     *
     * @param hosts The hosts, at least one, without a port
     * @return The condition
     */
    static RouteCondition host(String... hosts) {
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
    static RouteCondition remoteAddress(String... cidrs) {
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
    static RouteCondition peerAddress(String... cidrs) {
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
    static RouteCondition before(Instant instant) {
        return new TimeWindow(null, Objects.requireNonNull(instant, "instant"));
    }

    /**
     * A request that arrives at or after an instant, e.g. once a release goes live.
     *
     * @param instant The instant, included
     * @return The condition
     */
    static RouteCondition after(Instant instant) {
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
    static RouteCondition between(Instant start, Instant end) {
        return new TimeWindow(Objects.requireNonNull(start, "start"), Objects.requireNonNull(end, "end"));
    }

    /**
     * A request that meets every condition.
     *
     * @param conditions The conditions
     * @return The condition, met by every request if there are none
     */
    static RouteCondition all(RouteCondition... conditions) {
        return new AllOf(List.of(conditions));
    }

    /**
     * A request that meets one of the conditions.
     *
     * @param conditions The conditions
     * @return The condition, met by no request if there are none
     */
    static RouteCondition any(RouteCondition... conditions) {
        return new AnyOf(List.of(conditions));
    }

    /**
     * A request that does not meet the condition.
     *
     * @param condition The condition
     * @return The condition
     */
    static RouteCondition not(RouteCondition condition) {
        return condition.negate();
    }

    /**
     * A condition of a lambda, for what the other conditions do not express. The router
     * evaluates it after the others it is combined with, and cannot read it.
     *
     * @param predicate The predicate, which must be fast, must not block and must not read the body
     * @return The condition
     */
    static RouteCondition custom(Predicate<? super HttpRequest<?>> predicate) {
        return new Custom(Objects.requireNonNull(predicate, "predicate"));
    }

    /**
     * @param other Another condition, see {@link #custom(Predicate)} for a lambda
     * @return A condition met when both are
     */
    default RouteCondition and(RouteCondition other) {
        return new AllOf(List.of(this, Objects.requireNonNull(other, "other")));
    }

    /**
     * @param other Another condition, see {@link #custom(Predicate)} for a lambda
     * @return A condition met when one of them is
     */
    default RouteCondition or(RouteCondition other) {
        return new AnyOf(List.of(this, Objects.requireNonNull(other, "other")));
    }

    /**
     * @return A condition met when this one is not
     */
    default RouteCondition negate() {
        return this instanceof Not not ? not.condition() : new Not(this);
    }

    /**
     * A request with a header whose value the matcher matches, one of its values if the header
     * is repeated. A request without the header is given to the matcher as an absent value.
     *
     * @param name  The name of the header, case-insensitive
     * @param value The matcher of the value
     */
    record Header(String name, ValueMatcher value) implements RouteCondition {
        public Header {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
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
    record Query(String name, ValueMatcher value) implements RouteCondition {
        public Query {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * A request with a cookie whose value the matcher matches. The {@code Cookie} header is
     * scanned for the cookie: the other cookies are not decoded.
     *
     * @param name  The name of the cookie, case-sensitive
     * @param value The matcher of the value, without the quotes of a quoted value
     */
    record Cookie(String name, ValueMatcher value) implements RouteCondition {
        public Cookie {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * A request of one of the methods.
     *
     * @param methods The names of the methods, copied; {@code CUSTOM} stands for every custom method
     */
    record Method(Set<String> methods) implements RouteCondition {
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
    record Host(ValueMatcher host) implements RouteCondition {
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
    record RemoteAddress(List<Cidr> ranges) implements RouteCondition {
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
    record PeerAddress(List<Cidr> ranges) implements RouteCondition {
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
    record TimeWindow(@Nullable Instant after, @Nullable Instant before) implements RouteCondition {
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
    record AllOf(List<RouteCondition> conditions) implements RouteCondition {
        public AllOf {
            conditions = List.copyOf(conditions);
        }
    }

    /**
     * A request that meets one of the conditions, met by no request if there are none.
     *
     * @param conditions The conditions, copied
     */
    record AnyOf(List<RouteCondition> conditions) implements RouteCondition {
        public AnyOf {
            conditions = List.copyOf(conditions);
        }
    }

    /**
     * A request that does not meet the condition.
     *
     * @param condition The negated condition
     */
    record Not(RouteCondition condition) implements RouteCondition {
        public Not {
            Objects.requireNonNull(condition, "condition");
        }
    }

    /**
     * A condition of a lambda. The router cannot read it, and evaluates it after the conditions
     * it is combined with.
     *
     * @param predicate The predicate
     */
    record Custom(Predicate<? super HttpRequest<?>> predicate) implements RouteCondition {
        public Custom {
            Objects.requireNonNull(predicate, "predicate");
        }
    }
}
