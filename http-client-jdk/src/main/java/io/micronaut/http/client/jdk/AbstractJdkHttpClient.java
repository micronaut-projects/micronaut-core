/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.client.jdk;

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.client.ClientAttributes;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientExceptionUtils;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.exceptions.NoHostException;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.http.client.filter.ClientFilterResolutionContext;
import io.micronaut.http.client.jdk.cookie.CookieDecoder;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.context.ContextPathUtils;
import io.micronaut.http.cookie.ClientCookieEncoder;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.filter.FilterRunner;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.http.filter.HttpClientFilterResolver;
import io.micronaut.http.filter.HttpFilterResolver;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import io.micronaut.http.ssl.ClientAuthentication;
import io.micronaut.http.ssl.ClientSslConfiguration;
import io.micronaut.http.util.HttpHeadersUtil;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieManager;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static io.micronaut.http.client.exceptions.HttpClientExceptionUtils.populateServiceId;

/**
 * Abstract implementation of {@link DefaultJdkHttpClient} that provides common functionality.
 *
 * @author Sergio del Amo
 * @author Tim Yates
 * @since 4.0.0
 */
@Internal
@Experimental
abstract class AbstractJdkHttpClient {

    public static final String H2C_ERROR_MESSAGE = "H2C is not supported by the JDK HTTP client";
    public static final String H3_ERROR_MESSAGE = "HTTP/3 is not supported by the JDK HTTP client";
    public static final String WEIRD_ALPN_ERROR_MESSAGE = "The only supported ALPN modes are [" + HttpVersionSelection.ALPN_HTTP_1 + "] or [" + HttpVersionSelection.ALPN_HTTP_1 + "," + HttpVersionSelection.ALPN_HTTP_2 + "]";
    /**
     * The headers that are never copied to a redirect to another origin.
     */
    private static final List<String> ALWAYS_CROSS_ORIGIN_FILTERED_HEADERS = List.of(
        io.micronaut.http.HttpHeaders.AUTHORIZATION,
        io.micronaut.http.HttpHeaders.COOKIE,
        io.micronaut.http.HttpHeaders.ORIGIN,
        io.micronaut.http.HttpHeaders.REFERER
    );

    protected final LoadBalancer loadBalancer;
    protected final HttpVersionSelection httpVersion;
    protected final HttpClientConfiguration configuration;
    protected final String contextPath;
    protected final HttpClient client;
    /**
     * Not used by the client: the cookies of a request are sent in its {@code Cookie} header,
     * and the cookies a response sets are not kept, because a client is shared by the requests
     * of different users.
     */
    protected final CookieManager cookieManager;
    protected final RequestBinderRegistry requestBinderRegistry;
    protected final String clientId;
    protected final ConversionService conversionService;
    protected final JdkClientSslBuilder sslBuilder;
    protected final Logger log;
    protected final HttpClientFilterResolver<ClientFilterResolutionContext> filterResolver;
    protected final List<HttpFilterResolver.FilterEntry> clientFilterEntries;
    protected final CookieDecoder cookieDecoder;
    protected MediaTypeCodecRegistry mediaTypeCodecRegistry;
    protected MessageBodyHandlerRegistry messageBodyHandlerRegistry;

    protected AbstractJdkHttpClient(AbstractJdkHttpClient prototype) {
        this.loadBalancer = prototype.loadBalancer;
        this.httpVersion = prototype.httpVersion;
        this.configuration = prototype.configuration;
        this.contextPath = prototype.contextPath;
        this.client = prototype.client;
        this.cookieManager = prototype.cookieManager;
        this.requestBinderRegistry = prototype.requestBinderRegistry;
        this.clientId = prototype.clientId;
        this.conversionService = prototype.conversionService;
        this.sslBuilder = prototype.sslBuilder;
        this.log = prototype.log;
        this.filterResolver = prototype.filterResolver;
        this.clientFilterEntries = prototype.clientFilterEntries;
        this.cookieDecoder = prototype.cookieDecoder;
        this.mediaTypeCodecRegistry = prototype.mediaTypeCodecRegistry;
        this.messageBodyHandlerRegistry = prototype.messageBodyHandlerRegistry;
    }

