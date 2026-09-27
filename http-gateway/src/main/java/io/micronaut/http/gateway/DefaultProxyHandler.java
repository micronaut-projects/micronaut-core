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
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.exceptions.NoAvailableServiceException;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.PathVariables;
import io.micronaut.http.body.AvailableByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.client.AsyncRawHttpClient;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.RawHttpClientSupport;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.http.client.exceptions.UnprocessedRequestException;
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
import java.util.Objects;
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

    private final GatewayRoute route;
    private final ProxyHandlerFactory factory;
    private final RawRequestOptions options;
    @Nullable
    private final RetryOperations retryOperations;
    @Nullable
    private final CircuitBreakerGuard guard;
    private final List<GatewayPathPattern> patterns;
    @Nullable
    private volatile AsyncRawHttpClient client;
    @Nullable
    private volatile AsyncRawHttpClient upgradeClient;

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
            .responseTimeout(route.getResponseTimeout())
            .activityTimeout(route.getActivityTimeout());
        this.options = builder.build();
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
        request.setAttribute(GatewayAttributes.ROUTE_ID, route.getId());
        String rejected = hostRejection(request);
        if (rejected == null) {
            rejected = ProxyPaths.rejection(request.getPath());
        }
        if (rejected != null) {
            return CompletableFuture.failedFuture(new HttpStatusException(HttpStatus.BAD_REQUEST, rejected));
        }
        ProxyExchange exchange = new ProxyExchange(request, pathVariables, route.getId(), route.getUri(), factory.isXForwarded(), patterns);
        MutableHttpRequest<Object> upstream;
        try {
            upstream = upstreamRequest(request);
            for (GatewayFilter filter : route.getFilters()) {
                filter.filterRequest(exchange, upstream);
            }
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
        rejected = ProxyPaths.rejection(upstream.getUri().getRawPath());
        if (rejected != null) {
            return CompletableFuture.failedFuture(new UpstreamException(HttpStatus.BAD_GATEWAY, route.getId(),
                "The filters of the route made an upstream path that is not relayed: " + rejected, null));
        }
        RequestBody body;
        try {
            body = RequestBody.of(request);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        Relay relay = new Relay(request, pathVariables, exchange, upstream, body);
        return relay.start();
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
        String host = hosts.get(0);
        if (host.isEmpty()) {
            return "An empty Host header";
        }
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            boolean allowed = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'
                || c == '.' || c == '-' || c == '_' || c == ':' || c == '[' || c == ']' || c == '%' || c == '~';
            if (!allowed) {
                return "An invalid Host header";
            }
        }
        return null;
    }

    /**
     * @param request The inbound request
     * @return The upstream request: the method, raw path and query, and the end-to-end headers
     */
    private MutableHttpRequest<Object> upstreamRequest(HttpRequest<?> request) {
        URI uri = request.getUri();
        MutableHttpRequest<Object> upstream = HttpRequest.create(request.getMethod(),
            ProxyPaths.pathAndQuery(request.getPath(), uri.getRawQuery()), request.getMethodName());
        MutableHttpHeaders headers = upstream.getHeaders();
        request.getHeaders().forEach((name, values) -> {
            for (String value : values) {
                headers.add(name, value);
            }
        });
        List<String> upgrade = isUpgrade(request) ? upgradeProtocols(request) : List.of();
        HttpHeadersUtil.stripHopByHopHeaders(headers);
        removeForwardedAliases(headers);
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
        if (!upgrade.isEmpty()) {
            if (code == HttpStatus.SWITCHING_PROTOCOLS.getCode()) {
                headers.set(HttpHeaders.CONNECTION, "Upgrade");
            }
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
        AsyncRawHttpClient c = upgrade ? upgradeClient : client;
        if (c == null) {
            synchronized (this) {
                c = upgrade ? upgradeClient : client;
                if (c == null) {
                    HttpVersionSelection version = upgrade ? HttpVersionSelection.forWebsocket() : factory.httpVersion(route);
                    c = factory.rawClient(route, version);
                    if (upgrade) {
                        upgradeClient = c;
                    } else {
                        client = c;
                    }
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
        String id = route.getId();
        if (cause instanceof CircuitOpenException) {
            return new UpstreamException(HttpStatus.SERVICE_UNAVAILABLE, id, "The circuit breaker of the gateway route " + id + " is open", cause);
        }
        if (cause instanceof NoAvailableServiceException) {
            return new UpstreamException(HttpStatus.SERVICE_UNAVAILABLE, id, "No available instance of the upstream of the gateway route " + id, cause);
        }
        if (cause instanceof ReadTimeoutException || cause instanceof TimeoutException
            || cause instanceof UnprocessedRequestException unprocessed && unprocessed.getReason() == UnprocessedRequestException.Reason.CONNECT_TIMEOUT) {
            return new UpstreamException(HttpStatus.GATEWAY_TIMEOUT, id, "The upstream of the gateway route " + id + " did not answer in time", cause);
        }
        return new UpstreamException(HttpStatus.BAD_GATEWAY, id, "The upstream of the gateway route " + id + " failed: " + cause.getMessage(), cause);
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
    private static MutableHttpRequest<?> forInstance(MutableHttpRequest<Object> upstream, @Nullable ServiceInstance instance) {
        if (instance == null) {
            return upstream;
        }
        URI base = instance.getURI();
        String basePath = base.getRawPath();
        if (basePath == null || basePath.isEmpty() || basePath.equals("/")) {
            return upstream;
        }
        URI uri = upstream.getUri();
        String absolute = base.getScheme() + "://" + base.getRawAuthority()
            + ProxyPaths.prefix(basePath, uri.getRawPath())
            + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        MutableHttpRequest<Object> copy = HttpRequest.create(upstream.getMethod(), absolute, upstream.getMethodName());
        MutableHttpHeaders headers = copy.getHeaders();
        upstream.getHeaders().forEach((name, values) -> {
            for (String value : values) {
                headers.add(name, value);
            }
        });
        return copy;
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
        return "ProxyHandler{" + route.getId() + " -> " + route.getUri() + '}';
    }

    /**
     * The body of the inbound request, and whether it can be sent more than once. The body is
     * taken from the request by the first attempt that sends it: until then, e.g. while the
     * circuit is open, the request keeps it, so that a fallback can read it. A request without a
     * body keeps its empty body.
     */
    private static final class RequestBody {
        private final HttpRequest<?> request;
        @Nullable
        private CloseableByteBody body;
        private boolean claimed;
        private boolean replayable = true;
        private boolean moved;

        private RequestBody(HttpRequest<?> request) {
            this.request = request;
        }

        static RequestBody of(HttpRequest<?> request) {
            RequestBody body = new RequestBody(request);
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
            // the bytes are all there: a split is a reference, not a copy
            body = taken;
            replayable = taken instanceof AvailableByteBody;
        }

        /**
         * @return Whether the body can be sent by an attempt after the first
         */
        synchronized boolean canResend() {
            return !claimed || body == null || replayable;
        }

        /**
         * @return The body of the next attempt, or {@code null} for none
         */
        synchronized @Nullable CloseableByteBody next() {
            if (!claimed) {
                claim();
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

        synchronized void release() {
            CloseableByteBody b = body;
            body = null;
            if (b != null) {
                b.close();
            }
        }
    }

    /**
     * The relay of one request: its attempts, and the response.
     */
    private final class Relay {
        private final HttpRequest<?> request;
        private final PathVariables pathVariables;
        private final ProxyExchange exchange;
        private final MutableHttpRequest<Object> upstream;
        private final RequestBody body;
        private final boolean upgrade;
        private final List<URI> tried = new ArrayList<>(2);
        private final AtomicInteger attempts = new AtomicInteger();
        private final AtomicReference<@Nullable CompletableFuture<?>> current = new AtomicReference<>();
        private final CompletableFuture<HttpResponse<?>> result = new CompletableFuture<>();

        Relay(HttpRequest<?> request, PathVariables pathVariables, ProxyExchange exchange, MutableHttpRequest<Object> upstream, RequestBody body) {
            this.request = request;
            this.pathVariables = pathVariables;
            this.exchange = exchange;
            this.upstream = upstream;
            this.body = body;
            this.upgrade = isUpgrade(upstream);
        }

        CompletableFuture<HttpResponse<?>> start() {
            // a cancel of the result, e.g. when the client disconnects, cancels the attempt
            result.whenComplete((response, failure) -> {
                if (result.isCancelled()) {
                    CompletableFuture<?> attempt = current.get();
                    if (attempt != null) {
                        attempt.cancel(false);
                    }
                }
            });
            CompletionStage<HttpResponse<?>> relayed = retryOperations == null ? attempt() : retryOperations.executeCompletionStage(this::attempt);
            relayed.whenComplete((response, failure) -> {
                body.release();
                request.setAttribute(GatewayAttributes.ATTEMPTS, attempts.get());
                if (failure == null) {
                    if (!result.complete(response) && response instanceof ByteBodyHttpResponse<?> byteBodyResponse) {
                        byteBodyResponse.close();
                    }
                } else {
                    fail(failure);
                }
            });
            return result;
        }

        private void fail(Throwable failure) {
            Throwable cause = toFailure(failure);
            GatewayCircuitBreaker circuitBreaker = route.getCircuitBreaker();
            AsyncRequestHandlerFallback fallback = circuitBreaker == null || circuitBreaker.fallback() == null ? null
                : new AsyncRequestHandlerFallback(Objects.requireNonNull(circuitBreaker.fallback()));
            if (fallback == null) {
                result.completeExceptionally(cause);
                return;
            }
            Throwable reason = unwrap(failure);
            if (reason instanceof RetryableAttempt attempt && attempt.getCause() != null) {
                reason = attempt.getCause();
            }
            request.setAttribute(GatewayAttributes.FAILURE, reason);
            fallback.handle(request, pathVariables).whenComplete((response, error) -> {
                if (error != null) {
                    result.completeExceptionally(unwrap(error));
                } else {
                    result.complete(response);
                }
            });
        }

        /**
         * One attempt: select the instance, acquire the circuit, send, and decide what a failure
         * or a status means.
         */
        private CompletionStage<HttpResponse<?>> attempt() {
            int attempt = attempts.incrementAndGet();
            if (attempt > 1 && !body.canResend()) {
                // never reached: a body that cannot be sent again makes a failure final
                return CompletableFuture.failedFuture(new IllegalStateException("The body of the request cannot be sent again"));
            }
            if (result.isDone()) {
                return CompletableFuture.failedFuture(new IllegalStateException("The relay was cancelled"));
            }
            CompletableFuture<@Nullable ServiceInstance> selected = route.isLoadBalanced() ? select() : CompletableFuture.completedFuture(null);
            return selected.thenCompose(instance -> send(attempt, instance));
        }

        private CompletionStage<HttpResponse<?>> send(int attempt, @Nullable ServiceInstance instance) {
            URI target = instance == null ? route.getUri() : instance.getURI();
            if (instance != null) {
                tried.add(instance.getURI());
            }
            request.setAttribute(GatewayAttributes.UPSTREAM, target);
            exchange.target(target);
            // the permit is taken right before the exchange, which always reports its outcome, and
            // before the body is taken from the request: while the circuit is open, a fallback
            // can still read it
            CircuitBreakerGuard.Permit permit;
            try {
                permit = guard == null ? null : guard.acquire();
            } catch (RuntimeException open) {
                return CompletableFuture.failedFuture(open);
            }
            CloseableByteBody attemptBody;
            try {
                attemptBody = body.next();
            } catch (RuntimeException e) {
                if (permit != null) {
                    // the upstream was not called: nothing to hold against it
                    permit.onSuccess();
                }
                return CompletableFuture.failedFuture(e);
            }
            RawRequestOptions attemptOptions = options.toBuilder()
                .retainHostHeader(exchange.isRetainHostHeader())
                .serviceInstance(instance)
                .build();
            CompletableFuture<HttpResponse<?>> exchanged;
            try {
                exchanged = client(upgrade).exchange(forInstance(upstream, instance), attemptBody, attemptOptions).toCompletableFuture();
            } catch (RuntimeException e) {
                if (attemptBody != null) {
                    attemptBody.close();
                }
                exchanged = CompletableFuture.failedFuture(e);
            }
            current.set(exchanged);
            if (result.isCancelled()) {
                exchanged.cancel(false);
            }
            return exchanged.handle((response, failure) -> {
                if (failure != null) {
                    Throwable cause = unwrap(failure);
                    if (permit != null) {
                        permit.onFailure(cause);
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
                    && retry.retriesMethod(request.getMethodName()) && retry.retriesStatus(status)) {
                    if (response instanceof ByteBodyHttpResponse<?> byteBodyResponse) {
                        byteBodyResponse.close();
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
            return retry.retriesMethod(request.getMethodName());
        }

        private HttpResponse<?> relay(HttpResponse<?> response) {
            MutableHttpResponse<?> mutable = RawHttpClientSupport.toMutableResponse(response);
            try {
                // the upstream decided whether to compress the body: relayed as it is
                mutable.setAttribute(ServerResponseAttributes.SKIP_COMPRESSION, true);
                stripHopByHopHeaders(mutable);
                for (GatewayFilter filter : route.getFilters()) {
                    filter.filterResponse(exchange, mutable);
                }
                return mutable;
            } catch (Exception e) {
                if (response instanceof ByteBodyHttpResponse<?> byteBodyResponse) {
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
            first(loadBalancer.select()).whenComplete((instance, failure) -> {
                if (failure != null) {
                    if (first != null) {
                        selected.complete(first);
                    } else {
                        selected.completeExceptionally(unwrap(failure));
                    }
                } else if (instance == null) {
                    selected.completeExceptionally(new NoAvailableServiceException(route.getServiceId()));
                } else if (!tried.contains(instance.getURI())) {
                    selected.complete(instance);
                } else if (selections + 1 >= Math.min(MAX_SELECTIONS, tried.size() + 1)) {
                    // every instance the load balancer gives was tried: try one again
                    selected.complete(first == null ? instance : first);
                } else {
                    selectNot(loadBalancer, selected, first == null ? instance : first, selections + 1);
                }
            });
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
