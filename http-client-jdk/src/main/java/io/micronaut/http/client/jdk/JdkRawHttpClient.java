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
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.client.ProxyHttpClient;
import io.micronaut.http.client.ProxyRequestOptions;
import io.micronaut.http.client.RawHttpClientSupport;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.http.ByteBodyHttpResponseWrapper;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.stream.AvailableByteArrayBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.http.util.HttpHeadersUtil;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of {@link RawHttpClient} for the JDK http client.
 *
 * @since 4.8.0
 * @author Jonas Konrad
 */
@Internal
final class JdkRawHttpClient extends AbstractJdkHttpClient implements RawHttpClient, ProxyHttpClient {
    private static final String OPTIONS_ATTRIBUTE = "micronaut.http.client.raw.options";
    private static final String ALLOW_RESTRICTED_HEADERS_PROPERTY = "jdk.httpclient.allowRestrictedHeaders";
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

    @Override
    public Publisher<? extends HttpResponse<?>> exchange(HttpRequest<?> request, @Nullable CloseableByteBody requestBody, @Nullable Thread blockedThread) {
        // null is equivalent to an empty body
        CloseableByteBody body = requestBody == null ? AvailableByteArrayBody.create(ReadBufferFactory.getJdkFactory().createEmpty()) : requestBody;
        Flux<? extends HttpResponse<?>> response;
        try {
            response = exchangeImpl(new RawHttpRequestWrapper<>(conversionService, request.toMutableRequest(), body), null);
        } catch (RuntimeException | Error e) {
            // building the exchange failed, so nothing else releases the body
            body.close();
            throw e;
        }
        // the body is released however the exchange ends, also when the JDK client never reads
        // it, e.g. because the connection was refused or the request is a GET
        return response.doFinally(signal -> body.close());
    }

    @Override
    public Publisher<? extends HttpResponse<?>> exchange(HttpRequest<?> request, @Nullable CloseableByteBody requestBody, @Nullable Thread blockedThread, RawRequestOptions options) {
        Objects.requireNonNull(options, "options");
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
        Objects.requireNonNull(options, "options");
        // same behavior as the Netty client: redirects are followed as configured, and the host
        // header is computed from the URI unless it is retained
        RawRequestOptions rawOptions = RawRequestOptions.builder()
            .retainHostHeader(options.isRetainHostHeader())
            .build();
        // the body bytes of a server request are claimed when the exchange starts
        return Mono.defer(() -> {
            MutableHttpRequest<Object> copy = RawHttpClientSupport.copyRequest(request, rawOptions);
            CloseableByteBody serverBody = RawHttpClientSupport.claimServerRequestBody(request);
            MutableHttpRequest<?> proxyRequest;
            if (serverBody != null) {
                proxyRequest = new RawHttpRequestWrapper<>(conversionService, copy, serverBody);
            } else {
                request.getBody().ifPresent(copy::body);
                proxyRequest = copy;
            }
            return exchangeWithOptions(proxyRequest, serverBody, rawOptions);
        }).map(HttpResponse::toMutableResponse);
    }

