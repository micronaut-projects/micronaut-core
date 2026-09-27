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
package io.micronaut.http.gateway.config;

import io.micronaut.context.BeanLocator;
import io.micronaut.web.router.builder.RouteFilterSpec;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.format.ReadableBytes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.gateway.ClientAddress;
import io.micronaut.http.gateway.FailoverHandler;
import io.micronaut.http.gateway.GatewayCircuitBreaker;
import io.micronaut.http.gateway.GatewayConfiguration;
import io.micronaut.http.gateway.GatewayFilters;
import io.micronaut.http.gateway.GatewayRateLimit;
import io.micronaut.http.gateway.GatewayRateLimiter;
import io.micronaut.http.gateway.InMemoryRateLimiter;
import io.micronaut.http.gateway.KeyResolver;
import io.micronaut.http.gateway.GatewayRetry;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.web.router.builder.AsyncRequestHandler;
import io.micronaut.web.router.builder.Cidr;
import io.micronaut.web.router.builder.RequestHandler;
import io.micronaut.web.router.builder.RequestCondition;
import io.micronaut.web.router.builder.ValueMatcher;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * The predicates and filters of the gateway routes of the configuration, named after their
 * Spring Cloud Gateway equivalents.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Factory
final class BuiltInGatewayFactories {

    private static final String VALUE = "value";
    private static final String REGEXP = "regexp";
    private static final String DATETIME = "datetime";
    private static final String REPLACEMENT = "replacement";
    private static final String STATUS = "status";
    private static final String MAX_SIZE = "max-size";
    private static final String KEY_RESOLVER = "key-resolver";
    private static final List<String> NAME_VALUE = List.of("name", VALUE);

    // ---- predicates

    @Singleton
    @Named("Path")
    GatewayPredicateFactory path() {
        return GatewayPredicateFactory.of(List.of("*patterns"), (route, args) -> {
            List<String> patterns = args.getList("patterns");
            if (patterns.isEmpty()) {
                throw new ConfigurationException("The Path predicate needs a pattern: " + args);
            }
            route.path(patterns.toArray(String[]::new));
        });
    }

    @Singleton
    @Named("Method")
    GatewayPredicateFactory method() {
        return GatewayPredicateFactory.of(List.of("*methods"), (route, args) -> {
            List<String> methods = args.getList("methods");
            List<String> upper = new ArrayList<>(methods.size());
            for (String method : methods) {
                upper.add(method.toUpperCase(Locale.ROOT));
            }
            route.where(RequestCondition.method(upper.toArray(String[]::new)));
        });
    }

    @Singleton
    @Named("Header")
    GatewayPredicateFactory header() {
        return GatewayPredicateFactory.of(List.of("header", REGEXP), (route, args) -> {
            String name = args.get("header");
            Optional<String> regexp = args.find(REGEXP);
            route.where(regexp.isPresent() ? RequestCondition.header(name, listElement(regexp.get())) : RequestCondition.header(name));
        });
    }

    /**
     * A value the regular expression matches as a whole, or one of its elements separated by
     * commas matches, e.g. {@code b} in {@code a, b}, like the header predicate of Spring Cloud
     * Gateway.
     *
     * @param regexp The regular expression
     * @return The matcher
     */
    static ValueMatcher listElement(String regexp) {
        return ValueMatcher.anyOf(ValueMatcher.regex(regexp), ValueMatcher.regex("(?:.*,\\s*)?(?:" + regexp + ")(?:\\s*,.*)?"));
    }

    @Singleton
    @Named("Query")
    GatewayPredicateFactory query() {
        return GatewayPredicateFactory.of(List.of("param", REGEXP), (route, args) -> {
            String name = args.get("param");
            Optional<String> regexp = args.find(REGEXP);
            route.where(regexp.isPresent() ? RequestCondition.query(name, ValueMatcher.regex(regexp.get())) : RequestCondition.query(name));
        });
    }

