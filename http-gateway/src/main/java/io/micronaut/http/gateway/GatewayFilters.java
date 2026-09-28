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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.PathVariables;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.util.ForwardedHeaders;
import io.micronaut.http.uri.UriTemplate;
import io.micronaut.web.router.builder.RouteFilterSpec;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.time.Duration;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The built-in {@link GatewayFilter gateway filters}, named after their Spring Cloud Gateway
 * equivalents. The path filters work on the raw, percent-encoded path, which they never decode:
 * the rest of the path and the query are relayed byte for byte.
 *
 * <p>A value of a header or a query parameter may use the path variables of the route, e.g.
 * {@code addRequestHeader("X-Segment", "{segment}")}; a value that would carry a control
 * character, e.g. a line break from a variable, answers {@code 400}.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class GatewayFilters {

    private static final String MASK = "******";
    private static final List<String> SENSITIVE_NAMES = List.of("auth", "cookie", "token", "secret", "password", "key", "credential", "session");
    private static final String ERROR_MESSAGE = "errorMessage";

    private static final Pattern VARIABLE = Pattern.compile("\\{([A-Za-z0-9_.-]+)}");
    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{(\\+)?([A-Za-z0-9_.-]+)}");

    private GatewayFilters() {
    }

    /**
     * The value of a header or a parameter in the description of a filter, e.g. in the gateway
     * endpoint or a log: masked for a name that carries credentials.
     *
     * @param name  The name
     * @param value The value
     * @return The value to show
     */
    static String shown(String name, String value) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String sensitive : SENSITIVE_NAMES) {
            if (lower.contains(sensitive)) {
                return MASK;
            }
        }
        return value;
    }

    // ---- path

    /**
     * Remove the first segments of the path, e.g. {@code stripPrefix(2)} sends {@code /a/b/c} as
     * {@code /c}; fewer segments than that leave {@code /}. The removed prefix is sent as
     * {@code X-Forwarded-Prefix}, appended to one of a trusted proxy, when the gateway sends the
     * {@code X-Forwarded-*} headers.
     *
     * @param parts The number of segments, at least 1
     * @return The filter
     */
    public static GatewayFilter stripPrefix(int parts) {
        if (parts < 1) {
            throw new IllegalArgumentException("StripPrefix removes at least one segment: " + parts);
        }
        return new StripPrefix(parts);
    }

    /**
     * Prepend a prefix to the path, e.g. {@code prefixPath("/v2")} sends {@code /orders} as
     * {@code /v2/orders}. The prefix may use the path variables of the route.
     *
     * @param prefix The prefix, a raw path
     * @return The filter
     */
    public static GatewayFilter prefixPath(String prefix) {
        return new PrefixPath(requirePath(prefix, "PrefixPath"));
    }

    /**
     * Replace the path with a URI template of the path variables of the route, e.g.
     * {@code setPath("/v2/{segment}")}. A variable is percent-encoded once; {@code {+path}}
     * keeps the reserved characters of its value.
     *
     * @param template The URI template of the path
     * @return The filter
     */
    public static GatewayFilter setPath(String template) {
        return new SetPath(requirePath(template, "SetPath"));
    }

    /**
     * Rewrite the raw path with a regular expression, e.g.
     * {@code rewritePath("/red/(?<segment>.*)", "/${segment}")}. The Spring Cloud Gateway form of
     * a group reference, {@code $\{segment}}, is accepted too.
     *
     * @param regex       The regular expression
     * @param replacement The replacement
     * @return The filter
     */
    public static GatewayFilter rewritePath(String regex, String replacement) {
        return new RewritePath(Pattern.compile(Objects.requireNonNull(regex, "regex")), Objects.requireNonNull(replacement, "replacement").replace("$\\", "$"));
    }

    // ---- request headers and parameters

    /**
     * @param name  The name of the header
     * @param value The value to add
     * @return A filter that adds a value to a header of the upstream request
     */
    public static GatewayFilter addRequestHeader(String name, String value) {
        return new RequestHeader(Edit.ADD, requireHeaderName(name), requireHeaderValue(value));
    }

    /**
     * @param name  The name of the header
     * @param value The value
     * @return A filter that adds a header to the upstream request unless it has one
     */
    public static GatewayFilter addRequestHeaderIfNotPresent(String name, String value) {
        return new RequestHeader(Edit.ADD_IF_ABSENT, requireHeaderName(name), requireHeaderValue(value));
    }

    /**
     * @param name  The name of the header
     * @param value The value
     * @return A filter that replaces a header of the upstream request
     */
    public static GatewayFilter setRequestHeader(String name, String value) {
        return new RequestHeader(Edit.SET, requireHeaderName(name), requireHeaderValue(value));
    }

    /**
     * @param name The name of the header
     * @return A filter that removes a header from the upstream request
     */
    public static GatewayFilter removeRequestHeader(String name) {
        return new RequestHeader(Edit.REMOVE, requireHeaderName(name), "");
    }

    /**
     * @param from The header to copy the values of
     * @param to   The header to add them to
     * @return A filter that copies the values of a header of the upstream request to another
     */
    public static GatewayFilter mapRequestHeader(String from, String to) {
        return new MapRequestHeader(requireHeaderName(from), requireHeaderName(to));
    }

    /**
     * @param name  The name of the parameter
     * @param value The value
     * @return A filter that appends a parameter to the query of the upstream request, leaving
     * the rest of the query as it is
     */
    public static GatewayFilter addRequestParameter(String name, String value) {
        return new AddRequestParameter(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
    }

    /**
     * @param name The decoded name of the parameter
     * @return A filter that removes a parameter from the query of the upstream request, leaving
     * the rest of the query as it is
     */
    public static GatewayFilter removeRequestParameter(String name) {
        return new RemoveRequestParameter(Objects.requireNonNull(name, "name"));
    }

    /**
     * Send the {@code Host} of the inbound request to the upstream, rather than the host of the
     * upstream.
     *
     * @return The filter
     */
    public static GatewayFilter preserveHostHeader() {
        return PreserveHostHeader.INSTANCE;
    }

    /**
     * @param host The {@code Host} to send to the upstream, e.g. {@code api.example.com}
     * @return A filter that sets the {@code Host} of the upstream request
     */
    public static GatewayFilter setRequestHostHeader(String host) {
        return new SetRequestHostHeader(requireHeaderValue(host));
    }

    // ---- response headers

    /**
     * @param name  The name of the header
     * @param value The value to add
     * @return A filter that adds a value to a header of the response
     */
    public static GatewayFilter addResponseHeader(String name, String value) {
        return new ResponseHeader(Edit.ADD, requireHeaderName(name), requireHeaderValue(value));
    }

    /**
     * @param name  The name of the header
     * @param value The value
     * @return A filter that replaces a header of the response
     */
    public static GatewayFilter setResponseHeader(String name, String value) {
        return new ResponseHeader(Edit.SET, requireHeaderName(name), requireHeaderValue(value));
    }

    /**
     * @param name The name of the header
     * @return A filter that removes a header from the response
     */
    public static GatewayFilter removeResponseHeader(String name) {
        return new ResponseHeader(Edit.REMOVE, requireHeaderName(name), "");
    }

    /**
     * @param name        The name of the header
     * @param regex       A regular expression of the values
     * @param replacement The replacement
     * @return A filter that rewrites each value of a header of the response
     */
    public static GatewayFilter rewriteResponseHeader(String name, String regex, String replacement) {
        return new RewriteResponseHeader(requireHeaderName(name), Pattern.compile(regex), requireHeaderValue(replacement.replace("$\\", "$")));
    }

    /**
     * Keep one value of each of the headers of the response, e.g. of
     * {@code Access-Control-Allow-Origin} when the upstream and the gateway both set it.
     *
     * @param names    The names of the headers
     * @param strategy Which values to keep
     * @return The filter
     */
    public static GatewayFilter dedupeResponseHeader(List<String> names, DedupeStrategy strategy) {
        List<String> validated = new ArrayList<>(names.size());
        for (String name : names) {
            validated.add(requireHeaderName(name));
        }
        return new DedupeResponseHeader(List.copyOf(validated), Objects.requireNonNull(strategy, "strategy"));
    }

    /**
     * Rewrite a {@code Location} of the response that points to the upstream so that it points
     * to the gateway: the scheme and the authority of the inbound request replace those of the
     * upstream. A {@code Location} of another host, or a relative one, is kept.
     *
     * @return The filter
     */
    public static GatewayFilter rewriteLocationResponseHeader() {
        return RewriteLocationResponseHeader.INSTANCE;
    }

    /**
     * Add the security headers of a response that it does not have yet:
     * {@code X-Xss-Protection}, {@code Strict-Transport-Security}, {@code X-Frame-Options},
     * {@code X-Content-Type-Options}, {@code Referrer-Policy}, {@code Content-Security-Policy},
     * {@code X-Download-Options} and {@code X-Permitted-Cross-Domain-Policies}, with the values
     * of Spring Cloud Gateway. A route filter: it applies to every response of the route,
     * including an error of the gateway.
     *
     * @return The filter
     */
    public static GatewayFilter secureHeaders() {
        return SecureHeaders.INSTANCE;
    }

    // ---- route filters

    /**
     * Answer every request of the route with a status, e.g. of a response of the upstream or of
     * the gateway. A route filter.
     *
     * @param status The status
     * @return The filter
     */
    public static GatewayFilter setStatus(int status) {
        if (status < 200) {
            // an informational status is not the final response of a request
            throw new IllegalArgumentException("SetStatus needs a final status, 2xx to 5xx: " + status);
        }
        return new SetStatus(HttpStatus.valueOf(status));
    }

    /**
     * Answer every request of the route with a redirect, without calling the upstream. A route
     * filter.
     *
     * @param status   A redirect status, 3xx
     * @param location The location
     * @return The filter
     */
    public static GatewayFilter redirectTo(int status, String location) {
        if (status < 300 || status > 399) {
            throw new IllegalArgumentException("RedirectTo needs a 3xx status: " + status);
        }
        URI uri = URI.create(requireHeaderValue(location));
        return new RedirectTo(HttpStatus.valueOf(status), uri);
    }

    /**
     * Limit the size of the body of a request, like Spring Cloud Gateway's {@code RequestSize}: a
     * request whose {@code Content-Length} is over the limit is answered with {@code 413} and an
     * {@code errorMessage} header, without calling the upstream; a body that streams in fails the
     * relay with {@code 413} once more bytes than the limit arrive, if no response was relayed
     * yet. A route filter.
     *
     * @param maxBytes The maximum size of the body in bytes
     * @return The filter
     */
    public static GatewayFilter requestSize(long maxBytes) {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("RequestSize needs a size of at least 0: " + maxBytes);
        }
        return new RequestSize(maxBytes);
    }

    /**
     * Limit the headers of a request, like Spring Cloud Gateway's {@code RequestHeaderSize}: a
     * request whose headers are larger than the limit, the names and the values of all of them,
     * cookies included, or that has more headers than the count, is answered with {@code 431}
     * and an {@code errorMessage} header, without calling the upstream. A route filter.
     *
     * @param maxBytes The maximum size of the headers in bytes
     * @param maxCount The maximum number of header lines, {@code 0} for no limit
     * @return The filter
     */
    public static GatewayFilter requestHeaderSize(long maxBytes, int maxCount) {
        if (maxBytes < 0 || maxCount < 0) {
            throw new IllegalArgumentException("RequestHeaderSize needs a size and a count of at least 0: " + maxBytes + ", " + maxCount);
        }
        return new RequestHeaderSize(maxBytes, maxCount);
    }

    /**
     * Limit the requests of the route in flight per key, e.g. per client: a request over the
     * limit is answered with {@code 429} without calling the upstream. A request counts from the
     * route filters until the headers of its response, of the upstream, a fallback or an error.
     * A request without a key is not limited. A route filter.
     *
     * @param maxConcurrent The maximum number of requests of a key in flight, at least 1
     * @param keyResolver   The key of a request
     * @return The filter
     */
    public static GatewayFilter bulkhead(int maxConcurrent, KeyResolver keyResolver) {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("A bulkhead needs at least 1 request in flight: " + maxConcurrent);
        }
        return new Bulkhead(maxConcurrent, Objects.requireNonNull(keyResolver, "keyResolver"));
    }

    /**
     * The CORS policy of the route, like the per-route {@code cors} metadata of Spring Cloud
     * Gateway: the route has a {@code @CrossOrigin} with these settings, so that the CORS filter
     * of the server answers its preflight requests without calling the upstream, a forbidden
     * origin with {@code 403}, and adds {@code Access-Control-Allow-Origin} to the responses to an
     * allowed origin. The other routes keep their own policy. A route filter.
     *
     * @param allowedOrigins   The allowed origins, none for any
     * @param allowedMethods   The allowed methods, none for any
     * @param allowedHeaders   The allowed request headers, none for any
     * @param exposedHeaders   The response headers exposed to the client
     * @param allowCredentials Whether credentials are allowed
     * @param maxAge           How long a preflight response is cached, in seconds; {@code -1} for the default
     * @return The filter
     */
    public static GatewayFilter cors(List<String> allowedOrigins, List<String> allowedMethods, List<String> allowedHeaders,
                                     List<String> exposedHeaders, boolean allowCredentials, long maxAge) {
        return new Cors(List.copyOf(allowedOrigins), List.copyOf(allowedMethods), List.copyOf(allowedHeaders), List.copyOf(exposedHeaders),
            allowCredentials, maxAge);
    }

    /**
     * Mirror a percentage of the requests of the route to a shadow upstream, like Envoy's request
     * mirroring: the shadow gets the request as the proxy transforms of the route made it, with a
     * copy of the body, and its response is discarded. The copy is read at the pace of the
     * upstream of the route, never slower, up to a limit of 64 KiB: a body over the limit is not
     * mirrored, and the request is mirrored once its whole body arrived. The shadow is not
     * retried, and has a response timeout of 5 seconds. Nothing of the shadow, a failure, a
     * reset or a timeout, affects the request. A proxy transform.
     *
     * @param uri        The URI of the shadow upstream, see {@link GatewayRoute}
     * @param percentage The percentage of the requests to mirror, from 0 to 100
     * @return The filter
     */
    public static GatewayFilter mirror(String uri, double percentage) {
        return mirror(uri, percentage, 64 * 1024, Duration.ofSeconds(5), false);
    }

    /**
     * Mirror a percentage of the requests of the route to a shadow upstream, see
     * {@link #mirror(String, double)}.
     *
     * @param uri          The URI of the shadow upstream
     * @param percentage   The percentage of the requests to mirror, from 0 to 100
     * @param maxBodyBytes The limit of the body of a mirrored request
     * @param timeout      The response timeout of the shadow
     * @param shadowHost   Whether the {@code Host} of the shadow request gets a {@code -shadow}
     *                     suffix, like Envoy, e.g. {@code orders-shadow:8080}
     * @return The filter
     */
    public static GatewayFilter mirror(String uri, double percentage, long maxBodyBytes, Duration timeout, boolean shadowHost) {
        if (!(percentage >= 0 && percentage <= 100)) {
            throw new IllegalArgumentException("The percentage of a mirror is not between 0 and 100: " + percentage);
        }
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("The timeout of a mirror must be positive: " + timeout);
        }
        // validated like the URI of a route
        URI target = GatewayRoute.builder("mirror").uri(uri).build().getUri();
        return new Mirror(target, percentage, checkMax(maxBodyBytes, "Mirror"), timeout, shadowHost);
    }

    /**
     * @param filters The filters of a route
     * @return The mirrors among them
     */
    static List<Mirror> mirrors(List<GatewayFilter> filters) {
        List<Mirror> mirrors = new ArrayList<>(0);
        for (GatewayFilter filter : filters) {
            if (filter instanceof Mirror mirror) {
                mirrors.add(mirror);
            }
        }
        return mirrors;
    }

    /**
     * Cache the responses of the route, like Spring Cloud Gateway's {@code LocalResponseCache}:
     * a {@code 200} response to a {@code GET} is stored when its {@code Cache-Control} allows it,
     * fresh for its {@code s-maxage}, {@code max-age} or {@code Expires}, or for the time to live
     * when it has none, and answers the next {@code GET} and {@code HEAD} of the same URI, with
     * the same credentials and the same values of the headers of its {@code Vary}, with an
     * {@code Age}, or {@code 304} to a matching {@code If-None-Match} or
     * {@code If-Modified-Since}. The body is copied as it streams to the client, which is never
     * slowed down; an incomplete body, or one over the maximum size, is not stored. Not stored:
     * {@code no-store}, {@code private} and {@code no-cache} responses, responses with
     * {@code Set-Cookie} or {@code Vary: *}, and responses to a request with
     * {@code Authorization}, unless {@code public} or {@code s-maxage}. A request with
     * {@code no-cache} or {@code max-age=0} goes to the upstream, and one with {@code no-store}
     * is not stored; a {@code POST}, {@code PUT}, {@code PATCH} or {@code DELETE} of the URI
     * removes its responses. Protocol switches and {@code Range} requests are never cached. A
     * route filter.
     *
     * @param store         Where the responses are kept
     * @param timeToLive    How long a response without its own freshness is fresh; zero to
     *                      store only responses with their own freshness
     * @param maxEntryBytes The maximum size of the body of a stored response
     * @return The filter
     */
    public static GatewayFilter localResponseCache(ResponseCacheStore store, Duration timeToLive, long maxEntryBytes) {
        return new LocalResponseCache(store, timeToLive, maxEntryBytes, System::currentTimeMillis);
    }

    /**
     * A response cache in the memory of the gateway, see
     * {@link #localResponseCache(ResponseCacheStore, Duration, long)}.
     *
     * @param timeToLive How long a response without its own freshness is fresh
     * @param size       The capacity of the cache in bytes, also the maximum size of a response
     * @return The filter
     */
    public static GatewayFilter localResponseCache(Duration timeToLive, long size) {
        return localResponseCache(new InMemoryResponseCacheStore(size), timeToLive, size);
    }

    /**
     * Read the body of a request before the other filters of the route, up to a limit, like Spring
     * Cloud Gateway's {@code CacheRequestBody}: the filters find the bytes in
     * {@link GatewayRequestContext#cachedRequestBody()}, and the body is still relayed. A body over
     * the limit is answered with {@code 413}. A route filter.
     *
     * @param maxBytes The maximum size of the body
     * @return The filter
     */
    public static GatewayFilter cacheRequestBody(long maxBytes) {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("CacheRequestBody needs a size of at least 0: " + maxBytes);
        }
        return new CacheRequestBody(maxBytes);
    }

    /**
     * Rewrite the body of the request before it is relayed, like Spring Cloud Gateway's
     * {@code ModifyRequestBody}: the whole body, up to the limit, is read and decoded, rewritten,
     * encoded again and sent with its new {@code Content-Length}. The route no longer streams the
     * body of the request. A body over the limit is answered with {@code 413}. A proxy transform.
     *
     * @param maxBytes The limit of the body, encoded and decoded
     * @param rewriter The rewrite
     * @return The filter
     */
    public static GatewayFilter modifyRequestBody(long maxBytes, BodyRewriter rewriter) {
        return new ModifyRequestBody(checkMax(maxBytes, "ModifyRequestBody"), Objects.requireNonNull(rewriter, "rewriter"));
    }

    /**
     * Rewrite the body of the response of the upstream, like Spring Cloud Gateway's
     * {@code ModifyResponseBody}: the whole body, up to the limit, is read and decoded when the
     * upstream encoded it with {@code gzip} or {@code deflate}, rewritten, encoded again and sent
     * with its new {@code Content-Length}; its trailers are dropped. The route no longer streams
     * the body of the response. A body over the limit fails the request with {@code 502}. The
     * response of a {@code HEAD} request, a {@code 1xx}, {@code 204} or {@code 304}, and a protocol
     * switch are not rewritten. A proxy transform.
     *
     * @param maxBytes The limit of the body, encoded and decoded
     * @param rewriter The rewrite
     * @return The filter
     */
    public static GatewayFilter modifyResponseBody(long maxBytes, BodyRewriter rewriter) {
        return new ModifyResponseBody(checkMax(maxBytes, "ModifyResponseBody"), Objects.requireNonNull(rewriter, "rewriter"));
    }

    /**
     * Remove attributes from the JSON body of the response, like Spring Cloud Gateway's
     * {@code RemoveJsonAttributesResponseBody}: from the top-level object, or from every object of
     * the body when recursive. A body that is not JSON fails the request with {@code 502}. See
     * {@link #modifyResponseBody(long, BodyRewriter)}.
     *
     * @param mapper    The JSON mapper
     * @param maxBytes  The limit of the body
     * @param recursive Whether the attributes are removed from the nested objects too
     * @param names     The names of the attributes
     * @return The filter
     */
    public static GatewayFilter removeJsonAttributesResponseBody(io.micronaut.json.JsonMapper mapper, long maxBytes, boolean recursive, String... names) {
        Objects.requireNonNull(mapper, "mapper");
        Set<String> removed = Set.of(names);
        if (removed.isEmpty()) {
            throw new IllegalArgumentException("RemoveJsonAttributesResponseBody needs at least one attribute");
        }
        BodyRewriter rewriter = (exchange, headers, body) -> {
            if (body.length == 0) {
                return java.util.concurrent.CompletableFuture.completedFuture(body);
            }
            io.micronaut.json.tree.JsonNode tree;
            try {
                tree = mapper.readValue(body, io.micronaut.json.tree.JsonNode.class);
            } catch (java.io.IOException | RuntimeException e) {
                throw new HttpStatusException(HttpStatus.BAD_GATEWAY, "The body of the upstream is not JSON: " + e.getMessage());
            }
            if (tree == null) {
                return java.util.concurrent.CompletableFuture.completedFuture(body);
            }
            return java.util.concurrent.CompletableFuture.completedFuture(mapper.writeValueAsBytes(removeAttributes(tree, removed, recursive, true)));
        };
        return new ModifyResponseBody(checkMax(maxBytes, "RemoveJsonAttributesResponseBody"), rewriter) {
            @Override
            public String toString() {
                return "RemoveJsonAttributesResponseBody=" + String.join(", ", names) + (recursive ? ", true" : "");
            }
        };
    }

    /**
     * @param node      A node
     * @param names     The attributes to remove
     * @param recursive Whether they are removed from the nested objects too
     * @param here      Whether they are removed from this node, if it is an object, or from the
     *                  objects of this node, if it is an array
     * @return The node without the attributes
     */
    private static io.micronaut.json.tree.JsonNode removeAttributes(io.micronaut.json.tree.JsonNode node, Set<String> names, boolean recursive, boolean here) {
        if (node.isObject()) {
            Map<String, io.micronaut.json.tree.JsonNode> kept = new LinkedHashMap<>();
            for (Map.Entry<String, io.micronaut.json.tree.JsonNode> entry : node.entries()) {
                if (here && names.contains(entry.getKey())) {
                    continue;
                }
                kept.put(entry.getKey(), recursive ? removeAttributes(entry.getValue(), names, true, true) : entry.getValue());
            }
            return io.micronaut.json.tree.JsonNode.createObjectNode(kept);
        }
        if (node.isArray() && (recursive || here)) {
            List<io.micronaut.json.tree.JsonNode> items = new ArrayList<>(node.size());
            for (io.micronaut.json.tree.JsonNode item : node.values()) {
                items.add(removeAttributes(item, names, recursive, here));
            }
            return io.micronaut.json.tree.JsonNode.createArrayNode(items);
        }
        return node;
    }

    /**
     * Match the requests of the route by their body, like Spring Cloud Gateway's
     * {@code ReadBody} predicate: a copy of the body, up to the limit, is read and tested, and the
     * body is still relayed; the bytes are in the attribute
     * {@link GatewayAttributes#CACHED_REQUEST_BODY}. Unlike a predicate of the route, a request
     * whose body does not match is answered with {@code 404}: the router chose the route before
     * the body arrived, so no other route is tried. A body over the limit is answered with
     * {@code 413}. A route filter.
     *
     * @param maxBytes  The limit of the body
     * @param predicate The test of the body
     * @return The filter
     */
    public static GatewayFilter readBody(long maxBytes, java.util.function.Predicate<byte[]> predicate) {
        return new ReadBody(checkMax(maxBytes, "ReadBody"), Objects.requireNonNull(predicate, "predicate"));
    }

    private static long checkMax(long maxBytes, String filter) {
        if (maxBytes < 0) {
            throw new IllegalArgumentException(filter + " needs a size of at least 0: " + maxBytes);
        }
        return maxBytes;
    }

    /**
     * @param filters The filters of a route
     * @return The rewrites of the body of the request, in order
     */
    static List<ModifyRequestBody> requestRewrites(List<GatewayFilter> filters) {
        List<ModifyRequestBody> rewrites = new ArrayList<>(0);
        for (GatewayFilter filter : filters) {
            if (filter instanceof ModifyRequestBody rewrite) {
                rewrites.add(rewrite);
            }
        }
        return rewrites;
    }

    /**
     * @param filters The filters of a route
     * @return The rewrites of the body of the response, in order
     */
    static List<ModifyResponseBody> responseRewrites(List<GatewayFilter> filters) {
        List<ModifyResponseBody> rewrites = new ArrayList<>(0);
        for (GatewayFilter filter : filters) {
            if (filter instanceof ModifyResponseBody rewrite) {
                rewrites.add(rewrite);
            }
        }
        return rewrites;
    }

    /**
     * @param filters The filters of a route
     * @return The smallest {@link #requestSize(long) body limit} among them, or {@code -1} for none
     */
    static long maxRequestSize(List<GatewayFilter> filters) {
        long max = -1;
        for (GatewayFilter filter : filters) {
            if (filter instanceof RequestSize size && (max < 0 || size.maxBytes() < max)) {
                max = size.maxBytes();
            }
        }
        return max;
    }

    // ---- helpers

    private static String requirePath(String path, String filter) {
        Objects.requireNonNull(path, "path");
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException(filter + " needs a path starting with a slash: " + path);
        }
        return path;
    }

    private static String requireHeaderName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("A header name is empty");
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c <= ' ' || c >= 0x7f || "()<>@,;:\\\"/[]?={}".indexOf(c) >= 0) {
                throw new IllegalArgumentException("Not a header name: " + name);
            }
        }
        return name;
    }

    private static String requireHeaderValue(String value) {
        Objects.requireNonNull(value, "value");
        if (!isValidHeaderValue(value)) {
            throw new IllegalArgumentException("A header value has a control character: " + value.replace("\r", "\\r").replace("\n", "\\n"));
        }
        return value;
    }

    private static boolean isValidHeaderValue(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < ' ' && c != '\t' || c == 0x7f) {
                return false;
            }
        }
        return true;
    }

    /**
     * Replace the variables {@code {name}} of a value with the path variables of the route.
     *
     * @param value         The value
     * @param pathVariables The path variables
     * @return The value
     */
    static String expand(String value, PathVariables pathVariables) {
        if (value.indexOf('{') < 0) {
            return value;
        }
        Matcher matcher = VARIABLE.matcher(value);
        StringBuilder expanded = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String replacement = pathVariables.contains(name) ? pathVariables.getString(name, "") : matcher.group();
            matcher.appendReplacement(expanded, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(expanded);
        String result = expanded.toString();
        if (!isValidHeaderValue(result)) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "A path variable put a control character into a header or parameter");
        }
        return result;
    }

    /**
     * Replace the variables of a path template, {@code {name}} or {@code {+name}}, with their
     * raw values, as the client sent them, so a value is neither decoded nor encoded twice; a
     * variable without a raw value, e.g. of a route of the functional routes API, is its decoded
     * value, encoded once, keeping its slashes for {@code {+name}}.
     *
     * @param template The template
     * @param exchange The exchange
     * @return The path
     */
    static String expandPath(String template, ProxyExchange exchange) {
        if (template.indexOf('{') < 0) {
            return template;
        }
        Matcher matcher = PATH_VARIABLE.matcher(template);
        StringBuilder expanded = new StringBuilder();
        Map<String, String> raw = exchange.rawPathVariables();
        while (matcher.find()) {
            boolean reserved = matcher.group(1) != null;
            String name = matcher.group(2);
            String value = raw.get(name);
            if (value == null) {
                PathVariables variables = exchange.pathVariables();
                String decoded = variables.contains(name) ? variables.getString(name, "") : "";
                if (name.equals(GatewayRouteSpec.PATH_VARIABLE)) {
                    // the rest of a proxy route is raw already
                    value = decoded;
                } else {
                    value = UriTemplate.of(reserved ? "{+v}" : "{v}").expand(Map.of("v", decoded));
                }
                // a reserved expansion keeps ? and #, which would end the path: a client could
                // cut the upstream path and add a query
                value = value.replace("?", "%3F").replace("#", "%23");
            }
            matcher.appendReplacement(expanded, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(expanded);
        return expanded.toString();
    }

    private static MutableHttpRequest<?> withPath(MutableHttpRequest<?> request, String rawPath) {
        return request.uri(ProxyPaths.uri(rawPath, request.getUri().getRawQuery()));
    }

    private static MutableHttpRequest<?> withQuery(MutableHttpRequest<?> request, @Nullable String rawQuery) {
        return request.uri(ProxyPaths.uri(request.getUri().getRawPath(), rawQuery));
    }

    /**
     * The values of a header a {@link #dedupeResponseHeader(List, DedupeStrategy)} keeps.
     */
    public enum DedupeStrategy {
        /**
         * The first value.
         */
        RETAIN_FIRST,
        /**
         * The last value.
         */
        RETAIN_LAST,
        /**
         * Each distinct value once, in the order they first appear.
         */
        RETAIN_UNIQUE
    }

    private enum Edit {
        ADD, ADD_IF_ABSENT, SET, REMOVE
    }

    private record StripPrefix(int parts) implements GatewayFilter {
        @Override
        public void filterRequest(ProxyExchange exchange, MutableHttpRequest<?> request) {
            String path = request.getUri().getRawPath();
            String stripped = ProxyPaths.stripPrefix(path, parts);
            withPath(request, stripped);
            if (exchange.isXForwarded()) {
                String removed = path.length() > stripped.length() && path.endsWith(stripped)
                    ? path.substring(0, path.length() - stripped.length())
                    : path;
                if (removed.endsWith("/") && removed.length() > 1) {
                    removed = removed.substring(0, removed.length() - 1);
                }
                if (!removed.isEmpty() && !removed.equals("/")) {
                    MutableHttpHeaders headers = request.getHeaders();
                    String existing = headers.get(ForwardedHeaders.X_FORWARDED_PREFIX);
                    headers.set(ForwardedHeaders.X_FORWARDED_PREFIX, existing == null ? removed : existing + removed);
                }
            }
        }

        @Override
        public String toString() {
            return "StripPrefix=" + parts;
        }
    }

    private record PrefixPath(String prefix) implements GatewayFilter {
        @Override
        public void filterRequest(ProxyExchange exchange, MutableHttpRequest<?> request) {
            String expanded = expandPath(prefix, exchange);
            withPath(request, ProxyPaths.prefix(expanded, request.getUri().getRawPath()));
        }

        @Override
        public String toString() {
            return "PrefixPath=" + prefix;
        }
    }

    private record SetPath(String template) implements GatewayFilter {
        @Override
        public void filterRequest(ProxyExchange exchange, MutableHttpRequest<?> request) {
            String path = expandPath(template, exchange);
            withPath(request, path.startsWith("/") ? path : "/" + path);
        }

        @Override
        public String toString() {
            return "SetPath=" + template;
        }
    }

    private record RewritePath(Pattern regex, String replacement) implements GatewayFilter {
        @Override
        public void filterRequest(ProxyExchange exchange, MutableHttpRequest<?> request) {
            String rewritten = regex.matcher(request.getUri().getRawPath()).replaceAll(replacement);
            withPath(request, rewritten.isEmpty() ? "/" : rewritten);
        }

        @Override
        public String toString() {
            return "RewritePath=" + regex + ", " + replacement;
        }
    }

    private record RequestHeader(Edit edit, String name, String value) implements GatewayFilter {
        @Override
        public void filterRequest(ProxyExchange exchange, MutableHttpRequest<?> request) {
            MutableHttpHeaders headers = request.getHeaders();
            switch (edit) {
                case ADD -> headers.add(name, expand(value, exchange.pathVariables()));
                case ADD_IF_ABSENT -> {
                    if (!headers.contains(name)) {
                        headers.add(name, expand(value, exchange.pathVariables()));
                    }
                }
                case SET -> headers.set(name, expand(value, exchange.pathVariables()));
                case REMOVE -> headers.remove(name);
                default -> throw new IllegalStateException();
            }
        }

        @Override
        public String toString() {
            return switch (edit) {
                case ADD -> "AddRequestHeader=" + name + ", " + shown(name, value);
                case ADD_IF_ABSENT -> "AddRequestHeadersIfNotPresent=" + name + ":" + shown(name, value);
                case SET -> "SetRequestHeader=" + name + ", " + shown(name, value);
                case REMOVE -> "RemoveRequestHeader=" + name;
            };
        }
    }

    private record MapRequestHeader(String from, String to) implements GatewayFilter {
        @Override
        public void filterRequest(ProxyExchange exchange, MutableHttpRequest<?> request) {
            MutableHttpHeaders headers = request.getHeaders();
            for (String value : headers.getAll(from)) {
                headers.add(to, value);
            }
        }

        @Override
        public String toString() {
            return "MapRequestHeader=" + from + ", " + to;
        }
    }

    private record AddRequestParameter(String name, String value) implements GatewayFilter {
        @Override
        public void filterRequest(ProxyExchange exchange, MutableHttpRequest<?> request) {
            withQuery(request, ProxyPaths.addQueryParameter(request.getUri().getRawQuery(),
                expand(name, exchange.pathVariables()), expand(value, exchange.pathVariables())));
        }

        @Override
        public String toString() {
            return "AddRequestParameter=" + name + ", " + shown(name, value);
        }
    }

    private record RemoveRequestParameter(String name) implements GatewayFilter {
        @Override
        public void filterRequest(ProxyExchange exchange, MutableHttpRequest<?> request) {
            withQuery(request, ProxyPaths.removeQueryParameter(request.getUri().getRawQuery(), name));
        }

        @Override
        public String toString() {
            return "RemoveRequestParameter=" + name;
        }
    }

    private enum PreserveHostHeader implements GatewayFilter {
        INSTANCE;

        @Override
        public void filterRequest(ProxyExchange exchange, MutableHttpRequest<?> request) {
            String host = exchange.request().getHeaders().get(HttpHeaders.HOST);
            if (host != null) {
                request.getHeaders().set(HttpHeaders.HOST, host);
                exchange.setRetainHostHeader(true);
            }
        }

        @Override
        public String toString() {
            return "PreserveHostHeader";
        }
    }

    private record SetRequestHostHeader(String host) implements GatewayFilter {
        @Override
        public void filterRequest(ProxyExchange exchange, MutableHttpRequest<?> request) {
            request.getHeaders().set(HttpHeaders.HOST, expand(host, exchange.pathVariables()));
            exchange.setRetainHostHeader(true);
        }

        @Override
        public String toString() {
            return "SetRequestHostHeader=" + host;
        }
    }

    private record ResponseHeader(Edit edit, String name, String value) implements GatewayFilter {
        @Override
        public void filterResponse(ProxyExchange exchange, MutableHttpResponse<?> response) {
            MutableHttpHeaders headers = response.getHeaders();
            switch (edit) {
                case ADD -> headers.add(name, expand(value, exchange.pathVariables()));
                case SET -> headers.set(name, expand(value, exchange.pathVariables()));
                case REMOVE -> headers.remove(name);
                default -> throw new IllegalStateException();
            }
        }

        @Override
        public String toString() {
            return switch (edit) {
                case ADD -> "AddResponseHeader=" + name + ", " + shown(name, value);
                case SET -> "SetResponseHeader=" + name + ", " + shown(name, value);
                case REMOVE -> "RemoveResponseHeader=" + name;
                default -> edit + "ResponseHeader=" + name;
            };
        }
    }

    private record RewriteResponseHeader(String name, Pattern regex, String replacement) implements GatewayFilter {
        @Override
        public void filterResponse(ProxyExchange exchange, MutableHttpResponse<?> response) {
            MutableHttpHeaders headers = response.getHeaders();
            List<String> values = headers.getAll(name);
            if (values.isEmpty()) {
                return;
            }
            List<String> rewritten = new ArrayList<>(values.size());
            for (String value : values) {
                String result = regex.matcher(value).replaceAll(replacement);
                if (!isValidHeaderValue(result)) {
                    throw new IllegalStateException("RewriteResponseHeader made a header value with a control character");
                }
                rewritten.add(result);
            }
            headers.remove(name);
            for (String value : rewritten) {
                headers.add(name, value);
            }
        }

        @Override
        public String toString() {
            return "RewriteResponseHeader=" + name + ", " + regex + ", " + replacement;
        }
    }

    private record DedupeResponseHeader(List<String> names, DedupeStrategy strategy) implements GatewayFilter {
        @Override
        public void filterResponse(ProxyExchange exchange, MutableHttpResponse<?> response) {
            MutableHttpHeaders headers = response.getHeaders();
            for (String name : names) {
                List<String> values = new ArrayList<>();
                // Set-Cookie is not a list: its Expires attribute has a comma
                boolean list = !name.equalsIgnoreCase(HttpHeaders.SET_COOKIE);
                for (String value : headers.getAll(name)) {
                    // a header can be sent once with a list of values
                    for (String v : list ? value.split(",") : new String[]{value}) {
                        String stripped = v.strip();
                        if (!stripped.isEmpty()) {
                            values.add(stripped);
                        }
                    }
                }
                if (values.size() < 2) {
                    continue;
                }
                headers.remove(name);
                switch (strategy) {
                    case RETAIN_FIRST -> headers.add(name, values.get(0));
                    case RETAIN_LAST -> headers.add(name, values.get(values.size() - 1));
                    case RETAIN_UNIQUE -> {
                        Set<String> unique = new LinkedHashSet<>(values);
                        for (String value : unique) {
                            headers.add(name, value);
                        }
                    }
                    default -> throw new IllegalStateException();
                }
            }
        }

        @Override
        public String toString() {
            return "DedupeResponseHeader=" + String.join(" ", names) + ", " + strategy;
        }
    }

    private enum RewriteLocationResponseHeader implements GatewayFilter {
        INSTANCE;

        @Override
        public void filterResponse(ProxyExchange exchange, MutableHttpResponse<?> response) {
            String location = response.getHeaders().get(HttpHeaders.LOCATION);
            if (location == null) {
                return;
            }
            URI uri;
            try {
                uri = new URI(location);
            } catch (URISyntaxException e) {
                return;
            }
            URI target = exchange.target();
            if (uri.getHost() == null || target.getHost() == null || !uri.getHost().equalsIgnoreCase(target.getHost()) || port(uri) != port(target)) {
                return;
            }
            HttpRequest<?> inbound = exchange.request();
            String host = inbound.getHeaders().get(HttpHeaders.HOST);
            if (host == null) {
                return;
            }
            String scheme = inbound.isSecure() ? "https" : "http";
            StringBuilder rewritten = new StringBuilder(scheme).append("://").append(host);
            String rawPath = uri.getRawPath();
            rewritten.append(rawPath == null || rawPath.isEmpty() ? "/" : rawPath);
            if (uri.getRawQuery() != null) {
                rewritten.append('?').append(uri.getRawQuery());
            }
            if (uri.getRawFragment() != null) {
                rewritten.append('#').append(uri.getRawFragment());
            }
            response.getHeaders().set(HttpHeaders.LOCATION, rewritten.toString());
        }

        private static int port(URI uri) {
            if (uri.getPort() >= 0) {
                return uri.getPort();
            }
            String scheme = uri.getScheme() == null ? "http" : uri.getScheme().toLowerCase(Locale.ROOT);
            return scheme.equals("https") || scheme.equals("wss") ? 443 : 80;
        }

        @Override
        public String toString() {
            return "RewriteLocationResponseHeader";
        }
    }

    private enum SecureHeaders implements GatewayFilter {
        INSTANCE;

        private static final Map<String, String> HEADERS;

        static {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("X-Xss-Protection", "1 ; mode=block");
            headers.put("Strict-Transport-Security", "max-age=631138519");
            headers.put("X-Frame-Options", "DENY");
            headers.put("X-Content-Type-Options", "nosniff");
            headers.put("Referrer-Policy", "no-referrer");
            headers.put("Content-Security-Policy", "default-src 'self' https:; font-src 'self' https: data:; img-src 'self' https: data:; object-src 'none'; script-src https:; style-src 'self' https: 'unsafe-inline'");
            headers.put("X-Download-Options", "noopen");
            headers.put("X-Permitted-Cross-Domain-Policies", "none");
            HEADERS = headers;
        }

        @Override
        public void configure(RouteFilterSpec<?> route) {
            route.after((request, response) -> {
                MutableHttpHeaders headers = response.getHeaders();
                HEADERS.forEach((name, value) -> {
                    if (!headers.contains(name)) {
                        headers.add(name, value);
                    }
                });
            });
        }

        @Override
        public String toString() {
            return "SecureHeaders";
        }
    }

    private record SetStatus(HttpStatus status) implements GatewayFilter {
        @Override
        public void configure(RouteFilterSpec<?> route) {
            route.afterReplacing((request, response) -> {
                int code = status.getCode();
                if (code != HttpStatus.NO_CONTENT.getCode() && code != HttpStatus.NOT_MODIFIED.getCode()) {
                    return response.status(status);
                }
                // a status without content: the bytes of the upstream are not sent, and the
                // stream they come from is closed rather than left to the writer
                MutableHttpResponse<?> bodyless = HttpResponse.status(status);
                response.getHeaders().forEach((name, values) -> {
                    if (!name.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH) && !name.equalsIgnoreCase(HttpHeaders.TRANSFER_ENCODING)
                        && (code == HttpStatus.NOT_MODIFIED.getCode() || !name.equalsIgnoreCase(HttpHeaders.CONTENT_TYPE))) {
                        for (String value : values) {
                            bodyless.getHeaders().add(name, value);
                        }
                    }
                });
                if (response instanceof ByteBodyHttpResponse<?> byteBodyResponse) {
                    byteBodyResponse.close();
                }
                return bodyless;
            });
        }

        @Override
        public String toString() {
            return "SetStatus=" + status.getCode();
        }
    }

    /**
     * The in-flight limit per key, see {@link #bulkhead(int, KeyResolver)}.
     */
    static final class Bulkhead implements GatewayFilter {
        private final int maxConcurrent;
        private final KeyResolver keyResolver;
        /**
         * The requests in flight of a route and a key; a key without requests in flight is
         * removed, so that the keys not seen any more do not stay in memory.
         */
        private final Map<PermitKey, Integer> inFlight = new ConcurrentHashMap<>();

        Bulkhead(int maxConcurrent, KeyResolver keyResolver) {
            this.maxConcurrent = maxConcurrent;
            this.keyResolver = keyResolver;
        }

        @Override
        public void configure(RouteFilterSpec<?> route) {
            route.beforeReplacing(request -> {
                String key = keyResolver.resolve(request);
                if (key == null || key.isEmpty()) {
                    return null;
                }
                // a bulkhead of the default filters is shared by the routes: the limit is per route
                PermitKey permit = new PermitKey(GatewayRequestContext.routeId(request), key);
                if (!acquire(permit)) {
                    return HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS);
                }
                // in flight until the response is done: its body streamed, or the tunnel of a
                // switched connection closed; on a server that cannot tell, until its headers
                AtomicBoolean released = new AtomicBoolean();
                Runnable release = () -> {
                    if (released.compareAndSet(false, true)) {
                        release(permit);
                    }
                };
                if (!(request instanceof ServerHttpRequest<?> server) || !server.whenResponseCompleted(failure -> release.run())) {
                    GatewayRequestContext.forFilters(request).state(this, release);
                }
                return null;
            });
            route.after((request, response) -> {
                if (GatewayRequestContext.forFilters(request).removeState(this) instanceof Runnable release) {
                    release.run();
                }
            });
        }

        private boolean acquire(PermitKey key) {
            boolean[] acquired = new boolean[1];
            inFlight.compute(key, (k, count) -> {
                int current = count == null ? 0 : count;
                if (current >= maxConcurrent) {
                    return count;
                }
                acquired[0] = true;
                return current + 1;
            });
            return acquired[0];
        }

        private void release(PermitKey key) {
            inFlight.computeIfPresent(key, (k, count) -> count <= 1 ? null : count - 1);
        }

        /**
         * @param routeId A route
         * @param key     A key
         * @return Its requests in flight
         */
        int inFlight(String routeId, String key) {
            return inFlight.getOrDefault(new PermitKey(routeId, key), 0);
        }

        /**
         * @return The number of keys with requests in flight
         */
        int keys() {
            return inFlight.size();
        }

        @Override
        public String toString() {
            return "Bulkhead=" + maxConcurrent;
        }

        /**
         * The permit of a request: its route and its key.
         *
         * @param routeId The route
         * @param key     The key
         */
        private record PermitKey(String routeId, String key) {
        }
    }

    private record Cors(List<String> allowedOrigins, List<String> allowedMethods, List<String> allowedHeaders, List<String> exposedHeaders,
                        boolean allowCredentials, long maxAge) implements GatewayFilter, GatewayRouteSpec.Configurer {
        @Override
        public void configure(GatewayRouteSpec route) {
            io.micronaut.core.annotation.AnnotationValueBuilder<io.micronaut.http.server.cors.CrossOrigin> builder =
                io.micronaut.core.annotation.AnnotationValue.builder(io.micronaut.http.server.cors.CrossOrigin.class);
            builder.member(io.micronaut.http.server.cors.CrossOriginUtil.MEMBER_ALLOWED_ORIGINS, allowedOrigins.toArray(String[]::new));
            builder.member(io.micronaut.http.server.cors.CrossOriginUtil.MEMBER_ALLOWED_METHODS, allowedMethods.toArray(String[]::new));
            builder.member(io.micronaut.http.server.cors.CrossOriginUtil.MEMBER_ALLOWED_HEADERS, allowedHeaders.toArray(String[]::new));
            builder.member(io.micronaut.http.server.cors.CrossOriginUtil.MEMBER_EXPOSED_HEADERS, exposedHeaders.toArray(String[]::new));
            builder.member(io.micronaut.http.server.cors.CrossOriginUtil.MEMBER_ALLOW_CREDENTIALS, allowCredentials);
            if (maxAge >= 0) {
                builder.member(io.micronaut.http.server.cors.CrossOriginUtil.MEMBER_MAX_AGE, maxAge);
            }
            route.annotate(builder.build());
        }

        @Override
        public void configure(RouteFilterSpec<?> route) {
            // the CORS filter answers the preflights of the allowed origins: one that reaches the
            // route is of a forbidden origin, and is not relayed
            // the local 403 can go once the CORS filter answers such a preflight itself
            route.beforeReplacing(request -> io.micronaut.http.server.cors.CorsUtil.isPreflightRequest(request)
                ? HttpResponse.status(HttpStatus.FORBIDDEN) : null);
        }

        @Override
        public String toString() {
            return "Cors=" + String.join(", ", allowedOrigins);
        }
    }

    /**
     * A mirror, see {@link #mirror(String, double, long, Duration, boolean)}.
     *
     * @param uri          The URI of the shadow
     * @param percentage   The percentage of the requests to mirror
     * @param maxBodyBytes The limit of the body
     * @param timeout      The response timeout of the shadow
     * @param shadowHost   Whether the Host gets a -shadow suffix
     */
    record Mirror(URI uri, double percentage, long maxBodyBytes, Duration timeout, boolean shadowHost) implements GatewayFilter {
        @Override
        public String toString() {
            return "Mirror=" + uri + ", " + percentage;
        }
    }

    /**
     * The rewrite of the body of the request, see {@link #modifyRequestBody(long, BodyRewriter)}.
     *
     * @param maxBytes The limit of the body
     * @param rewriter The rewrite
     */
    record ModifyRequestBody(long maxBytes, BodyRewriter rewriter) implements GatewayFilter {
        @Override
        public String toString() {
            return "ModifyRequestBody=" + maxBytes;
        }
    }

    /**
     * The rewrite of the body of the response, see {@link #modifyResponseBody(long, BodyRewriter)}.
     */
    static class ModifyResponseBody implements GatewayFilter {
        private final long maxBytes;
        private final BodyRewriter rewriter;

        ModifyResponseBody(long maxBytes, BodyRewriter rewriter) {
            this.maxBytes = maxBytes;
            this.rewriter = rewriter;
        }

        long maxBytes() {
            return maxBytes;
        }

        BodyRewriter rewriter() {
            return rewriter;
        }

        @Override
        public String toString() {
            return "ModifyResponseBody=" + maxBytes;
        }
    }

    private record ReadBody(long maxBytes, java.util.function.Predicate<byte[]> predicate) implements GatewayFilter {
        @Override
        public void configure(RouteFilterSpec<?> route) {
            route.beforeReplacingAsync(request -> {
                if (!(request instanceof io.micronaut.http.ServerHttpRequest<?> server)) {
                    return java.util.concurrent.CompletableFuture.completedFuture(null);
                }
                if (request.getContentLength() > maxBytes) {
                    return java.util.concurrent.CompletableFuture.completedFuture(HttpResponse.status(HttpStatus.REQUEST_ENTITY_TOO_LARGE));
                }
                io.micronaut.http.body.CloseableByteBody copy = server.byteBodyFactory().limit(
                    server.byteBody().split(io.micronaut.http.body.ByteBody.SplitBackpressureMode.FASTEST), maxBytes);
                return copy.buffer().handle((bytes, failure) -> {
                    if (failure != null) {
                        return HttpResponse.status(HttpStatus.REQUEST_ENTITY_TOO_LARGE);
                    }
                    byte[] array;
                    try (bytes) {
                        array = bytes.toByteArray();
                    }
                    request.setAttribute(GatewayAttributes.CACHED_REQUEST_BODY, array);
                    return predicate.test(array) ? null : HttpResponse.status(HttpStatus.NOT_FOUND);
                });
            });
        }

        @Override
        public String toString() {
            return "ReadBody=" + maxBytes;
        }
    }

    private record CacheRequestBody(long maxBytes) implements GatewayFilter {
        @Override
        public void configure(RouteFilterSpec<?> route) {
            route.beforeReplacingAsync(request -> {
                if (!(request instanceof io.micronaut.http.ServerHttpRequest<?> server)) {
                    return java.util.concurrent.CompletableFuture.completedFuture(null);
                }
                if (request.getContentLength() > maxBytes) {
                    return java.util.concurrent.CompletableFuture.completedFuture(HttpResponse.status(HttpStatus.REQUEST_ENTITY_TOO_LARGE));
                }
                // a copy of the bytes, read at the pace of the copy: the body is still relayed
                io.micronaut.http.body.CloseableByteBody copy = server.byteBodyFactory().limit(
                    server.byteBody().split(io.micronaut.http.body.ByteBody.SplitBackpressureMode.FASTEST), maxBytes);
                return copy.buffer().handle((bytes, failure) -> {
                    if (failure != null) {
                        return HttpResponse.status(HttpStatus.REQUEST_ENTITY_TOO_LARGE);
                    }
                    try (bytes) {
                        GatewayRequestContext.forFilters(request).cachedRequestBody(bytes.toByteArray());
                    }
                    return null;
                });
            });
        }

        @Override
        public String toString() {
            return "CacheRequestBody=" + maxBytes;
        }
    }

    record RequestSize(long maxBytes) implements GatewayFilter {
        @Override
        public void configure(RouteFilterSpec<?> route) {
            route.beforeReplacing(request -> {
                long length = request.getContentLength();
                if (length > maxBytes) {
                    return HttpResponse.status(HttpStatus.REQUEST_ENTITY_TOO_LARGE)
                        .header(ERROR_MESSAGE, "Request size is larger than permissible limit. Request size is " + length
                            + " bytes where permissible limit is " + maxBytes + " bytes");
                }
                return null;
            });
        }

        @Override
        public String toString() {
            return "RequestSize=" + maxBytes;
        }
    }

    private record RequestHeaderSize(long maxBytes, int maxCount) implements GatewayFilter {
        @Override
        public void configure(RouteFilterSpec<?> route) {
            route.beforeReplacing(request -> {
                long size = 0;
                int count = 0;
                String largest = null;
                long largestSize = -1;
                for (Map.Entry<String, List<String>> header : request.getHeaders()) {
                    long headerSize = 0;
                    for (String value : header.getValue()) {
                        // a header line: its name and its value
                        headerSize += header.getKey().length() + value.length();
                        count++;
                    }
                    size += headerSize;
                    if (headerSize > largestSize) {
                        largestSize = headerSize;
                        largest = header.getKey();
                    }
                }
                if (size > maxBytes) {
                    return HttpResponse.status(HttpStatus.REQUEST_HEADER_FIELDS_TOO_LARGE)
                        .header(ERROR_MESSAGE, "Request Header/s size is larger than permissible limit (" + maxBytes
                            + " bytes). Request Header/s size is " + size + " bytes, the largest is '" + largest + "' with " + largestSize + " bytes");
                }
                if (maxCount > 0 && count > maxCount) {
                    return HttpResponse.status(HttpStatus.REQUEST_HEADER_FIELDS_TOO_LARGE)
                        .header(ERROR_MESSAGE, "The request has " + count + " headers, more than the permissible " + maxCount);
                }
                return null;
            });
        }

        @Override
        public String toString() {
            return "RequestHeaderSize=" + maxBytes + ", " + maxCount;
        }
    }

    private record RedirectTo(HttpStatus status, URI location) implements GatewayFilter {
        @Override
        public void configure(RouteFilterSpec<?> route) {
            route.beforeReplacing(request -> HttpResponse.status(status).header(HttpHeaders.LOCATION, location.toString()));
        }

        @Override
        public String toString() {
            return "RedirectTo=" + status.getCode() + ", " + location;
        }
    }
}