    /**
     * @param log                        the logger to use
     * @param loadBalancer               The {@link LoadBalancer} to use for selecting servers
     * @param httpVersion                The {@link HttpVersionSelection} to prefer
     * @param configuration              The {@link HttpClientConfiguration} to use
     * @param contextPath                The base URI to prepend to request uris
     * @param mediaTypeCodecRegistry     The {@link MediaTypeCodecRegistry} to use for encoding and decoding objects
     * @param messageBodyHandlerRegistry The {@link MessageBodyHandlerRegistry} to use for encoding and decoding objects
     * @param requestBinderRegistry      The request binder registry
     * @param clientId                   The client id
     * @param conversionService          The {@link ConversionService}
     * @param sslBuilder                 The {@link JdkClientSslBuilder} for creating an {@link javax.net.ssl.SSLContext}
     */
    @SuppressWarnings({"java:S107", "checkstyle:parameternumber"}) // too many parameters
    protected AbstractJdkHttpClient(
        Logger log,
        LoadBalancer loadBalancer,
        HttpVersionSelection httpVersion,
        HttpClientConfiguration configuration,
        String contextPath,
        @Nullable HttpClientFilterResolver<ClientFilterResolutionContext> filterResolver,
        @Nullable List<HttpFilterResolver.FilterEntry> clientFilterEntries,
        MediaTypeCodecRegistry mediaTypeCodecRegistry,
        MessageBodyHandlerRegistry messageBodyHandlerRegistry,
        RequestBinderRegistry requestBinderRegistry,
        String clientId,
        ConversionService conversionService,
        JdkClientSslBuilder sslBuilder,
        CookieDecoder cookieDecoder
    ) {
        this.cookieDecoder = cookieDecoder;
        this.log = configuration.getLoggerName().map(LoggerFactory::getLogger).orElse(log);
        this.loadBalancer = loadBalancer;
        this.httpVersion = httpVersion;
        this.configuration = configuration;
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        this.messageBodyHandlerRegistry = messageBodyHandlerRegistry;
        this.requestBinderRegistry = requestBinderRegistry;
        this.clientId = clientId;
        this.conversionService = conversionService;
        this.cookieManager = new CookieManager();
        this.sslBuilder = sslBuilder;

        this.filterResolver = filterResolver;
        this.clientFilterEntries = clientFilterEntries(filterResolver, clientFilterEntries);

        if (System.getProperty("jdk.internal.httpclient.disableHostnameVerification") != null && log.isWarnEnabled()) {
            log.warn("The jdk.internal.httpclient.disableHostnameVerification system property is set. This is not recommended for production use as it prevents proper certificate validation and may allow man-in-the-middle attacks.");
        }

        if (StringUtils.isNotEmpty(contextPath)) {
            if (contextPath.charAt(0) != '/') {
                contextPath = '/' + contextPath;
            }
            this.contextPath = contextPath;
        } else {
            this.contextPath = null;
        }

        HttpClient.Builder builder = HttpClient.newBuilder();
        configuration.getConnectTimeout().ifPresent(builder::connectTimeout);

        HttpVersionSelection httpVersionSelection = HttpVersionSelection.forClientConfiguration(configuration);

        if (httpVersionSelection.getPlaintextMode() == HttpVersionSelection.PlaintextMode.H2C) {
            throw new ConfigurationException(H2C_ERROR_MESSAGE);
        }
        if (httpVersionSelection.isHttp3()) {
            throw new ConfigurationException(H3_ERROR_MESSAGE);
        }

        if (httpVersionSelection.isAlpn()) {
            List<String> supportedProtocols = Arrays.asList(httpVersionSelection.getAlpnSupportedProtocols());
            if (supportedProtocols.size() == 2 &&
                supportedProtocols.contains(HttpVersionSelection.ALPN_HTTP_1) &&
                supportedProtocols.contains(HttpVersionSelection.ALPN_HTTP_2)) {
                builder.version(HttpClient.Version.HTTP_2);
            } else if (supportedProtocols.size() == 1 &&
                supportedProtocols.get(0).equals(HttpVersionSelection.ALPN_HTTP_1)) {
                builder.version(HttpClient.Version.HTTP_1_1);
            } else {
                throw new ConfigurationException(WEIRD_ALPN_ERROR_MESSAGE);
            }
        } else {
            builder.version(HttpClient.Version.HTTP_1_1);
        }

        // redirects are followed by sendAsync, so that the redirect settings of the configuration apply
        builder.followRedirects(HttpClient.Redirect.NEVER);

        Optional<SocketAddress> proxyAddress = configuration.getProxyAddress();
        if (proxyAddress.isPresent()) {
            SocketAddress socketAddress = proxyAddress.get();
            builder = configureProxy(builder, socketAddress, configuration.getProxyUsername().orElse(null), configuration.getProxyPassword().orElse(null));
        }

        if (configuration.getSslConfiguration() instanceof ClientSslConfiguration clientSslConfiguration) {
            configureSsl(builder, clientSslConfiguration);
        }

        this.client = builder.build();
    }