    @Singleton
    @Named("Cookie")
    GatewayPredicateFactory cookie() {
        return GatewayPredicateFactory.of(List.of("name", REGEXP), (route, args) -> {
            String name = args.get("name");
            Optional<String> regexp = args.find(REGEXP);
            route.where(regexp.isPresent() ? RequestCondition.cookie(name, ValueMatcher.regex(regexp.get())) : RequestCondition.cookie(name));
        });
    }

    @Singleton
    @Named("Host")
    GatewayPredicateFactory host() {
        return GatewayPredicateFactory.of(List.of("*patterns"), (route, args) -> {
            List<String> patterns = args.getList("patterns");
            if (patterns.isEmpty()) {
                throw new ConfigurationException("The Host predicate needs a pattern: " + args);
            }
            List<ValueMatcher> matchers = new ArrayList<>(patterns.size());
            for (String pattern : patterns) {
                matchers.add(hostMatcher(pattern));
            }
            route.where(RequestCondition.host(matchers.size() == 1 ? matchers.get(0) : ValueMatcher.anyOf(matchers.toArray(ValueMatcher[]::new))));
        });
    }

    /**
     * A host pattern of Spring Cloud Gateway: {@code **} any labels, {@code *} or
     * {@code {name}} one label, e.g. {@code **.example.org} or {@code {sub}.example.org}. A port
     * in the pattern is ignored: the condition compares the host without its port.
     *
     * @param pattern The pattern
     * @return The matcher of the host
     */
    static ValueMatcher hostMatcher(String pattern) {
        String p = pattern.strip().toLowerCase(Locale.ROOT);
        int colon = p.lastIndexOf(':');
        if (colon > p.lastIndexOf(']')) {
            p = p.substring(0, colon);
        }
        if (p.endsWith(".")) {
            p = p.substring(0, p.length() - 1);
        }
        if (p.indexOf('*') < 0 && p.indexOf('{') < 0) {
            return ValueMatcher.equalTo(p);
        }
        StringBuilder regex = new StringBuilder();
        String[] labels = p.split("\\.", -1);
        for (int i = 0; i < labels.length; i++) {
            String label = labels[i];
            if (label.equals("**")) {
                regex.append("(?:[^.]+\\.)*[^.]+");
            } else if (label.equals("*") || label.startsWith("{") && label.endsWith("}")) {
                regex.append("[^.]+");
            } else {
                regex.append(Pattern.quote(label));
            }
            if (i < labels.length - 1) {
                regex.append("\\.");
            }
        }
        return ValueMatcher.regex(regex.toString());
    }

    @Singleton
    @Named("RemoteAddr")
    GatewayPredicateFactory remoteAddr() {
        return GatewayPredicateFactory.of(List.of("*sources"), (route, args) -> route.where(RequestCondition.peerAddress(args.getList("sources").toArray(String[]::new))));
    }

    @Singleton
    @Named("XForwardedRemoteAddr")
    GatewayPredicateFactory xForwardedRemoteAddr(GatewayConfiguration configuration) {
        List<Cidr> trusted = new ArrayList<>();
        for (String range : configuration.getForwarded().getTrustedProxies()) {
            trusted.add(Cidr.parse(range));
        }
        return GatewayPredicateFactory.of(List.of("*sources"), (route, args) -> {
            List<Cidr> sources = new ArrayList<>();
            for (String source : args.getList("sources")) {
                sources.add(Cidr.parse(source));
            }
            route.where(RequestCondition.custom(request -> {
                byte[] client = clientAddress(request, trusted);
                return client != null && sources.stream().anyMatch(range -> range.contains(client));
            }));
        });
    }

    /**
     * The address of the client: the right-most address of {@code X-Forwarded-For} that is not a
     * trusted proxy, walking from the right while the addresses are trusted proxies, when the
     * peer is a trusted proxy; otherwise the peer.
     *
     * @param request The request
     * @param trusted The trusted proxies
     * @return The address, or {@code null}
     */
    static byte @Nullable [] clientAddress(HttpRequest<?> request, List<Cidr> trusted) {
        return ClientAddress.of(request, trusted);
    }