    private Mono<MutableHttpResponse<?>> exchangeWithOptions(MutableHttpRequest<?> request, @Nullable CloseableByteBody requestBody, RawRequestOptions options) {
        Set<String> allowedRestrictedHeaders = allowedRestrictedHeaders();
        if (request.getHeaders().contains(HttpHeaders.HOST) && !allowedRestrictedHeaders.contains("host")) {
            if (requestBody != null) {
                requestBody.close();
            }
            return Mono.error(new HttpClientException("The JDK HTTP client can only send the Host header of the request if the '" +
                ALLOW_RESTRICTED_HEADERS_PROPERTY + "' system property includes 'host'. Set the property, e.g. -D" +
                ALLOW_RESTRICTED_HEADERS_PROPERTY + "=host, or do not retain the Host header"));
        }
        for (String header : RESTRICTED_HEADERS) {
            // content-length is always computed from the body
            if (header.equals(HttpHeaders.CONTENT_LENGTH) || !allowedRestrictedHeaders.contains(header.toLowerCase(Locale.ROOT))) {
                request.getHeaders().remove(header);
            }
        }
        request.setAttribute(OPTIONS_ATTRIBUTE, options);
        // the response timeout is the timeout of the JDK request, see mapToHttpRequest
        ExecutionFlow<HttpResponse<?>> flow = ReactiveExecutionFlow.fromPublisher(Mono.from(exchangeImpl(request, null)).map(r -> (HttpResponse<?>) r));
        Mono<MutableHttpResponse<?>> response = Mono.from(ReactiveExecutionFlow.toPublisher(
            flow.map(RawHttpClientSupport::toMutableResponse)
        ));
        if (requestBody != null) {
            // released unless they were sent, e.g. when the connection is refused, also when the
            // exchange is cancelled
            response = response.doFinally(signal -> requestBody.close());
        }
        return response;
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

    @Override
    java.net.http.HttpRequest toJdkRequest(URI uri, HttpRequest<?> request, @Nullable Argument<?> bodyType) {
        // the request cookies are sent in its Cookie header, and must not reach the cookie store
        // that is shared with the other clients of the same configuration
        java.net.http.HttpRequest.Builder builder = HttpRequestFactory.builder(uri, request, configuration, bodyType, mediaTypeCodecRegistry, messageBodyHandlerRegistry);
        Duration responseTimeout = responseTimeout(request);
        if (responseTimeout != null) {
            // it can only shorten the configured read timeout, like for the Netty client
            Duration readTimeout = configuration.getReadTimeout().orElse(null);
            builder.timeout(readTimeout != null && readTimeout.compareTo(responseTimeout) < 0 ? readTimeout : responseTimeout);
        }
        return builder.build();
    }

    private static @Nullable Duration responseTimeout(HttpRequest<?> request) {
        return request.getAttribute(OPTIONS_ATTRIBUTE, RawRequestOptions.class)
            .map(RawRequestOptions::getResponseTimeout)
            .orElse(null);
    }

    @Override
    <O> Publisher<HttpResponse<O>> responsePublisher(HttpRequest<?> request, ResolvedTarget target, @Nullable Argument<O> bodyType) {
        // built on subscription, so that any client filter changes are used
        return Mono.defer(() -> afterFilters(target, request))
            .flatMap(sent -> {
                java.net.http.HttpRequest httpRequest = toJdkRequest(sent.uri(), request, bodyType);
                if (log.isDebugEnabled()) {
                    log.debug("Client {} Sending HTTP Request: {}", clientId, httpRequest);
                }
                if (log.isTraceEnabled()) {
                    HttpHeadersUtil.trace(log,
                        () -> httpRequest.headers().map().keySet(),
                        headerName -> httpRequest.headers().allValues(headerName));
                }
                BodySizeLimits bodySizeLimits = new BodySizeLimits(Long.MAX_VALUE, configuration.getMaxContentLength());
                RawRequestOptions options = request.getAttribute(OPTIONS_ATTRIBUTE, RawRequestOptions.class).orElse(null);
                // a raw client relays exchanges of different users, so it must not keep the cookies an upstream sets
                java.net.http.HttpClient httpClient = options == null || options.isFollowRedirects() ? rawClient.get() : rawNoRedirectClient.get();
                // the outcome is reported once the body ends: a response whose body is cut off is a failure
                return Mono.fromCompletionStage(httpClient.sendAsync(httpRequest, responseInfo -> new ByteBodySubscriber(bodySizeLimits,
                        failure -> reportBodyEnd(target.instance(), responseInfo.statusCode(), failure))))
                    .onErrorMap(
                        e -> e instanceof HttpTimeoutException && !(e instanceof HttpConnectTimeoutException) && responseTimeout(request) != null,
                        e -> {
                            report(target.instance(), LoadBalancer.Outcome.TIMEOUT);
                            return ReadTimeoutException.TIMEOUT_EXCEPTION;
                        }
                    )
                    .onErrorMap(IOException.class, e -> sendError(sent.instance(), httpRequest.uri(), e));
            })
            .onErrorMap(InterruptedException.class, e -> new HttpClientException("Error sending request: " + e.getMessage(), e))
            .map(netResponse -> {
                if (log.isDebugEnabled()) {
                    log.debug("Client {} Received HTTP Response: {} {}", clientId, netResponse.statusCode(), netResponse.uri());
                }

                ByteBodyHttpResponse<?> response = ByteBodyHttpResponseWrapper.wrap(new BaseHttpResponseAdapter<CloseableByteBody, O>(netResponse, conversionService) {
                    @Override
                    public Optional<O> getBody() {
                        return Optional.empty();
                    }
                }, netResponse.body());
                //noinspection unchecked
                return (HttpResponse<O>) response;
            });
    }
}
