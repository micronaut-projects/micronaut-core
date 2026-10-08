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
import java.net.HttpCookie;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.concurrent.Flow;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;
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
    protected final HttpClientConfiguration configuration;
    protected final HttpClient client;
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
     * {@link HttpClientConfiguration#isJdkMicronautRedirects()}, else by the JDK client.
     */
    final boolean micronautRedirects;
    /**
     * The client for raw exchanges and for proxying: it follows redirects as configured, and
     * keeps no cookies, because the exchanges a raw client relays belong to different users.
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
        this.micronautRedirects = configuration.isJdkMicronautRedirects();
        HttpClient.Redirect redirect = configuration.isFollowRedirects() && !micronautRedirects ? HttpClient.Redirect.NORMAL : HttpClient.Redirect.NEVER;
        this.client = buildClient(redirect, true);
        Supplier<HttpClient> rawNoRedirect = SupplierUtil.memoized(() -> buildClient(HttpClient.Redirect.NEVER, false));
        this.rawClient = redirect == HttpClient.Redirect.NORMAL ? SupplierUtil.memoized(() -> buildClient(redirect, false)) : rawNoRedirect;
        this.rawNoRedirectClient = rawNoRedirect;
    }

    private HttpClient buildClient(HttpClient.Redirect redirect, boolean cookies) {
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

        builder.followRedirects(redirect);
        if (cookies) {
            builder.cookieHandler(cookieManager);
        }

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

    private static HttpCookie toJdkCookie(Cookie cookie,
                                          io.micronaut.http.HttpRequest<?> request,
                                          String host) {
        HttpCookie newCookie = new HttpCookie(cookie.getName(), cookie.getValue());
        newCookie.setMaxAge(cookie.getMaxAge());
        newCookie.setDomain(host);
        newCookie.setHttpOnly(cookie.isHttpOnly());
        newCookie.setSecure(cookie.isSecure());
        newCookie.setPath(cookie.getPath() == null ? request.getPath() : cookie.getPath());
        return newCookie;
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
                    protected PasswordAuthentication getPasswordAuthentication() {
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
        cookieDecoder.decode(request).ifPresent(cookies -> cookies.getAll().forEach(cookie -> {
            HttpCookie newCookie = toJdkCookie(cookie, request, uri.getHost());
            cookieManager.getCookieStore().add(uri, newCookie);
        }));

        return HttpRequestFactory.builder(uri, request, configuration, bodyType, mediaTypeCodecRegistry, messageBodyHandlerRegistry).build();
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
            result = new HttpClientException("Error sending request: " + e.getMessage(), e);
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
        // a raw client relays exchanges of different users, so it must not keep the cookies an upstream sets
        boolean raw = request.getAttribute(RAW_ATTRIBUTE).isPresent();
        HttpClient httpClient;
        if (raw) {
            // the JDK client follows the redirects of a raw exchange unless its options disable them
            httpClient = request.getAttribute(AbstractHttpClient.NO_FOLLOW_REDIRECTS).isPresent() ? rawNoRedirectClient.get() : rawClient.get();
        } else {
            httpClient = client;
        }
        if (!raw && bufferedHeaders != null) {
            return sendBuffered(httpClient, httpRequest, selection, bufferedHeaders);
        }
        // whether the headers arrived, so that a failure of the body is told from one before
        AtomicBoolean headersReceived = new AtomicBoolean();
        DelayedExecutionFlow<JdkByteBodyResponse> result = DelayedExecutionFlow.create();
        CompletableFuture<java.net.http.HttpResponse<CloseableByteBody>> sent;
        try {
            sent = httpClient.sendAsync(httpRequest, responseInfo -> {
                headersReceived.set(true);
                // the end of the body reports the outcome, or releases the selection
                if (selection != null) {
                    selection.claim();
                }
                // the body of a stream fails with a client exception; the body of a raw exchange
                // fails with the failure of the JDK client, as it always did
                return new ByteBodySubscriber(sizeLimits, !raw, failure -> reportBodyEnd(selection, responseInfo.statusCode(), failure));
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
                mapped = new HttpClientException("Error sending request: " + cause.getMessage(), cause);
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
                                                            @Nullable LoadBalancerSelection selection,
                                                            AtomicBoolean headersReceived) {
        DelayedExecutionFlow<JdkByteBodyResponse> result = DelayedExecutionFlow.create();
        CompletableFuture<java.net.http.HttpResponse<byte[]>> sent;
        try {
            sent = httpClient.sendAsync(httpRequest, responseInfo -> {
                headersReceived.set(true);
                return java.net.http.HttpResponse.BodySubscribers.ofByteArray();
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
                mapped = new HttpClientException("Error sending request: " + cause.getMessage(), cause);
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
     * The JDK request of a raw exchange: the request cookies are sent in its Cookie header, and
     * must not reach the cookie store that is shared with the other clients of the same
     * configuration, and the upload of its body is told to its listener, if any.
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