    @Singleton
    @Named("Weight")
    GatewayPredicateFactory weight() {
        return GatewayPredicateFactory.of(List.of("group", "weight"), (route, args) ->
            route.weight(args.get("group"), args.get("weight", Integer.class, 1)));
    }

    @Singleton
    @Named("After")
    GatewayPredicateFactory after() {
        return GatewayPredicateFactory.of(List.of(DATETIME), (route, args) -> route.where(RequestCondition.after(instant(args.get(DATETIME)))));
    }

    @Singleton
    @Named("Before")
    GatewayPredicateFactory before() {
        return GatewayPredicateFactory.of(List.of(DATETIME), (route, args) -> route.where(RequestCondition.before(instant(args.get(DATETIME)))));
    }

    @Singleton
    @Named("Between")
    GatewayPredicateFactory between() {
        return GatewayPredicateFactory.of(List.of("datetime1", "datetime2"), (route, args) ->
            route.where(RequestCondition.between(instant(args.get("datetime1")), instant(args.get("datetime2")))));
    }

    /**
     * @param value An ISO date-time with a zone, or epoch millis
     * @return The instant
     */
    static Instant instant(String value) {
        String v = value.strip();
        try {
            return Instant.ofEpochMilli(Long.parseLong(v));
        } catch (NumberFormatException e) {
            try {
                return ZonedDateTime.parse(v).toInstant();
            } catch (DateTimeParseException ex) {
                throw new ConfigurationException("Not a date-time with a zone or epoch millis: " + value, ex);
            }
        }
    }

    // ---- filters

    @Singleton
    @Named("StripPrefix")
    GatewayFilterFactory stripPrefix() {
        return GatewayFilterFactory.of(List.of("parts"), args -> GatewayFilters.stripPrefix(args.get("parts", Integer.class, 1)));
    }

    @Singleton
    @Named("PrefixPath")
    GatewayFilterFactory prefixPath() {
        return GatewayFilterFactory.of(List.of("prefix"), args -> GatewayFilters.prefixPath(args.get("prefix")));
    }

    @Singleton
    @Named("SetPath")
    GatewayFilterFactory setPath() {
        return GatewayFilterFactory.of(List.of("template"), args -> GatewayFilters.setPath(args.get("template")));
    }

    @Singleton
    @Named("RewritePath")
    GatewayFilterFactory rewritePath() {
        return GatewayFilterFactory.of(List.of(REGEXP, REPLACEMENT), args -> GatewayFilters.rewritePath(args.get(REGEXP), args.find(REPLACEMENT).orElse("")));
    }

    @Singleton
    @Named("AddRequestHeader")
    GatewayFilterFactory addRequestHeader() {
        return GatewayFilterFactory.of(NAME_VALUE, args -> GatewayFilters.addRequestHeader(args.get("name"), args.find(VALUE).orElse("")));
    }

    @Singleton
    @Named("AddRequestHeadersIfNotPresent")
    GatewayFilterFactory addRequestHeadersIfNotPresent() {
        // name:value pairs, e.g. AddRequestHeadersIfNotPresent=X-A:1,X-B:2
        return GatewayFilterFactory.of(List.of("*headers"), args -> {
            List<io.micronaut.http.gateway.GatewayFilter> filters = new ArrayList<>();
            for (String pair : args.getList("headers")) {
                int colon = pair.indexOf(':');
                if (colon <= 0) {
                    throw new ConfigurationException("AddRequestHeadersIfNotPresent takes name:value pairs: " + pair);
                }
                filters.add(GatewayFilters.addRequestHeaderIfNotPresent(pair.substring(0, colon).strip(), pair.substring(colon + 1).strip()));
            }
            return new CompositeFilter(List.copyOf(filters));
        });
    }

