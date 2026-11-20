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
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.client.loadbalance.LoadBalancerSelection;
import io.micronaut.http.client.AbstractHttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.http.client.exceptions.ResponseClosedException;
import io.micronaut.http.client.exceptions.UnprocessedRequestException;
import io.micronaut.http.client.jdk.cookie.CookieDecoder;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.cookie.ClientCookieEncoder;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.ssl.AbstractClientSslConfiguration;
import io.micronaut.http.ssl.ClientAuthentication;
import io.micronaut.http.util.HttpHeadersUtil;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.ConnectException;
import java.net.CookieManager;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.concurrent.Flow;
import java.util.concurrent.CompletionException;
import java.util.Objects;
import java.nio.ByteBuffer;

import static io.micronaut.http.client.AbstractHttpClient.report;

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
     * Request attribute of a request of the raw client, see {@link JdkRawHttpClient}.
     */
    static final String RAW_ATTRIBUTE = "micronaut.http.client.jdk.raw";
    /**
     * Request attribute with the {@link UploadListener} of the body of a raw request.
     */
    static final String UPLOAD_LISTENER_ATTRIBUTE = "micronaut.http.client.jdk.raw.upload-listener";
    /**
     * The headers that are never copied to a redirect to another origin.
     */
    private static final List<String> ALWAYS_CROSS_ORIGIN_FILTERED_HEADERS = List.of(
        io.micronaut.http.HttpHeaders.AUTHORIZATION,
        io.micronaut.http.HttpHeaders.COOKIE,
        io.micronaut.http.HttpHeaders.ORIGIN,
        io.micronaut.http.HttpHeaders.REFERER
    );
    private static final String ERROR_SENDING_REQUEST = "Error sending request: ";
    protected final HttpClientConfiguration configuration;
    protected final HttpClient client;
    /**
     * Not used by the client: the cookies of a request are sent in its {@code Cookie} header,
     * and the cookies a response sets are not kept, because a client is shared by the requests
     * of different users.
     */
    protected final CookieManager cookieManager;
    @Nullable
    protected final String clientId;
    protected final ConversionService conversionService;
    protected final JdkClientSslBuilder sslBuilder;
    protected final Logger log;
    protected final CookieDecoder cookieDecoder;
    @Nullable
    protected MediaTypeCodecRegistry mediaTypeCodecRegistry;
    @Nullable
    protected MessageBodyHandlerRegistry messageBodyHandlerRegistry;
    /**
     * The size limits of the streamed response bodies.
     */
    final BodySizeLimits sizeLimits;
    /**
     * Whether the redirects are followed by the client pipeline, see
     * {@link JdkHttpClientConfiguration.JdkConfiguration#isUseMicronautRedirects()}, else by the JDK client.
     */
    final boolean micronautRedirects;
    /**
     * Whether the redirects are followed like {@link HttpClient.Redirect#NORMAL} of the JDK client,
     * see {@link #sendAsync}. The JDK client itself never follows them, because it would copy the
     * headers the configuration filters to the redirect target.
     */
    final boolean jdkRedirects;
    /**
     * The settings only the JDK client uses, see {@link JdkHttpClientConfiguration}.
     */
    final JdkHttpClientConfiguration.JdkConfiguration jdkConfiguration;
    /**
     * The client for raw exchanges and for proxying: it follows redirects as configured.
     * Built on first use.
     */
    final Supplier<HttpClient> rawClient;
    /**
     * Like {@link #rawClient}, but never follows redirects, see
     * {@link io.micronaut.http.client.RawRequestOptions#isFollowRedirects()}.
     */
    final Supplier<HttpClient> rawNoRedirectClient;
    /**
     * The client whose pipeline sends the requests of this client: its filters, its redirects,
     * the load balancing and the decoding of the responses are shared with the other clients.
     */
    @Nullable
    DefaultJdkHttpClient http;

    protected AbstractJdkHttpClient(AbstractJdkHttpClient prototype) {
        this.configuration = prototype.configuration;
        this.client = prototype.client;
        this.sizeLimits = prototype.sizeLimits;
        this.micronautRedirects = prototype.micronautRedirects;
        this.jdkRedirects = prototype.jdkRedirects;
        this.jdkConfiguration = prototype.jdkConfiguration;
        this.rawClient = prototype.rawClient;
        this.rawNoRedirectClient = prototype.rawNoRedirectClient;
        this.cookieManager = prototype.cookieManager;
        this.clientId = prototype.clientId;
        this.conversionService = prototype.conversionService;
        this.sslBuilder = prototype.sslBuilder;
        this.log = prototype.log;
        this.cookieDecoder = prototype.cookieDecoder;
        this.mediaTypeCodecRegistry = prototype.mediaTypeCodecRegistry;
        this.messageBodyHandlerRegistry = prototype.messageBodyHandlerRegistry;
        this.http = prototype.http;
    }

    /**
     * @param log                        the logger to use
     * @param configuration              The {@link HttpClientConfiguration} to use
     * @param mediaTypeCodecRegistry     The {@link MediaTypeCodecRegistry} to use for encoding and decoding objects
     * @param messageBodyHandlerRegistry The {@link MessageBodyHandlerRegistry} to use for encoding and decoding objects
     * @param clientId                   The client id
     * @param conversionService          The {@link ConversionService}
     * @param sslBuilder                 The {@link JdkClientSslBuilder} for creating an {@link javax.net.ssl.SSLContext}
     * @param cookieDecoder              The cookie decoder
     */
    protected AbstractJdkHttpClient(
        Logger log,
        HttpClientConfiguration configuration,
        @Nullable
        MediaTypeCodecRegistry mediaTypeCodecRegistry,
        @Nullable
        MessageBodyHandlerRegistry messageBodyHandlerRegistry,
        @Nullable
        String clientId,
        ConversionService conversionService,
        JdkClientSslBuilder sslBuilder,
        CookieDecoder cookieDecoder
    ) {
        this(log, configuration, mediaTypeCodecRegistry, messageBodyHandlerRegistry, clientId, conversionService, sslBuilder, cookieDecoder, null);
    }

    /**
     * @param log                        the logger to use
     * @param configuration              The {@link HttpClientConfiguration} to use
     * @param mediaTypeCodecRegistry     The {@link MediaTypeCodecRegistry} to use for encoding and decoding objects
     * @param messageBodyHandlerRegistry The {@link MessageBodyHandlerRegistry} to use for encoding and decoding objects
     * @param clientId                   The client id
     * @param conversionService          The {@link ConversionService}
     * @param sslBuilder                 The {@link JdkClientSslBuilder} for creating an {@link javax.net.ssl.SSLContext}
     * @param cookieDecoder              The cookie decoder
     * @param defaultJdkConfiguration    The JDK settings used when the configuration is not a {@link JdkHttpClientConfiguration}, or {@code null} for the defaults
     */
    @SuppressWarnings("java:S107") // too many parameters
    AbstractJdkHttpClient(
        Logger log,
        HttpClientConfiguration configuration,
        @Nullable
        MediaTypeCodecRegistry mediaTypeCodecRegistry,
        @Nullable
        MessageBodyHandlerRegistry messageBodyHandlerRegistry,
        @Nullable
        String clientId,
        ConversionService conversionService,
        JdkClientSslBuilder sslBuilder,
        CookieDecoder cookieDecoder,
        JdkHttpClientConfiguration.@Nullable JdkConfiguration defaultJdkConfiguration
    ) {
        this.cookieDecoder = cookieDecoder;
        this.log = configuration.getLoggerName().map(LoggerFactory::getLogger).orElse(log);
        this.configuration = configuration;
        this.sizeLimits = new BodySizeLimits(Long.MAX_VALUE, configuration.getMaxContentLength());
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        this.messageBodyHandlerRegistry = messageBodyHandlerRegistry;
        this.clientId = clientId;
        this.conversionService = conversionService;
        this.cookieManager = new CookieManager();
        this.sslBuilder = sslBuilder;

        if (System.getProperty("jdk.internal.httpclient.disableHostnameVerification") != null && log.isWarnEnabled()) {
            log.warn("The jdk.internal.httpclient.disableHostnameVerification system property is set. This is not recommended for production use as it prevents proper certificate validation and may allow man-in-the-middle attacks.");
        }

        // the redirects are followed by the JDK client, as they always were, unless the client
        // pipeline follows them, see AbstractHttpClient, with the configuration and the options
        // of an exchange
        this.jdkConfiguration = jdkConfiguration(configuration, defaultJdkConfiguration);
        this.micronautRedirects = jdkConfiguration.isUseMicronautRedirects();
        this.jdkRedirects = configuration.isFollowRedirects() && !micronautRedirects;
        // the JDK client never follows redirects itself: sendAsync follows them, so that the
        // redirect header settings of the configuration apply
        this.client = buildClient();
        Supplier<HttpClient> raw = SupplierUtil.memoized(this::buildClient);
        this.rawClient = raw;
        this.rawNoRedirectClient = raw;
    }

    private static JdkHttpClientConfiguration.JdkConfiguration jdkConfiguration(HttpClientConfiguration configuration,
                                                                       JdkHttpClientConfiguration.@Nullable JdkConfiguration defaultJdkConfiguration) {
        if (configuration instanceof JdkHttpClientConfiguration jdkClientConfiguration) {
            return jdkClientConfiguration.getJdk();
        }
        return defaultJdkConfiguration != null ? defaultJdkConfiguration : new JdkHttpClientConfiguration.JdkConfiguration();
    }

    private HttpClient buildClient() {
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

        builder.followRedirects(HttpClient.Redirect.NEVER);

        Optional<SocketAddress> proxyAddress = configuration.getProxyAddress();
        if (proxyAddress.isPresent()) {
            SocketAddress socketAddress = proxyAddress.get();
            builder = configureProxy(builder, socketAddress, configuration.getProxyUsername().orElse(null), configuration.getProxyPassword().orElse(null));
        }

        if (configuration.getSslConfiguration() instanceof AbstractClientSslConfiguration sslConfiguration) {
            configureSsl(builder, sslConfiguration);
        }

        return builder.build();
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
        HttpClient.Builder builder,
        SocketAddress address,
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

    private void configureSsl(HttpClient.Builder builder, AbstractClientSslConfiguration sslConfiguration) {
        sslBuilder.build(sslConfiguration).ifPresent(builder::sslContext);
        SSLParameters sslParameters = new SSLParameters();
        sslConfiguration.getClientAuthentication().ifPresent(a -> {
            if (a == ClientAuthentication.WANT) {
                sslParameters.setWantClientAuth(true);
            } else if (a == ClientAuthentication.NEED) {
                sslParameters.setNeedClientAuth(true);
            }
        });
        sslConfiguration.getProtocols().ifPresent(sslParameters::setProtocols);
        sslConfiguration.getCiphers().ifPresent(sslParameters::setCipherSuites);
        builder.sslParameters(sslParameters);
    }

    /**
     * @return The {@link MediaTypeCodecRegistry}
     */
    @Nullable
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
    @Nullable
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
     * Convert the Micronaut request to a JDK request for the given absolute URI.
     *
     * @param uri      The absolute URI to send the request to
     * @param request  The Micronaut request object
     * @param bodyType The body type
     * @return A JDK request object
     */
    HttpRequest toJdkRequest(URI uri, io.micronaut.http.HttpRequest<?> request, @Nullable Argument<?> bodyType) {
        if (request.getAttribute(RAW_ATTRIBUTE).isPresent()) {
            return toRawJdkRequest(uri, request, bodyType);
        }
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
    }

    /**
     * Map an I/O failure of {@link HttpClient#sendAsync}: a request that was not sent, because the
     * connection could not be opened, is an {@link UnprocessedRequestException}, so that the caller
     * can send it again; a connection closed before the response arrived is a
     * {@link ResponseClosedException}.
     *
     * @param selection The selection of the load balancer, or {@code null}
     * @param uri       The URI the request was sent to
     * @param e         The failure
     * @return The client exception
     */
    HttpClientException sendError(@Nullable LoadBalancerSelection selection, @Nullable URI uri, IOException e) {
        return sendError(selection, uri, e, false);
    }

    /**
     * @param selection       The selection of the load balancer, or {@code null}
     * @param uri             The request URI
     * @param e               The failure of {@link HttpClient#sendAsync}
     * @param headersReceived Whether the response headers had arrived, i.e. the failure is one of
     *                        a body read by the JDK client
     * @return The client exception
     */
    HttpClientException sendError(@Nullable LoadBalancerSelection selection, @Nullable URI uri, IOException e, boolean headersReceived) {
        HttpClientException result;
        if (e instanceof HttpConnectTimeoutException) {
            result = new UnprocessedRequestException(UnprocessedRequestException.Reason.CONNECT_TIMEOUT, "Connect Error: " + e.getMessage(), e);
            report(selection, LoadBalancer.Outcome.CONNECT_FAILURE);
        } else if (e instanceof ConnectException) {
            result = new UnprocessedRequestException(UnprocessedRequestException.Reason.CONNECT, "Connect Error: " + e.getMessage(), e);
            report(selection, LoadBalancer.Outcome.CONNECT_FAILURE);
        } else if (e.getMessage() != null && e.getMessage().contains("header parser received no bytes")) {
            // the JDK client reports a connection closed before the response headers with this message
            result = new ResponseClosedException("Connection closed before response was received", false);
            report(selection, LoadBalancer.Outcome.RESET);
        } else if (e instanceof HttpTimeoutException) {
            // the request timeout of the JDK client is set from the read timeout; up to JDK 25 it
            // only runs until the response headers arrive, a later JDK may extend it to the body
            result = headersReceived ? ReadTimeoutException.BODY_TIMEOUT_EXCEPTION : ReadTimeoutException.TIMEOUT_EXCEPTION;
            report(selection, LoadBalancer.Outcome.TIMEOUT);
        } else {
            if (ByteBodySubscriber.isTruncatedBody(e)) {
                // a buffered response whose body was cut off
                report(selection, LoadBalancer.Outcome.RESET);
            }
            result = new HttpClientException(ERROR_SENDING_REQUEST + e.getMessage(), e);
        }
        if (result instanceof UnprocessedRequestException unprocessed && uri != null) {
            unprocessed.setTarget(uri, selection == null ? null : selection.instance());
        }
        return result;
    }

    /**
     * End an exchange without an outcome, unless one was reported already or the response
     * handling claimed the selection.
     *
     * @param selection The selection of the load balancer, or {@code null}
     */
    static void releaseUnclaimed(@Nullable LoadBalancerSelection selection) {
        if (selection != null) {
            selection.releaseUnclaimed();
        }
    }

    /**
     * Report the outcome of an exchange whose response body ended: the status when the body is
     * complete, or the failure that cut it off.
     *
     * @param selection  The selection of the load balancer, or {@code null}
     * @param statusCode The response status
     * @param failure    The failure of the body, or {@code null} if it is complete
     */
    void reportBodyEnd(@Nullable LoadBalancerSelection selection, int statusCode, @Nullable Throwable failure) {
        if (failure == null) {
            report(selection, statusCode >= 500 ? LoadBalancer.Outcome.SERVER_ERROR : LoadBalancer.Outcome.SUCCESS);
        } else if (failure instanceof ResponseClosedException) {
            report(selection, LoadBalancer.Outcome.RESET);
        } else if (failure instanceof HttpTimeoutException) {
            report(selection, LoadBalancer.Outcome.TIMEOUT);
        } else if (selection != null) {
            // any other failure of the body says nothing about the instance, and is not a success either
            selection.release();
        }
    }

    /**
     * The client of the shared pipeline.
     *
     * @return The client
     */
    DefaultJdkHttpClient pipelineClient() {
        return Objects.requireNonNull(http, "The client has no pipeline");
    }

    /**
     * Send one request with the JDK client, without filters and without following redirects:
     * the response body is streamed, and the outcome is reported to the load balancer once it
     * ended.
     *
     * @param request         The request, with an absolute URI
     * @param selection       The selection of the load balancer, or {@code null}
     * @param bufferedHeaders For an exchange whose response body is read whole, set once the
     *                        response headers arrived, else {@code null}
     * @return The flow of the response
     */
    ExecutionFlow<JdkByteBodyResponse> sendRequest(MutableHttpRequest<?> request,
                                                   @Nullable LoadBalancerSelection selection,
                                                   @Nullable AtomicBoolean bufferedHeaders) {
        HttpRequest httpRequest;
        try {
            httpRequest = toJdkRequest(request.getUri(), request, null);
        } catch (RuntimeException e) {
            return ExecutionFlow.error(e);
        }
        if (log.isDebugEnabled()) {
            log.debug("Client {} Sending HTTP Request: {}", clientId, httpRequest);
        }
        if (log.isTraceEnabled()) {
            HttpHeadersUtil.trace(log,
                () -> httpRequest.headers().map().keySet(),
                headerName -> httpRequest.headers().allValues(headerName));
        }
        boolean raw = request.getAttribute(RAW_ATTRIBUTE).isPresent();
        HttpClient httpClient;
        boolean followRedirects;
        if (raw) {
            // the redirects of a raw exchange are followed unless its options disable them
            httpClient = rawClient.get();
            followRedirects = jdkRedirects && request.getAttribute(AbstractHttpClient.NO_FOLLOW_REDIRECTS).isEmpty();
        } else {
            httpClient = client;
            followRedirects = jdkRedirects;
        }
        if (!raw && bufferedHeaders != null) {
            return sendBuffered(httpClient, httpRequest, followRedirects, selection, bufferedHeaders);
        }
        // whether the headers arrived, so that a failure of the body is told from one before
        AtomicBoolean headersReceived = new AtomicBoolean();
        DelayedExecutionFlow<JdkByteBodyResponse> result = DelayedExecutionFlow.create();
        CompletableFuture<java.net.http.HttpResponse<CloseableByteBody>> sent;
        try {
            sent = sendAsync(httpClient, httpRequest, followRedirects, responseInfo -> {
                headersReceived.set(true);
                // the end of the body reports the outcome, or releases the selection
                if (selection != null) {
                    selection.claim();
                }
                // the body of a stream fails with a client exception; the body of a raw exchange
                // fails with the failure of the JDK client, as it always did
                // the request timeout of the JDK client ends when the headers arrive, so the read
                // timeout applies to the body a raw exchange streams
                return new ByteBodySubscriber(sizeLimits, !raw, failure -> reportBodyEnd(selection, responseInfo.statusCode(), failure),
                    raw ? configuration.getReadTimeout().orElse(null) : null);
            });
        } catch (RuntimeException e) {
            return ExecutionFlow.error(e);
        }
        sent.whenComplete((response, error) -> {
            if (error == null) {
                if (log.isDebugEnabled()) {
                    log.debug("Client {} Received HTTP Response: {} {}", clientId, response.statusCode(), response.uri());
                }
                if (!result.tryComplete(new JdkByteBodyResponse(response, null, conversionService))) {
                    response.body().close();
                }
                return;
            }
            if (result.isCancelled()) {
                // the cancellation of the exchange aborted the request: nobody waits for an outcome
                return;
            }
            Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
            Throwable mapped;
            if (cause instanceof IOException io) {
                mapped = sendError(selection, httpRequest.uri(), io, headersReceived.get());
            } else if (cause instanceof InterruptedException) {
                mapped = new HttpClientException(ERROR_SENDING_REQUEST + cause.getMessage(), cause);
            } else {
                mapped = cause;
            }
            result.tryCompleteExceptionally(mapped);
        });
        result.onCancel(() -> sent.cancel(true));
        return result;
    }

    /**
     * Send a request whose response body is read whole, into an array: the outcome is reported
     * once the body is read.
     */
    private ExecutionFlow<JdkByteBodyResponse> sendBuffered(HttpClient httpClient,
                                                            HttpRequest httpRequest,
                                                            boolean followRedirects,
                                                            @Nullable LoadBalancerSelection selection,
                                                            AtomicBoolean headersReceived) {
        DelayedExecutionFlow<JdkByteBodyResponse> result = DelayedExecutionFlow.create();
        CompletableFuture<java.net.http.HttpResponse<byte[]>> sent;
        try {
            sent = sendAsync(httpClient, httpRequest, followRedirects, responseInfo -> {
                headersReceived.set(true);
                // the body is not buffered beyond the maximum content length. The request timeout
                // of the JDK client ends when the headers arrive, so the read timeout applies to
                // the body
                return new LimitedBodySubscriber<>(java.net.http.HttpResponse.BodySubscribers.ofByteArray(), configuration.getMaxContentLength(),
                    configuration.getReadTimeout().orElse(null));
            });
        } catch (RuntimeException e) {
            releaseUnclaimed(selection);
            return ExecutionFlow.error(e);
        }
        sent.whenComplete((response, error) -> {
            if (error == null) {
                report(selection, response.statusCode() >= 500 ? LoadBalancer.Outcome.SERVER_ERROR : LoadBalancer.Outcome.SUCCESS);
                if (log.isDebugEnabled()) {
                    log.debug("Client {} Received HTTP Response: {} {}", clientId, response.statusCode(), response.uri());
                }
                result.tryComplete(new JdkByteBodyResponse(response, response.body(), conversionService));
                return;
            }
            if (result.isCancelled()) {
                // the cancellation of the exchange aborted the request: nobody waits for an outcome
                releaseUnclaimed(selection);
                return;
            }
            Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
            Throwable mapped;
            if (cause instanceof IOException io) {
                mapped = sendError(selection, httpRequest.uri(), io, headersReceived.get());
            } else if (cause instanceof InterruptedException) {
                mapped = new HttpClientException(ERROR_SENDING_REQUEST + cause.getMessage(), cause);
            } else {
                mapped = cause;
            }
            releaseUnclaimed(selection);
            result.tryCompleteExceptionally(mapped);
        });
        result.onCancel(() -> sent.cancel(true));
        return result;
    }

    /**
     * Send a request, and follow the redirects of the response if the configuration says so.
     * The JDK client does not follow redirects itself, because it would copy headers the
     * configuration filters (e.g. {@link HttpClientConfiguration#getRedirectCrossOriginFilteredHeaders()})
     * to the redirect target.
     *
     * @param httpClient      The JDK client to send the request with
     * @param request         The request
     * @param followRedirects Whether the redirects are followed
     * @param bodyHandler The handler of the body of the final response
     * @param <T>         The body type
     * @return The final response
     */
    final <T> CompletableFuture<java.net.http.HttpResponse<T>> sendAsync(
        HttpClient httpClient,
        HttpRequest request,
        boolean followRedirects,
        java.net.http.HttpResponse.BodyHandler<T> bodyHandler
    ) {
        if (!followRedirects) {
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
        // like HttpClient.Redirect.NORMAL, the response of a redirect beyond the maximum number of
        // redirects is returned
        boolean lastRedirect = redirectCount >= configuration.getMaxRedirects();
        CompletableFuture<java.net.http.HttpResponse<T>> exchange = httpClient.sendAsync(request, responseInfo -> lastRedirect || redirectUri(request, responseInfo.statusCode(), responseInfo.headers()) == null
            ? bodyHandler.apply(responseInfo)
            // the body of a redirect that is followed is discarded, within the read timeout and
            // the maximum content length: the request timeout of the JDK client ends at the headers
            : new LimitedBodySubscriber<>(java.net.http.HttpResponse.BodySubscribers.<T>replacing(null),
                configuration.getMaxContentLength(), configuration.getReadTimeout().orElse(null)));
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
                URI location = lastRedirect ? null : redirectUri(request, response.statusCode(), response.headers());
                if (location == null) {
                    result.complete(response);
                    return;
                }
                int nextRedirectCount = redirectCount + 1;
                HttpRequest redirectRequest = redirectRequest(request, response.statusCode(), location);
                if (log.isDebugEnabled()) {
                    log.debug("Client {} Following redirect {} to: {}", clientId, response.statusCode(), redirectRequest);
                }
                sendFollowingRedirects(httpClient, redirectRequest, bodyHandler, nextRedirectCount, result, inFlight);
            } catch (Throwable t) {
                // result must always complete, or the caller waits forever
                result.completeExceptionally(t instanceof HttpClientException || !(t instanceof Exception) ? t
                    : new HttpClientException("Failed to follow the redirect: " + t.getMessage(), t));
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

    /**
     * The JDK request of a raw exchange: the request cookies are sent in its Cookie header, and
     * the upload of its body is told to its listener, if any.
     */
    private HttpRequest toRawJdkRequest(URI uri, io.micronaut.http.HttpRequest<?> request, @Nullable Argument<?> bodyType) {
        HttpRequest.Builder builder = HttpRequestFactory.builder(uri, request, configuration, bodyType, mediaTypeCodecRegistry, messageBodyHandlerRegistry);
        HttpRequest built = builder.build();
        UploadListener listener = request.getAttribute(UPLOAD_LISTENER_ATTRIBUTE, UploadListener.class).orElse(null);
        HttpRequest.BodyPublisher publisher = built.bodyPublisher().orElse(null);
        if (listener == null || publisher == null || publisher.contentLength() == 0) {
            // no body, no upload: the whole exchange counts into the response timeout
            return built;
        }
        // the response timeout of the options pauses from the start to the end of the upload of the
        // body: the body publisher tells when the client takes it
        return HttpRequest.newBuilder(built, (name, value) -> true)
            .method(built.method(), new UploadListeningBodyPublisher(publisher, listener))
            .build();
    }

    /**
     * Notified of the upload of the body of a raw request.
     *
     * @param started  Completes when the upload starts
     * @param uploaded Completes once the body was uploaded
     */
    record UploadListener(CompletableFuture<@Nullable Void> started, CompletableFuture<@Nullable Void> uploaded) {
    }

    /**
     * A body publisher that tells its listener when the client takes the body, and when the
     * body was uploaded.
     *
     * @param delegate The body publisher
     * @param listener The listener
     */
    private record UploadListeningBodyPublisher(HttpRequest.BodyPublisher delegate, UploadListener listener)
        implements HttpRequest.BodyPublisher {

        @Override
        public long contentLength() {
            return delegate.contentLength();
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            listener.started().complete(null);
            delegate.subscribe(new Flow.Subscriber<ByteBuffer>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscriber.onSubscribe(subscription);
                }

                @Override
                public void onNext(ByteBuffer item) {
                    subscriber.onNext(item);
                }

                @Override
                public void onError(Throwable throwable) {
                    subscriber.onError(throwable);
                }

                @Override
                public void onComplete() {
                    subscriber.onComplete();
                    listener.uploaded().complete(null);
                }
            });
        }
    }
}
