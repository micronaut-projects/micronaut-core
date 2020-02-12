/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.tracing.brave.sender;

import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.discovery.exceptions.NoAvailableServiceException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.*;
import io.micronaut.http.client.netty.DefaultHttpClient;
import io.micronaut.tracing.brave.ZipkinServiceInstanceList;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import zipkin2.Call;
import zipkin2.Callback;
import zipkin2.CheckResult;
import zipkin2.codec.Encoding;
import zipkin2.reporter.Sender;

import javax.inject.Provider;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An {@link Sender} implementation that uses Micronaut's {@link io.micronaut.http.client.HttpClient}.
 *
 * @author graemerocher
 * @since 1.0
 */
public final class HttpClientSender extends Sender {

    private final Encoding encoding;
    private final int messageMaxBytes;
    private final boolean compressionEnabled;
    private final URI endpoint;
    private final Provider<LoadBalancerResolver> loadBalancerResolver;
    private final HttpClientConfiguration clientConfiguration;
    private HttpClient httpClient;

    private HttpClientSender(
            Encoding encoding,
            int messageMaxBytes,
            boolean compressionEnabled,
            HttpClientConfiguration clientConfiguration,
            Provider<LoadBalancerResolver> loadBalancerResolver,
            String path) {
        this.loadBalancerResolver = loadBalancerResolver;
        this.clientConfiguration = clientConfiguration;
        this.encoding = encoding;
        this.messageMaxBytes = messageMaxBytes;
        this.compressionEnabled = compressionEnabled;
        this.endpoint = path != null ? URI.create(path) : URI.create(Builder.DEFAULT_PATH);
    }

    @Override
    public Encoding encoding() {
        return encoding;
    }

    @Override
    public int messageMaxBytes() {
        return messageMaxBytes;
    }

    @Override
    public int messageSizeInBytes(List<byte[]> encodedSpans) {
        return encoding().listSizeInBytes(encodedSpans);
    }

    @Override
    public Call<Void> sendSpans(List<byte[]> encodedSpans) {
        initHttpClient();
        if (httpClient != null && httpClient.isRunning()) {
            return new HttpCall(httpClient, endpoint, compressionEnabled, encodedSpans);
        } else {
            throw new IllegalStateException("HTTP Client Closed");
        }
    }

    @Override
    public CheckResult check() {
        initHttpClient();

        if (httpClient == null) {
            return CheckResult.failed(new NoAvailableServiceException(ZipkinServiceInstanceList.SERVICE_ID));
        }

        try {
            HttpResponse<Object> response = httpClient.toBlocking().exchange(HttpRequest.POST(endpoint, Collections.emptyList()));
            if (response.getStatus().getCode() < HttpStatus.MULTIPLE_CHOICES.getCode()) {
                return CheckResult.OK;
            } else {
                throw new IllegalStateException("check response failed: " + response);
            }
        } catch (Exception e) {
            return CheckResult.failed(e);
        }
    }

    private void initHttpClient() {
        if (this.httpClient == null) {
            final Optional<? extends LoadBalancer> loadBalancer = loadBalancerResolver.get().resolve(ZipkinServiceInstanceList.SERVICE_ID);

            this.httpClient = loadBalancer.map(lb -> new DefaultHttpClient(
                    lb,
                    clientConfiguration
            )).orElse(null);
        }
    }

    @Override
    public void close() {
        if (httpClient != null) {
            httpClient.close();
        }
    }

    /**
     * The HTTP call.
     */
    private static class HttpCall extends Call<Void> {
        private final HttpClient httpClient;
        private final URI endpoint;
        private final boolean compressionEnabled;
        private final List<byte[]> encodedSpans;

        private AtomicReference<Subscription> subscription = new AtomicReference<>();
        private AtomicBoolean cancelled = new AtomicBoolean(false);

        HttpCall(HttpClient httpClient, URI endpoint, boolean compressionEnabled, List<byte[]> encodedSpans) {
            this.httpClient = httpClient;
            this.endpoint = endpoint;
            this.compressionEnabled = compressionEnabled;
            this.encodedSpans = encodedSpans;
        }

        @Override
        public Void execute() throws IOException {
            BlockingHttpClient blockingHttpClient = httpClient.toBlocking();
            HttpResponse<Object> response = blockingHttpClient.exchange(prepareRequest());
            if (response.getStatus().getCode() >= HttpStatus.BAD_REQUEST.getCode()) {
                throw new IllegalStateException("Response return invalid status code: " + response.getStatus());
            }
            return null;
        }