    @Singleton
    @Named("SetRequestHeader")
    GatewayFilterFactory setRequestHeader() {
        return GatewayFilterFactory.of(NAME_VALUE, args -> GatewayFilters.setRequestHeader(args.get("name"), args.find(VALUE).orElse("")));
    }

    @Singleton
    @Named("RemoveRequestHeader")
    GatewayFilterFactory removeRequestHeader() {
        return GatewayFilterFactory.of(List.of("name"), args -> GatewayFilters.removeRequestHeader(args.get("name")));
    }

    @Singleton
    @Named("MapRequestHeader")
    GatewayFilterFactory mapRequestHeader() {
        return GatewayFilterFactory.of(List.of("from-header", "to-header"), args -> GatewayFilters.mapRequestHeader(args.get("from-header"), args.get("to-header")));
    }

    @Singleton
    @Named("AddRequestParameter")
    GatewayFilterFactory addRequestParameter() {
        return GatewayFilterFactory.of(NAME_VALUE, args -> GatewayFilters.addRequestParameter(args.get("name"), args.find(VALUE).orElse("")));
    }

    @Singleton
    @Named("RemoveRequestParameter")
    GatewayFilterFactory removeRequestParameter() {
        return GatewayFilterFactory.of(List.of("name"), args -> GatewayFilters.removeRequestParameter(args.get("name")));
    }

    @Singleton
    @Named("AddResponseHeader")
    GatewayFilterFactory addResponseHeader() {
        return GatewayFilterFactory.of(NAME_VALUE, args -> GatewayFilters.addResponseHeader(args.get("name"), args.find(VALUE).orElse("")));
    }

    @Singleton
    @Named("SetResponseHeader")
    GatewayFilterFactory setResponseHeader() {
        return GatewayFilterFactory.of(NAME_VALUE, args -> GatewayFilters.setResponseHeader(args.get("name"), args.find(VALUE).orElse("")));
    }

    @Singleton
    @Named("RemoveResponseHeader")
    GatewayFilterFactory removeResponseHeader() {
        return GatewayFilterFactory.of(List.of("name"), args -> GatewayFilters.removeResponseHeader(args.get("name")));
    }

    @Singleton
    @Named("RewriteResponseHeader")
    GatewayFilterFactory rewriteResponseHeader() {
        return GatewayFilterFactory.of(List.of("name", REGEXP, REPLACEMENT), args ->
            GatewayFilters.rewriteResponseHeader(args.get("name"), args.get(REGEXP), args.find(REPLACEMENT).orElse("")));
    }

    @Singleton
    @Named("DedupeResponseHeader")
    GatewayFilterFactory dedupeResponseHeader() {
        return GatewayFilterFactory.of(List.of("name", "strategy"), args -> {
            List<String> names = new ArrayList<>();
            for (String header : args.get("name").split("\\s+")) {
                if (!header.isBlank()) {
                    names.add(header);
                }
            }
            String name = args.find("strategy").orElse("RETAIN_FIRST").strip().toUpperCase(Locale.ROOT);
            GatewayFilters.DedupeStrategy strategy = switch (name) {
                case "RETAIN_FIRST" -> GatewayFilters.DedupeStrategy.RETAIN_FIRST;
                case "RETAIN_LAST" -> GatewayFilters.DedupeStrategy.RETAIN_LAST;
                case "RETAIN_UNIQUE" -> GatewayFilters.DedupeStrategy.RETAIN_UNIQUE;
                default -> throw new ConfigurationException("Unknown strategy of DedupeResponseHeader: " + name);
            };
            return GatewayFilters.dedupeResponseHeader(names, strategy);
        });
    }

    @Singleton
    @Named("RewriteLocationResponseHeader")
    GatewayFilterFactory rewriteLocationResponseHeader() {
        return GatewayFilterFactory.of(List.of(), args -> GatewayFilters.rewriteLocationResponseHeader());
    }

