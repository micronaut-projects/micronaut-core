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
package io.micronaut.http.gateway;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.exceptions.NoAvailableServiceException;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableByteBodyHttpResponse;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.PathVariables;
import io.micronaut.http.UpgradedHttpResponse;
import io.micronaut.http.body.AvailableByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.ReplayableByteBody;
import io.micronaut.http.MutableByteBodyHttpResponse;
import io.micronaut.http.UpgradedHttpResponse;
import io.micronaut.http.client.AsyncRawHttpClient;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.RawHttpClientSupport;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.http.client.exceptions.UnprocessedRequestException;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.ServerResponseAttributes;
import io.micronaut.http.util.HttpHeadersUtil;
import io.micronaut.retry.CircuitBreakerGuard;
import io.micronaut.retry.RetryOperations;
import io.micronaut.retry.exception.CircuitOpenException;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The default {@link ProxyHandler}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultProxyHandler implements ProxyHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ProxyHandler.class);
    private static final int MAX_SELECTIONS = 16;
    private static final String H2C = "h2c";
    private static final List<String> FORWARDED_NAMES = List.of(
        HttpHeaders.FORWARDED,
        "X-Forwarded-For",
        "X-Forwarded-Proto",
        "X-Forwarded-Host",
        "X-Forwarded-Port",
        "X-Forwarded-Prefix",
        "X-Real-IP"
    );

    private static final String UPSTREAM_OF_ROUTE = "The upstream of the gateway route ";

    private final GatewayRoute route;
    private final ProxyHandlerFactory factory;
    /**
     * The relays to the shadows of the mirrors of the route.
     */
    private final java.util.Map<GatewayFilters.Mirror, ProxyHandler> mirrorHandlers = new java.util.concurrent.ConcurrentHashMap<>();
    private final RawRequestOptions options;
    private final RawRequestOptions retainHostOptions;
    @Nullable
    private final RetryOperations retryOperations;
    @Nullable
    private final CircuitBreakerGuard guard;
    private final List<GatewayPathPattern> patterns;
    private final AtomicReference<@Nullable AsyncRawHttpClient> client = new AtomicReference<>();
    private final AtomicReference<@Nullable AsyncRawHttpClient> upgradeClient = new AtomicReference<>();

    DefaultProxyHandler(GatewayRoute route, ProxyHandlerFactory factory) {
        this.route = route;
        this.factory = factory;
        List<GatewayPathPattern> parsed = new ArrayList<>(route.getPaths().size());
        for (String path : route.getPaths()) {
            parsed.add(GatewayPathPattern.parse(path));
        }
        this.patterns = List.copyOf(parsed);
        RawRequestOptions.Builder builder = RawRequestOptions.proxy().toBuilder()
            // the retry policy of the route decides on retries, not the client
            .retryStaleConnection(false)
            // a body no attempt read can be sent again, see RequestBody.giveBack
            .returnUnsentBody(true)
            .responseTimeout(route.getResponseTimeout())
            .activityTimeout(route.getActivityTimeout());
        this.options = builder.build();
        this.retainHostOptions = options.toBuilder().retainHostHeader(true).build();
        GatewayRetry retry = route.getRetry();
        this.retryOperations = retry == null ? null
            : factory.retryOperationsFactory().createRetryOperations(retry.toPolicy(RetryableAttempt.class::isInstance));
        GatewayCircuitBreaker circuitBreaker = route.getCircuitBreaker();
        this.guard = circuitBreaker == null ? null : factory.circuitBreakerRegistry().guard(circuitBreaker.name());
    }

    @Override
    public GatewayRoute getRoute() {
        return route;
    }

    @Override
    public CompletionStage<? extends HttpResponse<?>> handle(HttpRequest<?> request, PathVariables pathVariables) {
        // a failover of another route relays the request that route prepared, with its filters,
        // and keeps the context of that route, e.g. its failure and the state of its filters
        FallbackRequest failover = request instanceof FallbackRequest fallbackRequest && fallbackRequest.prepared() != null ? fallbackRequest : null;
        GatewayRequestContext context = failover == null ? GatewayRequestContext.of(request, route)
            : GatewayRequestContext.find(request).orElseGet(() -> GatewayRequestContext.of(request, route));
        String rejected = hostRejection(request);
        if (rejected == null) {
            rejected = ProxyPaths.upstreamRejection(request.getPath());
        }
        if (rejected != null) {
            return CompletableFuture.failedFuture(new HttpStatusException(HttpStatus.BAD_REQUEST, rejected));
        }
        ProxyExchange exchange = new ProxyExchange(request, pathVariables, route.getId(), route.getUri(), factory.isXForwarded(), patterns);
        MutableHttpRequest<Object> upstream;
        try {
            upstream = failover == null ? upstreamRequest(request) : Objects.requireNonNull(failover.prepared());
            for (GatewayFilter filter : route.getFilters()) {
                filter.filterRequest(exchange, upstream);
            }
            rejected = upstreamPathRejection(upstream.getUri());
            if (exchange.isRetainHostHeader()) {
                // the Host that a filter kept or set, e.g. from a path variable, is sent as it is
                String hostRejected = hostRejection(upstream);
                if (hostRejected != null) {
                    return CompletableFuture.failedFuture(new HttpStatusException(HttpStatus.BAD_REQUEST, hostRejected));
                }
            }
        } catch (ProxyPaths.UpstreamPathException e) {
            return CompletableFuture.failedFuture(new UpstreamException(HttpStatus.BAD_GATEWAY, route.getId(),
                "The filters of the route made an upstream path that is not relayed: " + e.getMessage(), null));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
        if (rejected != null) {
            return CompletableFuture.failedFuture(new UpstreamException(HttpStatus.BAD_GATEWAY, route.getId(),
                "The filters of the route made an upstream path that is not relayed: " + rejected, null));
        }
        for (GatewayFilters.Mirror mirror : GatewayFilters.mirrors(route.getFilters())) {
            if (mirror.percentage() > 0 && java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 100 < mirror.percentage()) {
                mirror(request, pathVariables, mirror);
            }
        }
        List<GatewayFilters.ModifyRequestBody> requestRewrites = GatewayFilters.requestRewrites(route.getFilters());
        if (!requestRewrites.isEmpty()) {
            // the whole body is read, rewritten and sent again: the request no longer streams
            return rewriteRequestBody(request, exchange, upstream, requestRewrites)
                .thenCompose(rewritten -> new Relay(request, pathVariables, exchange, upstream, RequestBody.prepared(request, rewritten)).start());
        }
        RequestBody body;
        try {
            long sizeLimit = GatewayFilters.maxRequestSize(route.getFilters());
            if (failover != null && failover.sizeLimit() >= 0) {
                // the limit of the route that failed over still holds, e.g. while its circuit is open
                sizeLimit = sizeLimit < 0 ? failover.sizeLimit() : Math.min(sizeLimit, failover.sizeLimit());
            }
            body = RequestBody.of(request, route.getRetry(), route.getCircuitBreaker(), sizeLimit);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        Relay relay = new Relay(request, context, pathVariables, exchange, upstream, body);
        CompletableFuture<HttpResponse<?>> result = relay.start();
        if (request instanceof ServerHttpRequest<?> server && !result.isDone()) {
            // a client that goes away cancels the relay: the attempt in flight, its connection, its
            // permits and the retries still to come
            server.whenAbandoned(() -> result.cancel(false));
        }
        return result;
    }

    /**
     * Send a copy of the request to a shadow, and discard its response. Never fails and never
     * waits: the request goes on as if there were no mirror.
     */
    private void mirror(HttpRequest<?> request, PathVariables pathVariables, GatewayFilters.Mirror mirror) {
        try {
            if (request.getContentLength() > mirror.maxBodyBytes() || !(request instanceof ServerHttpRequest<?> server)) {
                return;
            }
            ProxyHandler shadow = mirrorHandlers.computeIfAbsent(mirror, this::mirrorHandler);
            ByteBodyFactory factory = server.byteBodyFactory();
            // a copy at the pace of the fastest reader: the upstream of the route is never slowed down
            CloseableByteBody copy = server.byteBody().split(ByteBody.SplitBackpressureMode.FASTEST);
            factory.limit(copy, mirror.maxBodyBytes()).buffer().whenComplete((bytes, failure) -> {
                if (failure != null) {
                    // over the limit, or the request failed: nothing is mirrored
                    return;
                }
                MirrorRequest copied = new MirrorRequest(request, bytes);
                CompletionStage<? extends HttpResponse<?>> relayed;
                try {
                    relayed = shadow.handle(copied, pathVariables);
                } catch (Exception e) {
                    copied.release();
                    LOG.debug("The mirror {} of the gateway route {} failed", mirror.uri(), route.getId(), e);
                    return;
                }
                relayed.whenComplete((response, error) -> {
                    copied.release();
                    if (response instanceof ByteBodyHttpResponse<?> byteBodyResponse) {
                        // the response of the shadow is discarded
                        byteBodyResponse.close();
                    }
                    if (error != null && LOG.isDebugEnabled()) {
                        LOG.debug("The mirror {} of the gateway route {} failed: {}", mirror.uri(), route.getId(), error.toString());
                    }
                });
            });
        } catch (RuntimeException e) {
            LOG.debug("The mirror {} of the gateway route {} failed", mirror.uri(), route.getId(), e);
        }
    }

    /**
     * The relay to a shadow: the proxy transforms of the route, no retry, no circuit breaker,
     * and the timeout of the mirror.
     */
    private ProxyHandler mirrorHandler(GatewayFilters.Mirror mirror) {
        List<GatewayFilter> transforms = new ArrayList<>();
        for (GatewayFilter filter : route.getFilters()) {
            if (!(filter instanceof GatewayFilters.Mirror) && !(filter instanceof GatewayFilters.ModifyResponseBody)) {
                transforms.add(filter);
            }
        }
        if (mirror.shadowHost()) {
            transforms.add(new GatewayFilter() {
                @Override
                public void filterRequest(ProxyExchange exchange, MutableHttpRequest<?> upstreamRequest) {
                    URI uri = mirror.uri();
                    String host = uri.getHost() + "-shadow" + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
                    upstreamRequest.getHeaders().set(HttpHeaders.HOST, host);
                    exchange.setRetainHostHeader(true);
                }
            });
        }
        return factory.create(GatewayRoute.builder(route.getId() + "-mirror")
            .uri(mirror.uri())
            .filters(transforms)
            .responseTimeout(mirror.timeout())
            .build());
    }

    private static ByteBodyFactory bodyFactory(HttpRequest<?> request) {
        return request instanceof ServerHttpRequest<?> server ? server.byteBodyFactory() : ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
    }

    private CompletionStage<CloseableByteBody> rewriteRequestBody(HttpRequest<?> request, ProxyExchange exchange, MutableHttpRequest<Object> upstream,
                                                                   List<GatewayFilters.ModifyRequestBody> rewrites) {
        CloseableByteBody claimed;
        try {
            claimed = RawHttpClientSupport.claimServerRequestBody(request);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        ByteBodyFactory factory = bodyFactory(request);
        if (claimed == null) {
            claimed = factory.createEmpty();
        }
        long max = Long.MAX_VALUE;
        List<BodyRewriter> rewriters = new ArrayList<>(rewrites.size());
        for (GatewayFilters.ModifyRequestBody rewrite : rewrites) {
            max = Math.min(max, rewrite.maxBytes());
            rewriters.add(rewrite.rewriter());
        }
        return BodyCodings.rewrite(factory, claimed, max, upstream.getHeaders(), exchange, rewriters, HttpStatus.REQUEST_ENTITY_TOO_LARGE)
            .thenApply(body -> body);
    }

    /**
     * The URI of an upstream request is a path and a query: a URI with a scheme or an authority,
     * e.g. a path that a filter left with an empty leading segment, {@code //attacker.example/x},
     * would send the request to another server.
     *
     * @param uri The URI of the upstream request
     * @return The reason it is rejected, or {@code null}
     */
    private static @Nullable String upstreamPathRejection(URI uri) {
        if (uri.getScheme() != null || uri.getRawAuthority() != null) {
            return "The upstream path is not a path";
        }
        String rawPath = uri.getRawPath();
        return rawPath == null ? "The path must start with a slash" : ProxyPaths.upstreamRejection(rawPath);
    }

    /**
     * The {@code Host} of a request a gateway relays: at most one, and a host with an optional
     * port only, so that it cannot be read as another target, e.g. {@code evil.example/path} or
     * {@code user@evil.example}.
     *
     * @param request The request
     * @return The reason it is rejected, or {@code null}
     */
    static @Nullable String hostRejection(HttpRequest<?> request) {
        List<String> hosts = request.getHeaders().getAll(HttpHeaders.HOST);
        if (hosts.size() > 1) {
            return "Several Host headers";
        }
        if (hosts.isEmpty()) {
            return null;
        }
        return RelayHeaders.hostRejection(hosts.get(0));
    }

    /**
     * @param request The inbound request
     * @return The upstream request: the method, raw path and query, and the end-to-end headers
     */
    private MutableHttpRequest<Object> upstreamRequest(HttpRequest<?> request) {
        URI uri = request.getUri();
        MutableHttpRequest<Object> upstream = HttpRequest.create(request.getMethod(),
            ProxyPaths.uri(request.getPath(), uri.getRawQuery()).toString(), request.getMethodName());
        MutableHttpHeaders headers = upstream.getHeaders();
        request.getHeaders().forEach((name, values) -> {
            for (String value : values) {
                headers.add(name, value);
            }
        });
        List<String> upgrade = isUpgrade(request) ? allowedUpgradeProtocols(request) : List.of();
        HttpHeadersUtil.stripHopByHopHeaders(headers);
        removeForwardedAliases(headers);
        // e.g. the stream id of an inbound HTTP/2 request, which an HTTP/1.1 upstream would read as a header
        RelayHeaders.removeTransportHeaders(headers);
        if (!upgrade.isEmpty()) {
            // the only hop-by-hop headers a relay passes on: the next hop switches protocols too
            headers.set(HttpHeaders.CONNECTION, "Upgrade");
            for (String value : upgrade) {
                headers.add(HttpHeaders.UPGRADE, value);
            }
        }
        factory.forwardedHeaders().write(request, upstream);
        return upstream;
    }

    /**
     * Remove the hop-by-hop headers of a response. A {@code 101} keeps {@code Upgrade} and
     * {@code Connection: Upgrade}, which switch the connection of the client too, and a
     * {@code 426} keeps {@code Upgrade}, which names the protocols the upstream requires.
     *
     * @param response The response of the upstream
     */
    static void stripHopByHopHeaders(MutableHttpResponse<?> response) {
        MutableHttpHeaders headers = response.getHeaders();
        int code = response.code();
        List<String> upgrade = code == HttpStatus.SWITCHING_PROTOCOLS.getCode() || code == HttpStatus.UPGRADE_REQUIRED.getCode()
            ? headers.getAll(HttpHeaders.UPGRADE)
            : List.of();
        HttpHeadersUtil.stripHopByHopHeaders(headers);
        RelayHeaders.removeTransportHeaders(headers);
        if (!upgrade.isEmpty()) {
            // the Netty HTTP/2 codec drops Connection for a client on HTTP/2
            headers.set(HttpHeaders.CONNECTION, "Upgrade");
            for (String value : upgrade) {
                headers.add(HttpHeaders.UPGRADE, value);
            }
        }
    }

    /**
     * Remove the headers whose names are forwarded headers written with underscores, e.g.
     * {@code X_Forwarded_For}, which some servers read as the forwarded headers.
     *
     * @param headers The headers of the upstream request
     */
    private static void removeForwardedAliases(MutableHttpHeaders headers) {
        List<String> aliases = null;
        for (String name : headers.names()) {
            if (name.indexOf('_') >= 0) {
                String canonical = name.replace('_', '-');
                for (String forwarded : FORWARDED_NAMES) {
                    if (forwarded.equalsIgnoreCase(canonical)) {
                        if (aliases == null) {
                            aliases = new ArrayList<>(2);
                        }
                        aliases.add(name);
                    }
                }
            }
        }
        if (aliases != null) {
            for (String alias : aliases) {
                headers.remove(alias);
            }
        }
    }

    /**
     * @param request A request that asks to switch protocols
     * @return The protocols it asks for that the route relays, see
     * {@link GatewayRoute#getUpgradeProtocols()}: a request for another protocol is relayed as a
     * plain request
     */
    private List<String> allowedUpgradeProtocols(HttpRequest<?> request) {
        List<String> protocols = upgradeProtocols(request);
        Set<String> allowed = route.getUpgradeProtocols();
        List<String> relayed = new ArrayList<>(protocols.size());
        for (String protocol : protocols) {
            int version = protocol.indexOf('/');
            String name = (version < 0 ? protocol : protocol.substring(0, version)).toLowerCase(Locale.ROOT);
            if (allowed.contains(name)) {
                relayed.add(protocol);
            }
        }
        return relayed;
    }

    /**
     * @param request A request that asks to switch protocols
     * @return The protocols it asks for, but {@code h2c}: a relay never upgrades the connection to
     * the upstream to HTTP/2
     */
    private static List<String> upgradeProtocols(HttpRequest<?> request) {
        List<String> protocols = new ArrayList<>(1);
        for (String value : request.getHeaders().getAll(HttpHeaders.UPGRADE)) {
            for (String token : value.split(",")) {
                String protocol = token.strip();
                if (!protocol.isEmpty() && !protocol.equalsIgnoreCase(H2C)) {
                    protocols.add(protocol);
                }
            }
        }
        return protocols;
    }

    /**
     * @param request A request
     * @return Whether it asks to switch protocols: an {@code Upgrade} header, listed in the
     * {@code Connection} header, to another protocol than {@code h2c}
     */
    static boolean isUpgrade(HttpRequest<?> request) {
        HttpHeaders headers = request.getHeaders();
        if (!headers.contains(HttpHeaders.UPGRADE) || upgradeProtocols(request).isEmpty()) {
            return false;
        }
        for (String connection : headers.getAll(HttpHeaders.CONNECTION)) {
            for (String token : connection.split(",")) {
                if (token.strip().equalsIgnoreCase(HttpHeaders.UPGRADE)) {
                    return true;
                }
            }
        }
        return false;
    }

    private AsyncRawHttpClient client(boolean upgrade) {
        AtomicReference<@Nullable AsyncRawHttpClient> ref = upgrade ? upgradeClient : client;
        AsyncRawHttpClient c = ref.get();
        if (c == null) {
            synchronized (this) {
                c = ref.get();
                if (c == null) {
                    HttpVersionSelection version = upgrade ? HttpVersionSelection.forWebsocket() : factory.httpVersion(route);
                    c = factory.rawClient(route, version);
                    ref.set(c);
                }
            }
        }
        return c;
    }

    /**
     * @param failure A failure of an attempt
     * @return The exception the request fails with
     */
    private Throwable toFailure(Throwable failure) {
        Throwable cause = unwrap(failure);
        if (cause instanceof RetryableAttempt attempt) {
            cause = attempt.getCause() == null ? cause : attempt.getCause();
        }
        if (cause instanceof FilterFailure filterFailure && filterFailure.getCause() != null) {
            return filterFailure.getCause();
        }
        if (cause instanceof HttpStatusException) {
            return cause;
        }
        if (cause instanceof ContentLengthExceededException exceeded) {
            // the body of the client is over the limit of the route
            return new HttpStatusException(HttpStatus.REQUEST_ENTITY_TOO_LARGE, Objects.requireNonNullElse(exceeded.getMessage(), "The request body is too large"));
        }
        String id = route.getId();
        if (cause instanceof UpstreamStatusFailure statusFailure) {
            // a retried status whose retries the total delay stopped: its status, the response is gone
            // a status that HttpStatus does not name, e.g. 599, is a bad gateway
            HttpStatus status = knownStatus(statusFailure.getStatus());
            return new UpstreamException(status, id, UPSTREAM_OF_ROUTE + id + " answered " + statusFailure.getStatus(), cause);
        }
        if (cause instanceof CircuitOpenException) {
            return new UpstreamException(HttpStatus.SERVICE_UNAVAILABLE, id, "The circuit breaker of the gateway route " + id + " is open", cause);
        }
        if (cause instanceof NoAvailableServiceException) {
            return new UpstreamException(HttpStatus.SERVICE_UNAVAILABLE, id, "No available instance of the upstream of the gateway route " + id, cause);
        }
        if (cause instanceof ReadTimeoutException || cause instanceof TimeoutException
            || cause instanceof UnprocessedRequestException unprocessed && unprocessed.getReason() == UnprocessedRequestException.Reason.CONNECT_TIMEOUT) {
            return new UpstreamException(HttpStatus.GATEWAY_TIMEOUT, id, UPSTREAM_OF_ROUTE + id + " did not answer in time", cause);
        }
        // the cause names internal hosts and addresses: it is logged and kept, not sent to the client
        LOG.debug("The upstream of the gateway route {} failed", id, cause);
        return new UpstreamException(HttpStatus.BAD_GATEWAY, id, UPSTREAM_OF_ROUTE + id + " failed", cause);
    }

    /**
     * @param code A status code
     * @return Its status, or {@code 502} for a code that {@link HttpStatus} does not name
     */
    private static HttpStatus knownStatus(int code) {
        try {
            return HttpStatus.valueOf(code);
        } catch (IllegalArgumentException e) {
            return HttpStatus.BAD_GATEWAY;
        }
    }

    /**
     * @param failure A failure of an attempt
     * @return Whether the body of the client went over the limit of the route
     */
    private static boolean isBodyOverLimit(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof ContentLengthExceededException) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    /**
     * The request to send to an instance: an instance whose URI has a path, e.g.
     * {@code http://10.0.0.1:8080/orders}, gets the path before the path of the request, like
     * the base path of the URI of a route; otherwise the client resolves the request against the
     * instance, with the context path of the client of the service.
     *
     * @param upstream The upstream request
     * @param instance The instance, or {@code null}
     * @return The request to send
     */
    private static MutableHttpRequest<?> forInstance(MutableHttpRequest<Object> upstream, URI uri, @Nullable ServiceInstance instance) {
        // the attempts are sent one after the other: the upstream request is reused, not copied
        URI base = instance == null ? null : instance.getURI();
        String basePath = base == null ? null : base.getRawPath();
        if (base == null || basePath == null || basePath.isEmpty() || basePath.equals("/")) {
            return upstream.uri(uri);
        }
        return upstream.uri(URI.create(base.getScheme() + "://" + base.getRawAuthority()
            + ProxyPaths.prefix(basePath, uri.getRawPath())
            + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())));
    }

    /**
     * @param request The inbound request
     * @return The factory of the bodies of the server, or a heap one
     */
    static ByteBodyFactory bodyFactory(HttpRequest<?> request) {
        return request instanceof ServerHttpRequest<?> server
            ? server.byteBodyFactory() : ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
    }

    /**
     * @param publisher A publisher
     * @param <T>       The type of the items
     * @return Its first item, or {@code null} if it has none
     */
    static <T> CompletableFuture<@Nullable T> first(Publisher<T> publisher) {
        CompletableFuture<@Nullable T> future = new CompletableFuture<>();
        publisher.subscribe(new Subscriber<T>() {
            @Nullable
            private Subscription subscription;

            @Override
            public void onSubscribe(Subscription s) {
                subscription = s;
                s.request(1);
            }

            @Override
            public void onNext(T item) {
                if (future.complete(item) && subscription != null) {
                    subscription.cancel();
                }
            }

            @Override
            public void onError(Throwable t) {
                future.completeExceptionally(t);
            }

            @Override
            public void onComplete() {
                future.complete(null);
            }
        });
        return future;
    }

    @Override
    public String toString() {
        return "ProxyHandler{" + route.getId() + " -> " + RelayHeaders.redact(route.getUri()) + '}';
    }

    /**
     * The body of the inbound request, and whether it can be sent more than once. The body is
     * taken from the request by the first attempt that sends it: until then, e.g. while the
     * circuit is open, the request keeps it, so that a fallback can read it. A request without a
     * body keeps its empty body.
     */
    private static final class RequestBody {
        private final HttpRequest<?> request;
        /**
         * The number of bytes of a body that streams in that are kept to send it again, or
         * {@code -1} to keep none.
         */
        private final long replayLimit;
        /**
         * The maximum number of bytes of the body, or {@code -1} for no limit, see
         * {@link GatewayFilters#requestSize(long)}.
         */
        private final long sizeLimit;
        @Nullable
        private CloseableByteBody body;
        @Nullable
        private ReplayableByteBody replay;
        private boolean claimed;
        private boolean replayable = true;
        private boolean moved;

        private RequestBody(HttpRequest<?> request, long replayLimit, long sizeLimit) {
            this.request = request;
            this.replayLimit = replayLimit;
            this.sizeLimit = sizeLimit;
        }

        /**
         * @param request The request
         * @param retry   The retries of the route, if any: the body of a request whose method
         *                they send again is kept up to their limit
         * @param sizeLimit The maximum number of bytes of the body, {@code -1} for no limit
         * @return The body of the request
         */
        /**
         * @param request The request
         * @param body    The body to send instead of the one of the request, all there
         * @return The body of the request, which can be sent again
         */
        static RequestBody prepared(HttpRequest<?> request, CloseableByteBody body) {
            RequestBody prepared = new RequestBody(request, -1, -1);
            prepared.claimed = true;
            prepared.body = body;
            prepared.replayable = true;
            return prepared;
        }

        static RequestBody of(HttpRequest<?> request, @Nullable GatewayRetry retry, @Nullable GatewayCircuitBreaker circuitBreaker, long sizeLimit) {
            long replayLimit = retry != null && retry.retriesMethod(request.getMethodName()) ? retry.maxReplayBytes() : -1;
            if (circuitBreaker != null && circuitBreaker.fallback() != null) {
                // kept for the fallback too, e.g. a failover to another upstream
                replayLimit = Math.max(replayLimit, circuitBreaker.maxFallbackBodyBytes());
            }
            RequestBody body = new RequestBody(request, replayLimit, sizeLimit);
            if (request instanceof ServerHttpRequest<?> server && server.byteBody().expectedLength().orElse(-1) == 0
                && request.getBody().isEmpty()) {
                // nothing to send: the request keeps its empty body
                body.claimed = true;
            }
            return body;
        }

        private void claim() {
            claimed = true;
            CloseableByteBody taken = RawHttpClientSupport.claimServerRequestBody(request);
            if (taken == null) {
                if (request.getBody().isPresent()) {
                    throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "A gateway route relays the bytes of the body of a request, and a filter replaced the body");
                }
                return;
            }
            if (taken.expectedLength().orElse(-1) == 0) {
                taken.close();
                return;
            }
            ByteBodyFactory factory = bodyFactory(request);
            // the trailers of the client are sanitized like its headers
            taken = RelayHeaders.withSanitizedTrailers(factory, taken);
            if (sizeLimit >= 0 && !(taken instanceof AvailableByteBody available && available.length() <= sizeLimit)) {
                // fails the relay once more bytes than the limit arrive
                taken = factory.limit(taken, sizeLimit);
            }
            if (taken instanceof AvailableByteBody) {
                // the bytes are all there: a split is a reference, not a copy
                body = taken;
                replayable = true;
            } else if (replayLimit > 0 && taken.expectedLength().orElse(0) <= replayLimit) {
                // kept while it streams into the first attempt, up to the limit; a body longer
                // than the limit is moved instead, so that an attempt that did not read it, e.g.
                // of a refused connection, gives it back for the next one
                replay = factory.replayable(taken, replayLimit);
                replayable = false;
                GatewayRequestContext.find(request).ifPresent(GatewayRequestContext::keepBodyForReplay);
            } else {
                body = taken;
                replayable = false;
            }
        }

        /**
         * Take back the body an attempt did not read, see
         * {@link UnprocessedRequestException#takeUnsentBody()}: a body that cannot be replayed can
         * be sent again.
         *
         * @param unsent The body
         */
        synchronized void giveBack(CloseableByteBody unsent) {
            if (moved && body == null && replay == null && !replayable) {
                body = unsent;
                moved = false;
            } else {
                unsent.close();
            }
        }

        /**
         * @return Whether an attempt took the body from the request
         */
        synchronized boolean isClaimed() {
            // a request without a body has nothing an attempt could take
            return claimed && (body != null || replay != null || moved);
        }

        /**
         * @return Whether the body can be sent by an attempt after the first
         */
        synchronized boolean canResend() {
            if (replay != null) {
                return replay.isReplayable();
            }
            // a streaming body that an attempt took is gone: a retry would send no body
            return !claimed || replayable || !moved;
        }

        /**
         * @return The body of the next attempt, or {@code null} for none
         */
        synchronized @Nullable CloseableByteBody next() {
            if (!claimed) {
                claim();
            }
            if (replay != null) {
                // the bytes from the start, or a failure once more than the limit arrived
                moved = true;
                return replay.next();
            }
            if (body == null) {
                return null;
            }
            if (replayable) {
                return body.split();
            }
            if (moved) {
                throw new IllegalStateException("The body of the request was already sent");
            }
            moved = true;
            CloseableByteBody b = body;
            body = null;
            return b;
        }

        /**
         * @param limit The number of bytes kept for the fallback, see
         *              {@link GatewayCircuitBreaker#maxFallbackBodyBytes()}
         * @return The bytes of the body for a fallback, when an attempt took them and they were
         * kept, or {@code null}: the request still has them, or they are gone
         */
        synchronized @Nullable CloseableByteBody forFallback(long limit) {
            if (!claimed || limit <= 0) {
                return null;
            }
            if (replay != null) {
                if (!replay.isReplayable()) {
                    return null;
                }
                // the replay may keep more bytes than the fallback, e.g. for the retries
                CloseableByteBody replayed = replay.next();
                long length = replayed.expectedLength().orElse(-1);
                if (length > limit) {
                    replayed.close();
                    return null;
                }
                if (length < 0) {
                    ByteBodyFactory factory = request instanceof ServerHttpRequest<?> server
                        ? server.byteBodyFactory() : ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
                    return factory.limit(replayed, limit);
                }
                return replayed;
            }
            if (body instanceof AvailableByteBody available && available.length() <= limit) {
                return body.split();
            }
            return null;
        }

        synchronized void release() {
            CloseableByteBody b = body;
            body = null;
            if (b != null) {
                b.close();
            }
            ReplayableByteBody r = replay;
            replay = null;
            if (r != null) {
                r.close();
            }
        }
    }

    /**
     * The relay of one request: its attempts, and the response.
     */
    private final class Relay {
        private final HttpRequest<?> request;
        private final GatewayRequestContext context;
        private final PathVariables pathVariables;
        private final ProxyExchange exchange;
        private final MutableHttpRequest<Object> upstream;
        private final RequestBody body;
        private final boolean upgrade;
        private final URI upstreamUri;
        private final List<URI> tried = new ArrayList<>(2);
        private final AtomicInteger attempts = new AtomicInteger();
        private final AtomicReference<@Nullable CompletableFuture<?>> current = new AtomicReference<>();
        private final AtomicReference<@Nullable CompletableFuture<?>> retrying = new AtomicReference<>();
        private final AtomicReference<@Nullable CompletableFuture<?>> fallingBack = new AtomicReference<>();
        /**
         * The response of a status that is retried, kept until the next attempt starts: when the
         * retries stop, e.g. at the total delay, it is relayed instead of a status without its
         * headers and body.
         */
        private final AtomicReference<@Nullable HttpResponse<?>> held = new AtomicReference<>();
        private final CompletableFuture<HttpResponse<?>> result = new CompletableFuture<>();

        Relay(HttpRequest<?> request, GatewayRequestContext context, PathVariables pathVariables, ProxyExchange exchange, MutableHttpRequest<Object> upstream, RequestBody body) {
            this.request = request;
            this.context = context;
            this.pathVariables = pathVariables;
            this.exchange = exchange;
            this.upstream = upstream;
            this.body = body;
            this.upgrade = isUpgrade(upstream);
            this.upstreamUri = upstream.getUri();
        }

        CompletableFuture<HttpResponse<?>> start() {
            // a cancel of the result, e.g. when the client disconnects, cancels the attempt, the
            // retry waiting for its delay, and releases the body kept for the retries
            result.whenComplete((response, failure) -> {
                if (result.isCancelled()) {
                    CompletableFuture<?> attempt = current.get();
                    if (attempt != null) {
                        attempt.cancel(false);
                    }
                    CompletableFuture<?> pending = retrying.get();
                    if (pending != null) {
                        pending.cancel(false);
                    }
                    CompletableFuture<?> fallback = fallingBack.get();
                    if (fallback != null) {
                        fallback.cancel(false);
                    }
                    closeHeld();
                    body.release();
                }
            });
            CompletionStage<HttpResponse<?>> relayed = retryOperations == null ? attempt() : retryOperations.executeCompletionStage(this::attempt);
            if (retryOperations != null) {
                retrying.set(relayed.toCompletableFuture());
                if (result.isCancelled()) {
                    relayed.toCompletableFuture().cancel(false);
                }
            }
            List<GatewayFilters.ModifyResponseBody> responseRewrites = GatewayFilters.responseRewrites(route.getFilters());
            if (!responseRewrites.isEmpty()) {
                relayed = relayed.thenCompose(response -> rewriteResponseBody(response, responseRewrites));
            }
            relayed.whenComplete((response, failure) -> {
                context.attempts(attempts.get());
                if (failure == null) {
                    body.release();
                    if (!result.complete(response) && response instanceof ByteBodyHttpResponse<?> byteBodyResponse) {
                        byteBodyResponse.close();
                    }
                } else if (result.isDone()) {
                    // an abandoned relay has no one to answer: no fallback runs for it
                    closeHeld();
                    body.release();
                } else if (held.get() != null && unwrap(failure) instanceof RetryableAttempt) {
                    // the retries of a status stopped: the last response is the answer
                    body.release();
                    HttpResponse<?> last = held.getAndSet(null);
                    try {
                        HttpResponse<?> relayedResponse = relay(Objects.requireNonNull(last));
                        if (!result.complete(relayedResponse) && relayedResponse instanceof ByteBodyHttpResponse<?> byteBodyResponse) {
                            byteBodyResponse.close();
                        }
                    } catch (RuntimeException e) {
                        fail(e);
                    }
                } else {
                    closeHeld();
                    // the fallback takes its copy of the body before the kept bytes go
                    try {
                        fail(failure);
                    } catch (RuntimeException e) {
                        // never leave the request without a response
                        result.completeExceptionally(e);
                    } finally {
                        body.release();
                    }
                }
            });
            return result;
        }

        private void closeHeld() {
            HttpResponse<?> last = held.getAndSet(null);
            if (last instanceof ByteBodyHttpResponse<?> byteBodyResponse) {
                byteBodyResponse.close();
            }
        }

        private void fail(Throwable failure) {
            Throwable cause = toFailure(failure);
            GatewayCircuitBreaker circuitBreaker = route.getCircuitBreaker();
            AsyncRequestHandlerFallback fallback = circuitBreaker == null || circuitBreaker.fallback() == null ? null
                : new AsyncRequestHandlerFallback(Objects.requireNonNull(circuitBreaker.fallback()));
            Throwable reason = unwrap(failure);
            if (reason instanceof RetryableAttempt attempt && attempt.getCause() != null) {
                reason = attempt.getCause();
            }
            // for the fallback, and for the error routes and the filters of the route
            context.failure(reason);
            if (fallback == null || !isUpstreamFailure(reason)) {
                // a failure of the gateway or of the client, e.g. of a filter of the response
                // after the upstream answered, is answered as it is: the fallback stands in for
                // the upstream only
                result.completeExceptionally(cause);
                return;
            }
            CloseableByteBody kept = body.forFallback(Objects.requireNonNull(circuitBreaker).maxFallbackBodyBytes());
            boolean failover = fallback.handler() instanceof ProxyHandler || fallback.handler() instanceof FailoverHandler;
            if (kept == null && failover && body.isClaimed()) {
                // a failover relays the body, which an attempt took and nothing kept within the
                // limit of the fallback: the failure of the upstream is the answer, not a failure
                // to read the body again
                result.completeExceptionally(cause);
                return;
            }
            FallbackRequest fallbackRequest;
            if (failover) {
                // the request the route prepared, with the credentials, the path and the
                // headers its filters set, and the limit of its body
                fallbackRequest = new FallbackRequest(request, kept, preparedCopy(), GatewayFilters.maxRequestSize(route.getFilters()));
            } else {
                fallbackRequest = kept == null ? null : new FallbackRequest(request, kept);
            }
            CompletableFuture<? extends HttpResponse<?>> stage = fallback.handle(fallbackRequest == null ? request : fallbackRequest, pathVariables).toCompletableFuture();
            fallingBack.set(stage);
            if (result.isCancelled()) {
                stage.cancel(false);
            }
            stage.whenComplete((response, error) -> {
                if (fallbackRequest != null) {
                    fallbackRequest.release();
                }
                if (error != null) {
                    result.completeExceptionally(unwrap(error));
                } else if (!result.complete(response) && response instanceof ByteBodyHttpResponse<?> byteBodyResponse) {
                    // the relay was cancelled: nothing takes the response of the fallback
                    byteBodyResponse.close();
                }
            });
        }

        /**
         * @return A copy of the upstream request as the filters of the route prepared it, for a
         * failover
         */
        private MutableHttpRequest<Object> preparedCopy() {
            MutableHttpRequest<Object> copy = HttpRequest.create(upstream.getMethod(), upstreamUri.toString(), upstream.getMethodName());
            MutableHttpHeaders headers = copy.getHeaders();
            upstream.getHeaders().forEach((name, values) -> {
                for (String value : values) {
                    headers.add(name, value);
                }
            });
            return copy;
        }

        /**
         * @param reason The failure of the relay
         * @return Whether it is a failure of the upstream, which a fallback answers: not one of a
         * filter, of the request of the client or of the gateway itself
         */
        private static boolean isUpstreamFailure(Throwable reason) {
            // a body over the limit of the route is a failure of the client: a failover would
            // send it on
            return !(reason instanceof FilterFailure) && !(reason instanceof HttpStatusException) && !isBodyOverLimit(reason);
        }

        /**
         * One attempt: select the instance, acquire the circuit, send, and decide what a failure
         * or a status means.
         */
        private CompletionStage<HttpResponse<?>> attempt() {
            int attempt = attempts.incrementAndGet();
            // the next attempt runs: the response kept from the previous one is not the answer
            closeHeld();
            if (attempt > 1 && !body.canResend()) {
                // never reached: a body that cannot be sent again makes a failure final
                return CompletableFuture.failedFuture(new IllegalStateException("The body of the request cannot be sent again"));
            }
            if (result.isDone()) {
                return CompletableFuture.failedFuture(new IllegalStateException("The relay was cancelled"));
            }
            CompletableFuture<@Nullable ServiceInstance> selected = route.isLoadBalanced() ? select() : CompletableFuture.completedFuture(null);
            return selected.thenCompose(instance -> {
                if (result.isDone()) {
                    // cancelled while the instance was selected
                    release(instance);
                    return CompletableFuture.failedFuture(new IllegalStateException("The relay was cancelled"));
                }
                return send(attempt, instance);
            });
        }

        private CompletionStage<HttpResponse<?>> send(int attempt, @Nullable ServiceInstance instance) {
            URI target = instance == null ? route.getUri() : instance.getURI();
            if (instance != null) {
                tried.add(instance.getURI());
            }
            context.upstream(target);
            exchange.target(target);
            // the permit is taken right before the exchange, which always reports its outcome, and
            // before the body is taken from the request: while the circuit is open, a fallback
            // can still read it
            CircuitBreakerGuard.Permit permit;
            try {
                permit = guard == null ? null : guard.acquire();
            } catch (RuntimeException open) {
                release(instance);
                return CompletableFuture.failedFuture(open);
            }
            CloseableByteBody attemptBody;
            try {
                attemptBody = body.next();
            } catch (RuntimeException e) {
                if (permit != null) {
                    // the upstream was not called: nothing to hold against it
                    permit.release();
                }
                release(instance);
                return CompletableFuture.failedFuture(e);
            }
            RawRequestOptions attemptOptions = instance == null
                ? (exchange.isRetainHostHeader() ? retainHostOptions : options)
                : options.toBuilder()
                    .retainHostHeader(exchange.isRetainHostHeader())
                    .serviceInstance(instance)
                    .build();
            CompletableFuture<HttpResponse<?>> exchanged;
            try {
                exchanged = client(upgrade).exchange(forInstance(upstream, upstreamUri, instance), attemptBody, attemptOptions).toCompletableFuture();
            } catch (RuntimeException e) {
                // nothing was sent: the selection and the permit are given back, not held against the upstream
                if (attemptBody != null) {
                    attemptBody.close();
                }
                if (permit != null) {
                    permit.release();
                }
                release(instance);
                return CompletableFuture.failedFuture(e);
            }
            current.set(exchanged);
            if (result.isCancelled()) {
                exchanged.cancel(false);
            }
            return exchanged.handle((response, failure) -> {
                if (failure != null) {
                    Throwable cause = unwrap(failure);
                    if (cause instanceof UnprocessedRequestException unprocessed) {
                        unprocessed.takeUnsentBody().ifPresent(body::giveBack);
                    }
                    if (permit != null) {
                        if (result.isCancelled()) {
                            // the client went away: nothing to hold against the upstream
                            permit.release();
                        } else if (isBodyOverLimit(cause)) {
                            // a failure of the client, not of the upstream: neither a failure nor
                            // a success, e.g. of a half-open circuit
                            permit.release();
                        } else {
                            permit.onFailure(cause);
                        }
                    }
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Attempt {} of the gateway route {} to {} failed: {}", attempt, route.getId(), target, cause.toString());
                    }
                    if (retries(attempt, cause)) {
                        throw new RetryableAttempt(cause);
                    }
                    throw cause instanceof RuntimeException re ? re : new CompletionException(cause);
                }
                int status = response.code();
                if (status < 100 || status > 999) {
                    // not a status of HTTP: nothing of this response can be relayed
                    if (response instanceof ByteBodyHttpResponse<?> byteBodyResponse) {
                        byteBodyResponse.close();
                    }
                    InvalidUpstreamResponse invalid = new InvalidUpstreamResponse("The upstream answered the status " + status);
                    if (permit != null) {
                        permit.onFailure(invalid);
                    }
                    throw invalid;
                }
                GatewayCircuitBreaker circuitBreaker = route.getCircuitBreaker();
                if (permit != null && circuitBreaker != null) {
                    if (circuitBreaker.isFailure(status)) {
                        permit.onFailure(new UpstreamStatusFailure(status));
                    } else {
                        permit.onSuccess();
                    }
                }
                GatewayRetry retry = route.getRetry();
                if (retry != null && attempt <= retry.retries() && body.canResend()
                    && retry.retriesMethod(request.getMethodName()) && (retry.retriesStatus(status) || retry.retriesHeaders(response.getHeaders()))) {
                    if (circuitBreaker != null && circuitBreaker.fallsBackOn(status)) {
                        // the fallback answers once the retries stop
                        if (response instanceof ByteBodyHttpResponse<?> byteBodyResponse) {
                            byteBodyResponse.close();
                        }
                    } else {
                        // kept until the next attempt starts, see held
                        HttpResponse<?> previous = held.getAndSet(response);
                        if (previous instanceof ByteBodyHttpResponse<?> byteBodyResponse) {
                            byteBodyResponse.close();
                        }
                    }
                    throw new RetryableAttempt(new UpstreamStatusFailure(status));
                }
                if (circuitBreaker != null && circuitBreaker.fallsBackOn(status)) {
                    // the fallback answers: nothing of this response is relayed
                    if (response instanceof ByteBodyHttpResponse<?> byteBodyResponse) {
                        byteBodyResponse.close();
                    }
                    throw new UpstreamStatusFailure(status);
                }
                return relay(response);
            });
        }

        /**
         * @param attempt The number of the attempt that failed
         * @param failure Its failure, before the headers of a response
         * @return Whether to send the request again
         */
        private boolean retries(int attempt, Throwable failure) {
            if (isBodyOverLimit(failure)) {
                return false;
            }
            GatewayRetry retry = route.getRetry();
            if (retry == null || attempt > retry.retries() || !body.canResend() || result.isDone()) {
                return false;
            }
            if (failure instanceof CircuitOpenException) {
                return false;
            }
            if (UnprocessedRequestException.isUnprocessed(failure)) {
                return true;
            }
            if (failure instanceof ReadTimeoutException timeout && timeout.isHeadersReceived()) {
                return false;
            }
            return retry.retriesMethod(request.getMethodName()) && retry.retriesException(failure);
        }

        /**
         * Rewrite the body of a relayed response, see {@link GatewayFilters#modifyResponseBody}.
         */
        private CompletionStage<HttpResponse<?>> rewriteResponseBody(HttpResponse<?> response, List<GatewayFilters.ModifyResponseBody> rewrites) {
            int status = response.code();
            if (!(response instanceof MutableByteBodyHttpResponse<?> mutable) || UpgradedHttpResponse.unwrap(response) != null
                || request.getMethodName().equals("HEAD") || status < 200 || status == 204 || status == 304) {
                return CompletableFuture.completedFuture(response);
            }
            long max = Long.MAX_VALUE;
            List<BodyRewriter> rewriters = new ArrayList<>(rewrites.size());
            for (GatewayFilters.ModifyResponseBody rewrite : rewrites) {
                max = Math.min(max, rewrite.maxBytes());
                rewriters.add(rewrite.rewriter());
            }
            CloseableByteBody bytes = mutable.byteBody().move();
            return BodyCodings.rewrite(bodyFactory(request), bytes, max, mutable.getHeaders(), exchange, rewriters, HttpStatus.BAD_GATEWAY)
                .handle((rewritten, failure) -> {
                    if (failure != null) {
                        mutable.close();
                        Throwable cause = unwrap(failure);
                        throw cause instanceof RuntimeException re ? re : new CompletionException(cause);
                    }
                    MutableByteBodyHttpResponse<?> replaced = MutableByteBodyHttpResponse.of(mutable, rewritten);
                    replaced.setAttribute(ServerResponseAttributes.SKIP_COMPRESSION, true);
                    return replaced;
                });
        }

        private HttpResponse<?> relay(HttpResponse<?> response) {
            MutableHttpResponse<?> mutable = RawHttpClientSupport.toMutableResponse(response);
            if (mutable instanceof MutableByteBodyHttpResponse<?> withBody && response.code() != HttpStatus.SWITCHING_PROTOCOLS.getCode()
                && UpgradedHttpResponse.unwrap(response) == null) {
                // the trailers of the upstream are sanitized like its headers
                io.micronaut.http.body.ByteBody bytes = withBody.byteBody();
                if (!(bytes instanceof AvailableByteBody) && bytes.expectedLength().isEmpty()) {
                    mutable = MutableByteBodyHttpResponse.of(withBody, RelayHeaders.withSanitizedTrailers(bodyFactory(request), bytes.move()));
                }
            }
            try {
                // the upstream decided whether to compress the body: relayed as it is
                mutable.setAttribute(ServerResponseAttributes.SKIP_COMPRESSION, true);
                stripHopByHopHeaders(mutable);
                for (GatewayFilter filter : route.getFilters()) {
                    filter.filterResponse(exchange, mutable);
                }
                return mutable;
            } catch (Exception e) {
                // the body may have moved to the response with the sanitized trailers
                if (mutable instanceof ByteBodyHttpResponse<?> byteBodyResponse) {
                    byteBodyResponse.close();
                }
                if (response != mutable && response instanceof ByteBodyHttpResponse<?> byteBodyResponse) {
                    byteBodyResponse.close();
                }
                // an error of the gateway, not of the upstream
                throw new FilterFailure(e);
            }
        }

        /**
         * Select an instance of the service, one not tried yet if the load balancer gives one.
         */
        private CompletableFuture<@Nullable ServiceInstance> select() {
            LoadBalancer loadBalancer;
            try {
                loadBalancer = factory.loadBalancer(route);
            } catch (RuntimeException e) {
                return CompletableFuture.failedFuture(e);
            }
            CompletableFuture<@Nullable ServiceInstance> selected = new CompletableFuture<>();
            selectNot(loadBalancer, selected, null, 0);
            return selected;
        }

        private void selectNot(LoadBalancer loadBalancer, CompletableFuture<@Nullable ServiceInstance> selected, @Nullable ServiceInstance first, int selections) {
            // the key of the request, e.g. for a sticky load balancer, as the client of the service selects with
            first(loadBalancer.select(factory.loadBalancerDiscriminator(route, request))).whenComplete((instance, failure) -> {
                if (failure != null) {
                    if (first != null) {
                        selected.complete(first);
                    } else {
                        selected.completeExceptionally(unwrap(failure));
                    }
                } else if (instance == null) {
                    release(loadBalancer, first);
                    selected.completeExceptionally(new NoAvailableServiceException(route.getServiceId()));
                } else if (!tried.contains(instance.getURI())) {
                    // a selection that is not sent is released, e.g. for the exchanges in flight of p2c
                    release(loadBalancer, first);
                    selected.complete(instance);
                } else if (first == null && selections + 1 >= Math.min(MAX_SELECTIONS, tried.size() + 1)) {
                    // every instance the load balancer gives was tried: try one again
                    selected.complete(instance);
                } else if (selections + 1 >= Math.min(MAX_SELECTIONS, tried.size() + 1)) {
                    release(loadBalancer, instance);
                    selected.complete(first);
                } else {
                    if (first != null) {
                        release(loadBalancer, instance);
                    }
                    selectNot(loadBalancer, selected, first == null ? instance : first, selections + 1);
                }
            });
        }

        /**
         * Release a selected instance that no exchange is sent to: no outcome is held against it.
         *
         * @param instance The instance, or {@code null}
         */
        private void release(@Nullable ServiceInstance instance) {
            if (instance == null) {
                return;
            }
            try {
                release(factory.loadBalancer(route), instance);
            } catch (RuntimeException e) {
                LOG.debug("The selection of {} for the gateway route {} could not be released", instance.getURI(), route.getId(), e);
            }
        }

        private static void release(LoadBalancer loadBalancer, @Nullable ServiceInstance instance) {
            if (instance != null) {
                loadBalancer.report(instance, LoadBalancer.Outcome.CANCELLED);
            }
        }
    }

    /**
     * An attempt that is sent again, with its failure as the cause.
     */
    static final class RetryableAttempt extends RuntimeException {
        RetryableAttempt(Throwable cause) {
            super(cause.getMessage(), cause, false, false);
        }
    }

    /**
     * A failure of a filter of the route on the response: an error of the gateway, answered like
     * an exception of a route, not as a failure of the upstream.
     */
    static final class FilterFailure extends RuntimeException {
        FilterFailure(Throwable cause) {
            super(cause.getMessage(), cause, false, false);
        }
    }

    /**
     * A response that is not one of HTTP, e.g. of a status out of range.
     */
    static final class InvalidUpstreamResponse extends RuntimeException {
        InvalidUpstreamResponse(String message) {
            super(message, null, false, false);
        }
    }

    /**
     * A response of a status that the circuit breaker counts as a failure, or that a retry sends
     * again.
     */
    static final class UpstreamStatusFailure extends RuntimeException {
        private final int status;

        UpstreamStatusFailure(int status) {
            super("The upstream answered " + status, null, false, false);
            this.status = status;
        }

        int getStatus() {
            return status;
        }
    }

    /**
     * Calls a fallback, turning an exception it throws into a failed stage.
     *
     * @param handler The fallback
     */
    private record AsyncRequestHandlerFallback(io.micronaut.web.router.builder.AsyncRequestHandler handler) {
        CompletionStage<? extends HttpResponse<?>> handle(HttpRequest<?> request, PathVariables pathVariables) {
            try {
                return Objects.requireNonNull(handler.handle(request, pathVariables), "The fallback returned null");
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }
    }
}