    @NonNull
    private static List<HttpFilterResolver.FilterEntry> clientFilterEntries(@Nullable HttpClientFilterResolver<ClientFilterResolutionContext> filterResolver,
                                                                            @Nullable List<HttpFilterResolver.FilterEntry> clientFilterEntries) {
        if (clientFilterEntries != null) {
            return clientFilterEntries;
        }
        if (filterResolver == null) {
            return Collections.emptyList();
        }
        return filterResolver.resolveFilterEntries(
                new ClientFilterResolutionContext(null, AnnotationMetadata.EMPTY_METADATA)
        );
    }

    private static String cookieHeader(@Nullable String existing, Collection<Cookie> cookies) {
        StringBuilder value = new StringBuilder();
        if (existing != null) {
            // the cookies of the request replace those of the same name in the header
            for (String pair : existing.split(";")) {
                String trimmed = pair.trim();
                int eq = trimmed.indexOf('=');
                String name = eq < 0 ? trimmed : trimmed.substring(0, eq).trim();
                if (!trimmed.isEmpty() && cookies.stream().noneMatch(c -> c.getName().equals(name))) {
                    value.append(trimmed).append("; ");
                }
            }
        }
        for (Cookie cookie : cookies) {
            value.append(ClientCookieEncoder.INSTANCE.encode(cookie)).append("; ");
        }
        return value.substring(0, value.length() - 2);
    }

    private HttpClient.Builder configureProxy(
        @NonNull HttpClient.Builder builder,
        @NonNull SocketAddress address,
        @Nullable String username,
        @Nullable String password
    ) {
        if (log.isDebugEnabled()) {
            log.debug("Configuring proxy: {} with username: {}", address, username);
        }
        if (address instanceof InetSocketAddress inetSocketAddress) {
            builder = builder.proxy(ProxySelector.of(inetSocketAddress));
            if (username != null && password != null) {
                builder = builder.authenticator(new Authenticator() {
                    @Override
                    protected @Nullable PasswordAuthentication getPasswordAuthentication() {
                        // the proxy credentials are only for the proxy, never for an origin server challenge
                        if (getRequestorType() != RequestorType.PROXY) {
                            return null;
                        }
                        return new PasswordAuthentication(username, password.toCharArray());
                    }
                });
            }
        } else {
            throw new IllegalArgumentException("Unsupported proxy address type: " + address.getClass().getName());
        }
        return builder;
    }

    private void configureSsl(HttpClient.Builder builder, ClientSslConfiguration clientSslConfiguration) {
        sslBuilder.build(clientSslConfiguration).ifPresent(builder::sslContext);
        SSLParameters sslParameters = new SSLParameters();
        clientSslConfiguration.getClientAuthentication().ifPresent(a -> {
            if (a == ClientAuthentication.WANT) {
                sslParameters.setWantClientAuth(true);
            } else if (a == ClientAuthentication.NEED) {
                sslParameters.setNeedClientAuth(true);
            }
        });
        clientSslConfiguration.getProtocols().ifPresent(sslParameters::setProtocols);
        clientSslConfiguration.getCiphers().ifPresent(sslParameters::setCipherSuites);
        builder.sslParameters(sslParameters);
    }