    @Singleton
    @Named("PreserveHostHeader")
    GatewayFilterFactory preserveHostHeader() {
        return GatewayFilterFactory.of(List.of(), args -> GatewayFilters.preserveHostHeader());
    }

    @Singleton
    @Named("SetRequestHostHeader")
    GatewayFilterFactory setRequestHostHeader() {
        return GatewayFilterFactory.of(List.of("host"), args -> GatewayFilters.setRequestHostHeader(args.get("host")));
    }

    @Singleton
    @Named("SecureHeaders")
    GatewayFilterFactory secureHeaders() {
        return GatewayFilterFactory.of(List.of(), args -> GatewayFilters.secureHeaders());
    }

    @Singleton
    @Named("SetStatus")
    GatewayFilterFactory setStatus() {
        return GatewayFilterFactory.of(List.of(STATUS), args -> GatewayFilters.setStatus(status(args.get(STATUS))));
    }

    @Singleton
    @Named("RedirectTo")
    GatewayFilterFactory redirectTo() {
        return GatewayFilterFactory.of(List.of(STATUS, "url"), args -> GatewayFilters.redirectTo(status(args.get(STATUS)), args.get("url")));
    }

    @Singleton
    @Named("RequestSize")
    GatewayFilterFactory requestSize() {
        return GatewayFilterFactory.of(List.of(MAX_SIZE), args -> GatewayFilters.requestSize(args.bind(RequestSizeArguments.class).maxSize()));
    }

    @Singleton
    @Named("CacheRequestBody")
    GatewayFilterFactory cacheRequestBody() {
        return GatewayFilterFactory.of(List.of(MAX_SIZE), args -> GatewayFilters.cacheRequestBody(args.bind(RequestSizeArguments.class).maxSize()));
    }

    @Singleton
    @Named("RequestHeaderSize")
    GatewayFilterFactory requestHeaderSize() {
        return GatewayFilterFactory.of(List.of(MAX_SIZE, "max-count"), args -> {
            RequestHeaderSizeArguments bound = args.bind(RequestHeaderSizeArguments.class);
            return GatewayFilters.requestHeaderSize(bound.maxSize(), bound.maxCount());
        });
    }

    @Singleton
    @Named("RequestRateLimiter")
    GatewayFilterFactory requestRateLimiter(BeanLocator beanLocator, GatewayConfiguration configuration) {
        return GatewayFilterFactory.of(List.of("replenish-rate", "burst-capacity", "requested-tokens", KEY_RESOLVER), args -> {
            RateLimiterArguments bound = args.bind(RateLimiterArguments.class);
            String limiterName = bound.rateLimiter();
            GatewayRateLimiter limiter = limiterName == null || limiterName.isBlank()
                ? new InMemoryRateLimiter()
                : beanLocator.findBean(GatewayRateLimiter.class, Qualifiers.byName(limiterName))
                    .orElseThrow(() -> new ConfigurationException("No GatewayRateLimiter bean named " + limiterName));
            long burst = bound.burstCapacity() == null ? bound.replenishRate() : bound.burstCapacity();
            try {
                return GatewayRateLimit.builder(bound.replenishRate(), burst)
                    .requestedTokens(bound.requestedTokens())
                    .keyResolver(keyResolver(beanLocator, configuration, bound.keyResolver()))
                    .emptyKey(bound.denyEmptyKey(), status(bound.emptyKeyStatus()))
                    .includeHeaders(bound.includeHeaders())
                    .limiter(limiter)
                    .build();
            } catch (IllegalArgumentException e) {
                throw new ConfigurationException("Invalid RequestRateLimiter: " + e.getMessage(), e);
            }
        });
    }

    @Singleton
    @Named("Bulkhead")
    GatewayFilterFactory bulkhead(BeanLocator beanLocator, GatewayConfiguration configuration) {
        return GatewayFilterFactory.of(List.of("max-concurrent", KEY_RESOLVER), args -> GatewayFilters.bulkhead(
            args.get("max-concurrent", Integer.class, 1), keyResolver(beanLocator, configuration, args.find(KEY_RESOLVER).orElse(null))));
    }