        @Override
        public void enqueue(Callback<Void> callback) {
            Publisher<HttpResponse<ByteBuffer>> publisher = httpClient.exchange(prepareRequest());
            publisher.subscribe(new Subscriber<HttpResponse<ByteBuffer>>() {
                @Override
                public void onSubscribe(Subscription s) {
                    subscription.set(s);
                    s.request(1);
                }

                @Override
                public void onNext(HttpResponse<ByteBuffer> response) {
                    if (response.getStatus().getCode() >= HttpStatus.BAD_REQUEST.getCode()) {
                        callback.onError(new IllegalStateException("Response return invalid status code: " + response.getStatus()));
                    } else {
                        callback.onSuccess(null);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    callback.onError(t);
                }

                @Override
                public void onComplete() {

                }
            });
        }

        @Override
        public void cancel() {
            Subscription s = this.subscription.get();
            if (s != null) {
                cancelled.set(true);
                s.cancel();
            }
        }

        @Override
        public boolean isCanceled() {
            Subscription s = this.subscription.get();
            if (s != null) {
                return cancelled.get();
            }
            return false;
        }

        @Override
        public Call<Void> clone() {
            // stateless. no need to clone
            return new HttpCall(httpClient, endpoint, compressionEnabled, encodedSpans);
        }

        protected MutableHttpRequest<Flowable<Object>> prepareRequest() {
            return HttpRequest.POST(endpoint, spanFlowable());
        }

        private Flowable<Object> spanFlowable() {
            return Flowable.create(emitter -> {
                for (byte[] encodedSpan : encodedSpans) {
                    emitter.onNext(encodedSpan);
                }
                emitter.onComplete();
            }, BackpressureStrategy.BUFFER);
        }
    }

    /**
     * Constructs the {@link HttpClientSender}.
     */
    public static class Builder {
        public static final String DEFAULT_PATH = "/api/v2/spans";
        public static final String DEFAULT_SERVER_URL = "http://localhost:9411";

        private Encoding encoding = Encoding.JSON;
        private int messageMaxBytes = 5 * 1024;
        private String path = DEFAULT_PATH;
        private boolean compressionEnabled = true;
        private List<URI> servers = Collections.singletonList(URI.create(DEFAULT_SERVER_URL));
        private final HttpClientConfiguration clientConfiguration;

        /**
         * Initialize the builder with HTTP client configurations.
         *
         * @param clientConfiguration The HTTP client configuration
         */
        public Builder(HttpClientConfiguration clientConfiguration) {
            this.clientConfiguration = clientConfiguration;
        }

        /**
         * @return The configured zipkin servers
         *
         */
        public List<URI> getServers() {
            return servers;
        }

        /**
         * The encoding to use. Defaults to {@link Encoding#JSON}
         * @param encoding The encoding
         * @return This builder
         */
        public Builder encoding(Encoding encoding) {
            if (encoding != null) {
                this.encoding = encoding;
            }
            return this;
        }

        /**
         * The message max bytes.
         *
         * @param messageMaxBytes The max bytes
         * @return This builder
         */
        public Builder messageMaxBytes(int messageMaxBytes) {
            this.messageMaxBytes = messageMaxBytes;
            return this;
        }

        /**
         * Whether compression is enabled (defaults to true).
         *
         * @param compressionEnabled True if compression is enabled
         * @return This builder
         */
        public Builder compressionEnabled(boolean compressionEnabled) {
            this.compressionEnabled = compressionEnabled;
            return this;
        }

        /**
         * The endpoint to use.
         *
         * @param endpoint The fully qualified URI of the Zipkin endpoint
         * @return This builder
         */
        public Builder server(URI endpoint) {
            if (endpoint != null) {
                this.servers = Collections.singletonList(endpoint);
            }
            return this;
        }

        /**
         * The endpoint to use.
         *
         * @param endpoint The fully qualified URI of the Zipkin endpoint
         * @return This builder
         */
        public Builder url(URI endpoint) {
            return server(endpoint);
        }

        /**
         * The endpoint to use.
         *
         * @param urls The zipkin server URLs
         * @return This builder
         */
        public Builder urls(List<URI> urls) {
            if (CollectionUtils.isNotEmpty(urls)) {
                this.servers = Collections.unmodifiableList(urls);
            }
            return this;
        }

        /**
         * Constructs a {@link HttpClientSender}.
         *
         * @param loadBalancerResolver Resolver instance capable of resolving references to services into a concrete load-balance
         * @return The sender
         */
        public HttpClientSender build(Provider<LoadBalancerResolver> loadBalancerResolver) {
            return new HttpClientSender(
                    encoding,
                    messageMaxBytes,
                    compressionEnabled,
                    clientConfiguration,
                    loadBalancerResolver,
                    path
            );
        }
    }

}