    /**
     * @return The {@link MediaTypeCodecRegistry}
     */
    public MediaTypeCodecRegistry getMediaTypeCodecRegistry() {
        return mediaTypeCodecRegistry;
    }

    /**
     * @param mediaTypeCodecRegistry The {@link MediaTypeCodecRegistry}
     */
    public void setMediaTypeCodecRegistry(MediaTypeCodecRegistry mediaTypeCodecRegistry) {
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
    }

    /**
     * @return The {@link MessageBodyHandlerRegistry}
     */
    public MessageBodyHandlerRegistry getMessageBodyHandlerRegistry() {
        return messageBodyHandlerRegistry;
    }

    /**
     * @param messageBodyHandlerRegistry The {@link MessageBodyHandlerRegistry}
     */
    public void setMessageBodyHandlerRegistry(MessageBodyHandlerRegistry messageBodyHandlerRegistry) {
        this.messageBodyHandlerRegistry = messageBodyHandlerRegistry;
    }

    /**
     * Convert the Micronaut request to a JDK request.
     *
     * @param request  The Micronaut request object
     * @param bodyType The body type
     * @param <I>      The body type
     * @return A JDK request object
     */
    protected <I> Mono<HttpRequest> mapToHttpRequest(@NonNull io.micronaut.http.HttpRequest<I> request, @Nullable Argument<?> bodyType) {
        return resolveRequestUri(request)
            .map(uri -> {
                HttpRequest.Builder builder = HttpRequestFactory.builder(uri, request, configuration, bodyType, mediaTypeCodecRegistry, messageBodyHandlerRegistry);
                // the cookies go in the Cookie header of this request only: a cookie store would
                // send them, and the cookies a response sets, with the later requests of other users
                cookieDecoder.decode(request).ifPresent(cookies -> {
                    Set<Cookie> all = cookies.getAll();
                    if (!all.isEmpty()) {
                        builder.setHeader(io.micronaut.http.HttpHeaders.COOKIE, cookieHeader(request.getHeaders().get(io.micronaut.http.HttpHeaders.COOKIE), all));
                    }
                });
                return builder.build();
            });
    }

    protected Mono<URI> resolveRequestUri(io.micronaut.http.HttpRequest<?> request) {
        if (request.getUri().getScheme() != null) {
            // Full request URI, so use that
            return Mono.just(request.getUri());
        }

        // Otherwise, go and look it up via the LoadBalancer
        return resolveURI(request);
    }

    private <I> Mono<URI> resolveURI(io.micronaut.http.HttpRequest<I> request) {
        URI requestURI = request.getUri();
        if (loadBalancer == null) {
            return Mono.error(populateServiceId(new NoHostException("Request URI specifies no host to connect to"), clientId, configuration));
        }

        return Mono.from(loadBalancer.select(request)).map(server -> {
                Optional<String> authInfo = server.getMetadata().get(io.micronaut.http.HttpHeaders.AUTHORIZATION_INFO, String.class);
                if (request instanceof MutableHttpRequest<?> mutableRequest && authInfo.isPresent()) {
                    mutableRequest.getHeaders().auth(authInfo.get());
                }

                try {
                    // Service-ID load balancers may select URLs with different base paths.
                    String resolvedContextPath = contextPath == null ? server.getURI().getRawPath() : contextPath;
                    return server.resolve(ContextPathUtils.prepend(requestURI, resolvedContextPath));
                } catch (URISyntaxException e) {
                    throw populateServiceId(new HttpClientException("Failed to construct the request URI", e), clientId, configuration);
                }
            }
        );
    }

    /**
     * Convert the JDK response to a Micronaut response.
     *
     * @param netResponse The JDK response
     * @param bodyType    The body type
     * @param <O>         The body type
     * @return A Micronaut response
     */
    @NonNull
    protected <O> HttpResponse<O> response(@NonNull java.net.http.HttpResponse<byte[]> netResponse, @NonNull Argument<O> bodyType) {
        return new HttpResponseAdapter<>(netResponse, bodyType, conversionService, mediaTypeCodecRegistry, messageBodyHandlerRegistry);
    }