    /**
     * The key resolver of a name: {@code remoteAddress}, the default, by the address of the
     * client behind the trusted proxies of the gateway; {@code principal}; {@code header:<name>};
     * or the name of a {@link KeyResolver} bean.
     */
    private static KeyResolver keyResolver(BeanLocator beanLocator, GatewayConfiguration configuration, @Nullable String name) {
        if (name == null || name.isBlank() || name.equals("remoteAddress")) {
            return KeyResolver.remoteAddress(configuration.getForwarded().getTrustedProxies());
        }
        if (name.equals("principal")) {
            return KeyResolver.principalName();
        }
        if (name.startsWith("header:")) {
            return KeyResolver.header(name.substring("header:".length()).strip());
        }
        return beanLocator.findBean(KeyResolver.class, Qualifiers.byName(name))
            .orElseThrow(() -> new ConfigurationException("No KeyResolver bean named " + name));
    }

    @Singleton
    @Named("Retry")
    GatewayFilterFactory retry() {
        return GatewayFilterFactory.of(List.of("retries"), args -> {
            RetryArguments bound = args.bind(RetryArguments.class);
            Set<Integer> statuses = new LinkedHashSet<>();
            for (String status : bound.statuses()) {
                statuses.add(status(status));
            }
            return GatewayRetry.builder(bound.retries())
                .methods(Set.copyOf(bound.methods()))
                .statuses(statuses)
                .backoff(bound.delay(), bound.multiplier())
                .jitter(bound.jitter())
                .maxReplayBytes(bound.maxReplayBytes() == null ? GatewayRetry.DEFAULT_MAX_REPLAY_BYTES : bound.maxReplayBytes().longValue())
                .maxDelay(bound.maxDelay())
                .responseHeader(bound.responseHeader(), bound.responseHeaderValue() == null ? null : Pattern.compile(bound.responseHeaderValue()))
                .build();
        });
    }

