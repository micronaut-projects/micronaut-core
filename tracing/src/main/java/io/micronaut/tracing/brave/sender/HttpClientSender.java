/*
 * Copyright 2018 original authors
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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An {@link Sender} implementation that uses Micronaut's {@link io.micronaut.http.client.HttpClient}
 *
 * @author graemerocher
 * @since 1.0
 */
public class HttpClientSender extends Sender{

    private final HttpClient httpClient ;
    private final Encoding encoding;
    private final int messageMaxBytes;
    private final boolean compressionEnabled;
    private final URI endpoint;

    private HttpClientSender(Encoding encoding, int messageMaxBytes, boolean compressionEnabled, URI endpoint) {
        try {
            this.httpClient = HttpClient.create(new URL(endpoint.getScheme(), endpoint.getHost(), endpoint.getPort(), ""));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid endpoint URI: " + endpoint);
        }
        this.encoding = encoding;
        this.messageMaxBytes = messageMaxBytes;
        this.compressionEnabled = compressionEnabled;
        this.endpoint = URI.create(endpoint.getPath());
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
        if(httpClient.isRunning()) {
            return new HttpCall(httpClient, endpoint, compressionEnabled,encodedSpans);
        }
        else {
            throw new IllegalStateException("HTTP Client Closed");
        }
    }

    @Override
    public CheckResult check() {
        try {
            HttpResponse<Object> response = httpClient.toBlocking().exchange(HttpRequest.POST(endpoint, Collections.emptyList()));
            if(response.getStatus().getCode() < 300) {
                return CheckResult.OK;
            }
            else {
                throw new IllegalStateException("check response failed: " + response);
            }
        } catch (Exception e) {
            return CheckResult.failed(e);
        }
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }

    private static class HttpCall extends Call<Void> {
        private final HttpClient httpClient;
        private final URI endpoint;
        private final boolean compressionEnabled;
        private final List<byte[]> encodedSpans;

        private AtomicReference<Subscription> subscription = new AtomicReference<>();
        private AtomicBoolean cancelled = new AtomicBoolean(false);

        public HttpCall(HttpClient httpClient, URI endpoint, boolean compressionEnabled, List<byte[]> encodedSpans) {
            this.httpClient = httpClient;
            this.endpoint = endpoint;
            this.compressionEnabled = compressionEnabled;
            this.encodedSpans = encodedSpans;
        }

        @Override
        public Void execute() throws IOException {
            BlockingHttpClient blockingHttpClient = httpClient.toBlocking();
            HttpResponse<Object> response = blockingHttpClient.exchange(prepareRequest());
            if(response.getStatus().getCode() >= 400) {
                throw new IllegalStateException("Response return invalid status code: " + response.getStatus());
            }
            return null;
        }

        private MutableHttpRequest<Flowable<Object>> prepareRequest() {
            MutableHttpRequest<Flowable<Object>> request = HttpRequest.POST(endpoint, spanFlowable());
            if(compressionEnabled) {
                return request.contentEncoding("gzip");
            }
            else {
                return request;
            }
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
                    if(response.getStatus().getCode() >= 400) {
                        callback.onError(new IllegalStateException("Response return invalid status code: " + response.getStatus()));
                    }
                    else {
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
            if(s != null) {
                cancelled.set(true);
                s.cancel();
            }
        }

        @Override
        public boolean isCanceled() {
            Subscription s = this.subscription.get();
            if(s != null) {
                return cancelled.get();
            }
            return false;
        }

        @Override
        public Call<Void> clone() {
            // stateless. no need to clone
            return new HttpCall(httpClient, endpoint,compressionEnabled, encodedSpans);
        }

        private Flowable<Object> spanFlowable() {
            return Flowable.create(emitter -> {
                for (byte[] encodedSpan : encodedSpans) {
                    emitter.onNext(encodedSpan);
                }
            }, BackpressureStrategy.BUFFER);
        }
    }

    /**
     * Constructs the {@link HttpClientSender}
     */
    public static class Builder {
        public static final String DEFAULT_ENDPOINT = "http://localhost:9411/api/v2/spans";
        private Encoding encoding = Encoding.JSON;
        private int messageMaxBytes = 5 * 1024;
        private boolean compressionEnabled = true;
        private URI endpoint = URI.create(DEFAULT_ENDPOINT);

        /**
         * The encoding to use. Defaults to {@link Encoding#JSON}
         * @param encoding The encoding
         * @return This builder
         */
        public Builder encoding(Encoding encoding) {
            if(encoding != null) {
                this.encoding = encoding;
            }
            return this;
        }

        /**
         * The message max bytes
         * @param messageMaxBytes The max bytes
         * @return This builder
         */
        public Builder messageMaxBytes(int messageMaxBytes) {
            this.messageMaxBytes = messageMaxBytes;
            return this;
        }

        /**
         * Whether compression is enabled (defaults to true)
         * @param compressionEnabled True if compression is enabled
         * @return This builder
         */
        public Builder compressionEnabled(boolean compressionEnabled) {
            this.compressionEnabled = compressionEnabled;
            return this;
        }

        /**
         * The endpoint to use
         *
         * @param endpoint The fully qualified URI of the Zipkin endpoint
         * @return This builder
         */
        public Builder endpoint(URI endpoint) {
            if(endpoint != null) {
                this.endpoint = endpoint;
            }
            return this;
        }

        /**
         * Consructs a {@link HttpClientSender}
         * @return The sender
         */
        public HttpClientSender build() {
            return new HttpClientSender(
                    encoding,
                    messageMaxBytes,
                    compressionEnabled,
                    endpoint
            );
        }
    }

}