    protected <I, O> Flux<HttpResponse<O>> exchangeImpl(@NonNull io.micronaut.http.HttpRequest<I> request, @Nullable Argument<O> bodyType) {
        var defaultPublisher = responsePublisher(request, bodyType);
        return resolveRequestUri(request)
            .flatMapMany(uri -> applyFilterToResponsePublisher(request, uri, defaultPublisher));
    }

    protected <I, R extends io.micronaut.http.HttpResponse<?>> Publisher<R> applyFilterToResponsePublisher(
        io.micronaut.http.HttpRequest<I> request,
        URI requestURI,
        Publisher<R> responsePublisher
    ) {
        if (!(request instanceof MutableHttpRequest<?> mutRequest) || filterResolver == null) {
            return responsePublisher;
        }

        mutRequest.uri(requestURI);
        List<GenericHttpFilter> filters =
            filterResolver.resolveFilters(request, clientFilterEntries);

        FilterRunner.sortReverse(filters);

        FilterRunner runner = new FilterRunner(filters) {
            @Override
            protected ExecutionFlow<HttpResponse<?>> provideResponse(io.micronaut.http.HttpRequest<?> request, PropagatedContext propagatedContext) {
                try {
                    try (PropagatedContext.Scope ignore = propagatedContext.propagate()) {
                        return ReactiveExecutionFlow.fromPublisher((Publisher<HttpResponse<?>>) responsePublisher);
                    }
                } catch (Throwable e) {
                    return ExecutionFlow.error(e);
                }
            }
        };
        return (Publisher<R>) Mono.from(ReactiveExecutionFlow.fromFlow(runner.run(request)).toPublisher());
    }

    /**
     * The request timeout of {@link java.net.http.HttpRequest.Builder#timeout} ends when the
     * response headers arrive. Apply the request timeout to the whole exchange, including the
     * body, like the Netty client.
     */
    private <T> Mono<T> withRequestTimeout(CompletableFuture<T> future) {
        Mono<T> mono = Mono.fromCompletionStage(future);
        Duration requestTimeout = configuration.getRequestTimeout();
        if (requestTimeout == null) {
            // for compatibility, like the Netty client
            requestTimeout = configuration.getReadTimeout()
                .filter(d -> !d.isNegative())
                .map(d -> d.plusSeconds(1)).orElse(null);
        }
        if (requestTimeout == null || requestTimeout.isNegative()) {
            return mono;
        }
        return mono.timeout(requestTimeout, Mono.defer(() -> {
            future.cancel(true);
            return Mono.error(ReadTimeoutException.TIMEOUT_EXCEPTION);
        }));
    }

    protected <O> Publisher<io.micronaut.http.HttpResponse<O>> responsePublisher(
        @NonNull io.micronaut.http.HttpRequest<?> request,
        @Nullable Argument<O> bodyType
    ) {
        if (clientId != null && BasicHttpAttributes.getServiceId(request).isEmpty()) {
            ClientAttributes.setServiceId(request, clientId);
        }

        return Flux.defer(() -> mapToHttpRequest(request, bodyType)) // defered so any client filter changes are used
            .map(httpRequest -> {
                if (log.isDebugEnabled()) {
                    log.debug("Client {} Sending HTTP Request: {}", clientId, httpRequest);
                }
                HttpHeadersUtil.trace(log,
                    () -> httpRequest.headers().map().keySet(),
                    headerName -> httpRequest.headers().allValues(headerName));
                int maxContentLength = configuration.getMaxContentLength();
                return sendAsync(client, httpRequest, responseInfo -> new LimitedBodySubscriber<>(java.net.http.HttpResponse.BodySubscribers.ofByteArray(), maxContentLength));
            })
            .flatMap(this::withRequestTimeout)
            .onErrorMap(IOException.class, e -> new HttpClientException("Error sending request: " + e.getMessage(), e))
            .onErrorMap(InterruptedException.class, e -> new HttpClientException("Error sending request: " + e.getMessage(), e))
            .handle((netResponse, sink) -> {
                if (log.isDebugEnabled()) {
                    log.debug("Client {} Received HTTP Response: {} {}", clientId, netResponse.statusCode(), netResponse.uri());
                }
                boolean errorStatus = netResponse.statusCode() >= 400;
                if (errorStatus && configuration.isExceptionOnErrorStatus()) {
                    sink.error(HttpClientExceptionUtils.populateServiceId(
                        new HttpClientResponseException(HttpStatus.valueOf(netResponse.statusCode()).getReason(), response(netResponse, bodyType)),
                        clientId,
                        configuration
                    ));
                } else {
                    sink.next(response(netResponse, bodyType));
                }
            });
    }

