/*
 * Copyright 2017-2024 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.client.AbstractHttpClient;
import io.micronaut.http.client.AsyncProxyHttpClient;
import io.micronaut.http.client.ProxyHttpClient;
import io.micronaut.http.client.ProxyRequestOptions;
import io.micronaut.http.client.RawHttpClientSupport;
import io.micronaut.http.client.internal.RawHttpRequestWrapper;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.client.RawResponseFuture;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import org.jspecify.annotations.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.stream.AvailableByteArrayBody;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.exceptions.HttpClientException;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Implementation of {@link RawHttpClient} for the JDK http client.
 *
 * @since 4.8.0
 * @author Jonas Konrad
 */
@Internal
final class JdkRawHttpClient extends AbstractJdkHttpClient implements RawHttpClient, ProxyHttpClient {
    private static final String OPTIONS_PARAMETER = "options";
    private static final String ALLOW_RESTRICTED_HEADERS_PROPERTY = "jdk.httpclient.allowRestrictedHeaders";
    /**
     * Request attribute with the {@link UploadListener} of the request body.
     */
    /**
     * The headers {@link java.net.http.HttpClient} manages itself, and refuses to take from the
     * request unless {@value #ALLOW_RESTRICTED_HEADERS_PROPERTY} allows them.
     */
    private static final List<String> RESTRICTED_HEADERS = List.of(
        HttpHeaders.CONNECTION,
        HttpHeaders.CONTENT_LENGTH,
        HttpHeaders.EXPECT,
        HttpHeaders.UPGRADE
    );

    public JdkRawHttpClient(AbstractJdkHttpClient prototype) {
        super(prototype);
    }

    /**
     * @param client The client whose pipeline sends the exchanges
     */
    public JdkRawHttpClient(DefaultJdkHttpClient client) {
        this(client.transport());
    }

    @Override
    @SuppressWarnings("java:S2095") // the request wrapper only holds the body, which the exchange releases
    public Publisher<? extends HttpResponse<?>> exchange(HttpRequest<?> request, @Nullable CloseableByteBody requestBody, @Nullable Thread blockedThread) {
        // null is equivalent to an empty body
        CloseableByteBody body = requestBody == null ? AvailableByteArrayBody.create(ReadBufferFactory.getJdkFactory().createEmpty()) : requestBody;
        MutableHttpRequest<?> rawRequest;
        try {
            rawRequest = new RawHttpRequestWrapper<>(conversionService, request.toMutableRequest(), body);
        } catch (RuntimeException | Error e) {
            // building the exchange failed, so nothing else releases the body
            body.close();
            throw e;
        }
        // the body is released however the exchange ends, also when the JDK client never reads
        // it, e.g. because the connection was refused or the request is a GET
        return Mono.defer(() -> Mono.from(ReactiveExecutionFlow.toPublisher(pipelineClient().rawExchangeFlow(rawRequest, blockedThread))))
            .doFinally(signal -> body.close());
    }

    @Override
    @SuppressWarnings("java:S2095") // the request wrapper only holds the body, which the exchange releases
    public Publisher<? extends HttpResponse<?>> exchange(HttpRequest<?> request, @Nullable CloseableByteBody requestBody, @Nullable Thread blockedThread, RawRequestOptions options) {
        Objects.requireNonNull(options, OPTIONS_PARAMETER);
        MutableHttpRequest<?> rawRequest;
        try {
            MutableHttpRequest<Object> copy = RawHttpClientSupport.copyRequest(request, options);
            if (requestBody != null) {
                rawRequest = new RawHttpRequestWrapper<>(conversionService, copy, requestBody);
            } else {
                rawRequest = copy;
            }
        } catch (RuntimeException | Error e) {
            if (requestBody != null) {
                requestBody.close();
            }
            throw e;
        }
        return exchangeWithOptions(rawRequest, requestBody, options);
    }

    @Override
    public Publisher<MutableHttpResponse<?>> proxy(HttpRequest<?> request) {
        return proxy(request, ProxyRequestOptions.getDefault());
    }

    @Override
    public Publisher<MutableHttpResponse<?>> proxy(HttpRequest<?> request, ProxyRequestOptions options) {
        Objects.requireNonNull(options, OPTIONS_PARAMETER);
        // the body bytes of a server request are claimed when the exchange starts
        return Mono.defer(() -> {
            ProxyExchange exchange = proxyExchange(request, options);
            return exchangeWithOptions(exchange.request(), exchange.serverBody(), exchange.options());
        }).map(HttpResponse::toMutableResponse);
    }

    @Override
    public AsyncProxyHttpClient toAsyncProxy() {
        return new JdkAsyncProxyHttpClient(this);
    }