    @Singleton
    @Named("CircuitBreaker")
    GatewayFilterFactory circuitBreaker(BeanLocator beanLocator) {
        return GatewayFilterFactory.of(List.of("name", "fallback"), args -> {
            CircuitBreakerArguments bound = args.bind(CircuitBreakerArguments.class);
            Set<Integer> failureStatuses = new LinkedHashSet<>();
            for (String status : bound.failureStatuses()) {
                failureStatuses.add(status(status));
            }
            Set<Integer> fallbackStatuses = new LinkedHashSet<>();
            for (String status : bound.fallbackStatuses()) {
                fallbackStatuses.add(status(status));
            }
            String fallback = bound.fallback();
            String fallbackUri = bound.fallbackUri();
            boolean failover = fallbackUri != null && !fallbackUri.isBlank();
            // a failover relays the body: kept up to 1 MiB unless configured
            long maxBody = bound.maxFallbackBody() != null ? bound.maxFallbackBody() : failover ? GatewayRetry.DEFAULT_MAX_REPLAY_BYTES : 0;
            if (failover) {
                String uri = java.util.Objects.requireNonNull(fallbackUri);
                if (fallback != null && !fallback.isBlank()) {
                    throw new ConfigurationException("The circuit breaker " + bound.name() + " has both a fallback and a fallback-uri");
                }
                AsyncRequestHandler handler = new FailoverHandler() {
                    private final java.util.concurrent.atomic.AtomicReference<io.micronaut.http.gateway.@Nullable ProxyHandler> proxy =
                        new java.util.concurrent.atomic.AtomicReference<>();

                    @Override
                    public java.util.concurrent.CompletionStage<? extends io.micronaut.http.HttpResponse<?>> handle(HttpRequest<?> request,
                                                                                                             io.micronaut.http.PathVariables pathVariables) throws Exception {
                        io.micronaut.http.gateway.ProxyHandler p = proxy.get();
                        if (p == null) {
                            p = beanLocator.getBean(io.micronaut.http.gateway.ProxyHandlerFactory.class).to(uri);
                            proxy.set(p);
                        }
                        return p.handle(request, pathVariables);
                    }
                };
                return GatewayCircuitBreaker.builder(bound.name()).fallback(handler)
                    .failureStatuses(failureStatuses).fallbackStatuses(fallbackStatuses).maxFallbackBodyBytes(maxBody).build();
            }
            if (fallback == null || fallback.isBlank()) {
                if (!fallbackStatuses.isEmpty()) {
                    throw new ConfigurationException("The circuit breaker " + bound.name() + " has fallback statuses and no fallback");
                }
                return GatewayCircuitBreaker.builder(bound.name()).failureStatuses(failureStatuses).maxFallbackBodyBytes(maxBody).build();
            }
            if (fallback.startsWith("forward:")) {
                throw new ConfigurationException("A gateway fallback is the name of a handler bean, not a forward: URI: " + fallback);
            }
            Optional<AsyncRequestHandler> async = beanLocator.findBean(AsyncRequestHandler.class, Qualifiers.byName(fallback));
            if (async.isPresent()) {
                return GatewayCircuitBreaker.builder(bound.name()).fallback(async.get())
                    .failureStatuses(failureStatuses).fallbackStatuses(fallbackStatuses).maxFallbackBodyBytes(maxBody).build();
            }
            RequestHandler handler = beanLocator.findBean(RequestHandler.class, Qualifiers.byName(fallback))
                .orElseThrow(() -> new ConfigurationException("No AsyncRequestHandler or RequestHandler bean named " + fallback + " for the fallback of the circuit breaker " + bound.name()));
            return GatewayCircuitBreaker.builder(bound.name())
                .fallback((request, pathVariables) -> CompletableFuture.completedFuture(handler.handle(request, pathVariables)))
                .failureStatuses(failureStatuses).fallbackStatuses(fallbackStatuses).maxFallbackBodyBytes(maxBody).build();
        });
    }