    /**
     * Send a request, and follow the redirects of the response if the configuration says so.
     * The JDK client does not follow redirects itself, because it would copy headers the
     * configuration filters (e.g. {@link HttpClientConfiguration#getRedirectCrossOriginFilteredHeaders()})
     * to the redirect target.
     *
     * @param httpClient  The JDK client to send the request with
     * @param request     The request
     * @param bodyHandler The handler of the body of the final response
     * @param <T>         The body type
     * @return The final response
     */
    final <T> CompletableFuture<java.net.http.HttpResponse<T>> sendAsync(
        HttpClient httpClient,
        HttpRequest request,
        java.net.http.HttpResponse.BodyHandler<T> bodyHandler
    ) {
        if (!configuration.isFollowRedirects()) {
            return httpClient.sendAsync(request, bodyHandler);
        }
        CompletableFuture<java.net.http.HttpResponse<T>> result = new CompletableFuture<>();
        AtomicReference<CompletableFuture<?>> inFlight = new AtomicReference<>();
        // cancelling the result cancels the exchange that is in flight
        result.whenComplete((response, error) -> {
            CompletableFuture<?> exchange = inFlight.get();
            if (result.isCancelled() && exchange != null) {
                exchange.cancel(true);
            }
        });
        sendFollowingRedirects(httpClient, request, bodyHandler, 0, result, inFlight);
        return result;
    }

    private <T> void sendFollowingRedirects(
        HttpClient httpClient,
        HttpRequest request,
        java.net.http.HttpResponse.BodyHandler<T> bodyHandler,
        int redirectCount,
        CompletableFuture<java.net.http.HttpResponse<T>> result,
        AtomicReference<CompletableFuture<?>> inFlight
    ) {
        if (result.isDone()) {
            return;
        }
        CompletableFuture<java.net.http.HttpResponse<T>> exchange = httpClient.sendAsync(request, responseInfo -> redirectUri(request, responseInfo.statusCode(), responseInfo.headers()) == null
            ? bodyHandler.apply(responseInfo)
            // the body of a redirect that is followed is discarded
            : java.net.http.HttpResponse.BodySubscribers.replacing(null));
        inFlight.set(exchange);
        if (result.isCancelled()) {
            exchange.cancel(true);
            return;
        }
        exchange.whenComplete((response, error) -> {
            if (error != null) {
                result.completeExceptionally(error);
                return;
            }
            try {
                URI location = redirectUri(request, response.statusCode(), response.headers());
                if (location == null) {
                    result.complete(response);
                    return;
                }
                int nextRedirectCount = redirectCount + 1;
                if (nextRedirectCount > configuration.getMaxRedirects()) {
                    result.completeExceptionally(populateServiceId(
                        new HttpClientException("Maximum number of redirects exceeded at redirect count: " + nextRedirectCount),
                        clientId,
                        configuration
                    ));
                    return;
                }
                HttpRequest redirectRequest = redirectRequest(request, response.statusCode(), location);
                if (log.isDebugEnabled()) {
                    log.debug("Client {} Following redirect {} to: {}", clientId, response.statusCode(), redirectRequest);
                }
                sendFollowingRedirects(httpClient, redirectRequest, bodyHandler, nextRedirectCount, result, inFlight);
            } catch (Throwable t) {
                // result must always complete, or the caller waits forever
                result.completeExceptionally(t instanceof HttpClientException || !(t instanceof Exception) ? t : populateServiceId(
                    new HttpClientException("Failed to follow the redirect: " + t.getMessage(), t),
                    clientId,
                    configuration
                ));
            }
        });
    }