    /**
     * The proxied exchange of {@link JdkAsyncProxyHttpClient}: {@link #proxy} without Reactor.
     * The claimed body of a server request is released once the exchange completes or is
     * cancelled.
     *
     * @param request The request to proxy
     * @param options The options
     * @return The future of the response
     */
    CompletionStage<MutableHttpResponse<?>> proxyAsync(HttpRequest<?> request, ProxyRequestOptions options) {
        Objects.requireNonNull(options, OPTIONS_PARAMETER);
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        ProxyExchange exchange;
        try {
            exchange = proxyExchange(request, options);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        CloseableByteBody serverBody = exchange.serverBody();
        HttpClientException refused = prepare(exchange.request(), exchange.options());
        if (refused != null) {
            if (serverBody != null) {
                serverBody.close();
            }
            return CompletableFuture.failedFuture(refused);
        }
        ExecutionFlow<MutableHttpResponse<?>> flow;
        try {
            flow = send(exchange.request(), exchange.options());
        } catch (RuntimeException e) {
            flow = ExecutionFlow.error(e);
        }
        return RawResponseFuture.ofMutable(flow, serverBody == null ? null : serverBody::close, propagatedContext);
    }

    /**
     * The request of a proxied exchange: the same behavior as the Netty client, redirects are
     * followed as configured, and the host header is computed from the URI unless it is retained.
     * The body bytes of a server request are claimed.
     */
    private ProxyExchange proxyExchange(HttpRequest<?> request, ProxyRequestOptions options) {
        RawRequestOptions rawOptions = RawRequestOptions.builder()
            .retainHostHeader(options.isRetainHostHeader())
            .build();
        MutableHttpRequest<Object> copy = RawHttpClientSupport.copyRequest(request, rawOptions);
        CloseableByteBody serverBody = RawHttpClientSupport.claimServerRequestBody(request);
        if (serverBody != null) {
            return new ProxyExchange(new RawHttpRequestWrapper<>(conversionService, copy, serverBody), serverBody, rawOptions);
        }
        request.getBody().ifPresent(copy::body);
        return new ProxyExchange(copy, null, rawOptions);
    }

    private Mono<MutableHttpResponse<?>> exchangeWithOptions(MutableHttpRequest<?> request, @Nullable CloseableByteBody requestBody, RawRequestOptions options) {
        HttpClientException refused = prepare(request, options);
        if (refused != null) {
            if (requestBody != null) {
                requestBody.close();
            }
            return Mono.error(refused);
        }
        Mono<MutableHttpResponse<?>> response = Mono.defer(() -> Mono.from(ReactiveExecutionFlow.toPublisher(send(request, options))));
        if (requestBody != null) {
            // released unless they were sent, e.g. when the connection is refused, also when the
            // exchange is cancelled
            response = response.doFinally(signal -> requestBody.close());
        }
        return response;
    }

    /**
     * Prepare a request for the JDK client, or refuse it.
     *
     * @param request The request, whose headers and attributes are adjusted
     * @param options The options
     * @return Why the JDK client cannot send the request, or {@code null}
     */
    private static @Nullable HttpClientException prepare(MutableHttpRequest<?> request, RawRequestOptions options) {
        if (options.isAllowUpgrade() && request.getHeaders().contains(HttpHeaders.UPGRADE)) {
            // the JDK client gives no access to a connection that switched protocols
            return new HttpClientException("The JDK HTTP client cannot switch a connection to another protocol: the request asks to upgrade to '" +
                request.getHeaders().get(HttpHeaders.UPGRADE) + "'");
        }
        Set<String> allowedRestrictedHeaders = allowedRestrictedHeaders();
        if (request.getHeaders().contains(HttpHeaders.HOST) && !allowedRestrictedHeaders.contains("host")) {
            return new HttpClientException("The JDK HTTP client can only send the Host header of the request if the '" +
                ALLOW_RESTRICTED_HEADERS_PROPERTY + "' system property includes 'host'. Set the property, e.g. -D" +
                ALLOW_RESTRICTED_HEADERS_PROPERTY + "=host, or do not retain the Host header");
        }
        for (String header : RESTRICTED_HEADERS) {
            // content-length is always computed from the body
            if (header.equals(HttpHeaders.CONTENT_LENGTH) || !allowedRestrictedHeaders.contains(header.toLowerCase(Locale.ROOT))) {
                request.getHeaders().remove(header);
            }
        }
        if (!options.isFollowRedirects()) {
            request.setAttribute(AbstractHttpClient.NO_FOLLOW_REDIRECTS, Boolean.TRUE);
        }
        return null;
    }

    /**
     * Send a prepared request.
     *
     * @param request The request
     * @param options The options
     * @return The flow of the response
     */
    private ExecutionFlow<MutableHttpResponse<?>> send(MutableHttpRequest<?> request, RawRequestOptions options) {
        UploadListener uploads = null;
        if (options.getResponseTimeout() != null) {
            // the response timeout does not count the upload of the body, see toJdkRequest
            uploads = new UploadListener(new CompletableFuture<>(), new CompletableFuture<>());
            request.setAttribute(UPLOAD_LISTENER_ATTRIBUTE, uploads);
        }
        ExecutionFlow<HttpResponse<?>> flow = pipelineClient().rawExchangeFlow(request, null);
        if (uploads != null) {
            flow = RawHttpClientSupport.withResponseTimeout(flow, options.getResponseTimeout(), uploads.started(), uploads.uploaded());
        }
        return flow.map(RawHttpClientSupport::toMutableResponse);
    }

    private static Set<String> allowedRestrictedHeaders() {
        String property = System.getProperty(ALLOW_RESTRICTED_HEADERS_PROPERTY);
        if (property == null) {
            return Set.of();
        }
        return Arrays.stream(property.split(","))
            .map(name -> name.trim().toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
    }

    @Override
    public void close() {
        // Nothing to do here, we do not need to close clients
    }

    /**
     * A proxied exchange.
     *
     * @param request    The request
     * @param serverBody The claimed body of a server request, or {@code null}
     * @param options    The options
     */
    private record ProxyExchange(MutableHttpRequest<?> request, @Nullable CloseableByteBody serverBody, RawRequestOptions options) {
    }
}
