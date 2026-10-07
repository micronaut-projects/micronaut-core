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
import io.micronaut.http.client.AbstractHttpClient;
import io.micronaut.http.client.ProxyHttpClient;
import io.micronaut.http.client.ProxyRequestOptions;
import io.micronaut.http.client.RawHttpClientSupport;
import io.micronaut.http.client.RawRequestOptions;
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
import java.util.stream.Collectors;

/**
 * Implementation of {@link RawHttpClient} for the JDK http client.
 *
 * @since 4.8.0
 * @author Jonas Konrad
 */
@Internal
final class JdkRawHttpClient extends AbstractJdkHttpClient implements RawHttpClient, ProxyHttpClient {
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
        return Mono.defer(() -> Mono.from(ReactiveExecutionFlow.toPublisher(http().rawExchangeFlow(rawRequest, blockedThread))))
            .doFinally(signal -> body.close());
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
        if (options.isAllowUpgrade() && request.getHeaders().contains(HttpHeaders.UPGRADE)) {
            // the JDK client gives no access to a connection that switched protocols
            if (requestBody != null) {
                requestBody.close();
            }
            return Mono.error(new HttpClientException("The JDK HTTP client cannot switch a connection to another protocol: the request asks to upgrade to '" +
                request.getHeaders().get(HttpHeaders.UPGRADE) + "'"));
        }
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
        if (!options.isFollowRedirects()) {
            request.setAttribute(AbstractHttpClient.NO_FOLLOW_REDIRECTS, Boolean.TRUE);
        }
        UploadListener listener = null;
        if (options.getResponseTimeout() != null) {
            // the response timeout does not count the upload of the body, see toJdkRequest
            listener = new UploadListener(new CompletableFuture<>(), new CompletableFuture<>());
            request.setAttribute(UPLOAD_LISTENER_ATTRIBUTE, listener);
        }
        UploadListener uploads = listener;
        Mono<MutableHttpResponse<?>> response = Mono.defer(() -> {
            ExecutionFlow<HttpResponse<?>> flow = http().rawExchangeFlow(request, null);
            if (uploads != null) {
                flow = RawHttpClientSupport.withResponseTimeout(flow, options.getResponseTimeout(), uploads.started(), uploads.uploaded());
            }
            return Mono.from(ReactiveExecutionFlow.toPublisher(flow.map(RawHttpClientSupport::toMutableResponse)));
        });
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

}