    /**
     * The URI a response redirects to, if the redirect is followed.
     *
     * @param request    The request
     * @param statusCode The status of the response
     * @param headers    The headers of the response
     * @return The redirect URI, or {@code null} if the response is not a redirect to follow
     */
    @Nullable
    private URI redirectUri(HttpRequest request, int statusCode, java.net.http.HttpHeaders headers) {
        if (statusCode != 301 && statusCode != 302 && statusCode != 303 && statusCode != 307 && statusCode != 308) {
            return null;
        }
        Optional<String> location = headers.firstValue(io.micronaut.http.HttpHeaders.LOCATION);
        if (location.isEmpty()) {
            return null;
        }
        URI redirectUri;
        try {
            redirectUri = request.uri().resolve(new URI(location.get()));
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
        if (redirectUri.getHost() == null) {
            if (log.isDebugEnabled()) {
                log.debug("Client {} Not following redirect {} to a location without a host: {}", clientId, statusCode, location.get());
            }
            return null;
        }
        String scheme = redirectUri.getScheme();
        // like HttpClient.Redirect.NORMAL: no redirects from https to http
        if (scheme == null || !(scheme.equalsIgnoreCase(request.uri().getScheme()) || scheme.equalsIgnoreCase("https"))) {
            return null;
        }
        return redirectUri;
    }

    /**
     * Build the request that follows a redirect. The headers the configuration filters for
     * this redirect are not copied.
     *
     * @param request    The request that was redirected
     * @param statusCode The redirect status
     * @param location   The redirect URI
     * @return The request to the redirect URI
     */
    private HttpRequest redirectRequest(HttpRequest request, int statusCode, URI location) {
        String method = request.method();
        // a 303 changes the method to GET (but, unlike HttpClient.Redirect.NORMAL, keeps HEAD
        // as RFC 9110 allows), a 301 or 302 only changes a POST to a GET like
        // HttpClient.Redirect.NORMAL, and any other redirect keeps the method and body
        boolean changeToGet = (statusCode == 303 && !method.equals(io.micronaut.http.HttpMethod.HEAD.name())) ||
            ((statusCode == 301 || statusCode == 302) && method.equals(io.micronaut.http.HttpMethod.POST.name()));
        // the body headers are kept for a 307 or 308 like in the Netty client, and for a
        // 301 or 302 that sends the body again
        boolean preserveBody = statusCode == 307 || statusCode == 308 ||
            (!changeToGet && request.bodyPublisher().map(body -> body.contentLength() != 0).orElse(false));
        Set<String> filteredHeaders = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        filteredHeaders.addAll(configuration.getRedirectAlwaysFilteredHeaders());
        if (!isSameOrigin(request.uri(), location)) {
            filteredHeaders.addAll(configuration.getRedirectCrossOriginFilteredHeaders());
            // HttpClient.Redirect.NORMAL never sends these to another origin, whatever the configuration
            filteredHeaders.addAll(ALWAYS_CROSS_ORIGIN_FILTERED_HEADERS);
        }
        if (!preserveBody) {
            filteredHeaders.addAll(configuration.getRedirectAdditionalNonPreserveBodyFilteredHeaders());
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(request, (name, value) -> !filteredHeaders.contains(name))
            .uri(location);
        if (changeToGet) {
            builder.GET();
        }
        return builder.build();
    }

    private static boolean isSameOrigin(URI a, URI b) {
        String hostA = a.getHost();
        String hostB = b.getHost();
        return hostA != null && hostB != null &&
            a.getScheme().equalsIgnoreCase(b.getScheme()) &&
            hostA.equalsIgnoreCase(hostB) &&
            effectivePort(a) == effectivePort(b);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}