    /**
     * @param status A status code or the name of a status, e.g. {@code 503} or {@code SERVICE_UNAVAILABLE}
     * @return The status code
     */
    static int status(String status) {
        String s = status.strip();
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            String name = s.toUpperCase(Locale.ROOT);
            for (HttpStatus value : HttpStatus.values()) {
                if (value.name().equals(name)) {
                    return value.getCode();
                }
            }
            throw new ConfigurationException("Not a status: " + status, e);
        }
    }

    /**
     * The arguments of the {@code Retry} filter.
     *
     * @param retries    The number of retries
     * @param methods    The methods
     * @param statuses   The statuses, codes or names
     * @param delay      The delay before the first retry
     * @param multiplier The multiplier of the delay
     * @param jitter     The jitter
     * @param maxReplayBytes The number of bytes kept to send a body again, e.g. {@code 64KB}
     * @param maxDelay       The limit of the sum of the delays
     * @param responseHeader The header of a response that retries it
     * @param responseHeaderValue The pattern of the value of the header
     */
    @Introspected
    record RetryArguments(@Bindable(defaultValue = "3") int retries,
                          @Bindable(defaultValue = "GET,HEAD") List<String> methods,
                          @Bindable(defaultValue = "502,503,504") List<String> statuses,
                          @Bindable(defaultValue = "50ms") Duration delay,
                          @Bindable(defaultValue = "2") double multiplier,
                          @Bindable(defaultValue = "0") double jitter,
                          @ReadableBytes @Nullable Long maxReplayBytes,
                          @Nullable Duration maxDelay,
                          @Nullable String responseHeader,
                          @Nullable String responseHeaderValue) {
    }

    /**
     * The arguments of the {@code RequestRateLimiter} filter.
     *
     * @param replenishRate   The tokens added per second
     * @param burstCapacity   The tokens a bucket holds, the replenish rate by default
     * @param requestedTokens The tokens a request takes
     * @param keyResolver     The key resolver, see {@link #keyResolver}
     * @param denyEmptyKey    Whether a request without a key is denied
     * @param emptyKeyStatus  The status of a request denied for its empty key
     * @param includeHeaders  Whether the responses carry the rate limit headers
     * @param rateLimiter     The name of a GatewayRateLimiter bean, the memory of the gateway by default
     */
    @Introspected
    record RateLimiterArguments(long replenishRate,
                                @Nullable Long burstCapacity,
                                @Bindable(defaultValue = "1") long requestedTokens,
                                @Nullable String keyResolver,
                                @Bindable(defaultValue = "true") boolean denyEmptyKey,
                                @Bindable(defaultValue = "403") String emptyKeyStatus,
                                @Bindable(defaultValue = "true") boolean includeHeaders,
                                @Nullable String rateLimiter) {
    }

    /**
     * The arguments of the {@code RequestSize} filter.
     *
     * @param maxSize The maximum size of the body, e.g. {@code 5MB}; 5 MB by default
     */
    @Introspected
    record RequestSizeArguments(@ReadableBytes @Bindable(defaultValue = "5MB") long maxSize) {
    }

    /**
     * The arguments of the {@code RequestHeaderSize} filter.
     *
     * @param maxSize  The maximum size of the headers, e.g. {@code 16KB}
     * @param maxCount The maximum number of headers, {@code 0} for no limit
     */
    @Introspected
    record RequestHeaderSizeArguments(@ReadableBytes @Bindable(defaultValue = "16000") long maxSize,
                                      @Bindable(defaultValue = "0") int maxCount) {
    }

    /**
     * The arguments of the {@code CircuitBreaker} filter.
     *
     * @param name             The name of the circuit
     * @param fallback         The name of the fallback handler bean, if any
     * @param failureStatuses  The statuses that count as a failure, codes or names; empty for 500 and above
     * @param fallbackStatuses The statuses the fallback answers for, codes or names
     * @param fallbackUri      The URI of an upstream the requests fail over to, instead of a fallback bean
     * @param maxFallbackBody  The bytes of a body kept for the fallback, e.g. {@code 64KB}; 1 MiB for
     *                         a fallback URI, none otherwise, by default
     */
    @Introspected
    record CircuitBreakerArguments(String name,
                                   @Nullable String fallback,
                                   @Bindable(defaultValue = "") List<String> failureStatuses,
                                   @Bindable(defaultValue = "") List<String> fallbackStatuses,
                                   @Nullable String fallbackUri,
                                   @ReadableBytes @Nullable Long maxFallbackBody) {
    }

    /**
     * Filters applied as one, e.g. the headers of {@code AddRequestHeadersIfNotPresent}.
     *
     * @param filters The filters
     */
    private record CompositeFilter(List<io.micronaut.http.gateway.GatewayFilter> filters) implements io.micronaut.http.gateway.GatewayFilter {
        @Override
        public void filterRequest(io.micronaut.http.gateway.ProxyExchange exchange, io.micronaut.http.MutableHttpRequest<?> request) throws Exception {
            for (io.micronaut.http.gateway.GatewayFilter filter : filters) {
                filter.filterRequest(exchange, request);
            }
        }

        @Override
        public void filterResponse(io.micronaut.http.gateway.ProxyExchange exchange, io.micronaut.http.MutableHttpResponse<?> response) throws Exception {
            for (io.micronaut.http.gateway.GatewayFilter filter : filters) {
                filter.filterResponse(exchange, response);
            }
        }

        @Override
        public void configure(RouteFilterSpec<?> route) {
            for (io.micronaut.http.gateway.GatewayFilter filter : filters) {
                filter.configure(route);
            }
        }

        @Override
        public String toString() {
            return filters.toString();
        }
    }
}
