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
package io.micronaut.tracing.brave;

import io.micronaut.http.client.HttpClient;
import zipkin2.Call;
import zipkin2.Callback;
import zipkin2.CheckResult;
import zipkin2.codec.Encoding;
import zipkin2.reporter.Sender;

import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * An {@link Sender} implementation that uses Micronaut's {@link io.micronaut.http.client.HttpClient}
 *
 * @author graemerocher
 * @since 1.0
 */
public class HttpClientSender extends Sender {

    private final HttpClient httpClient;
    private final Encoding encoding;
    private final int messageMaxBytes;
    private final boolean compressionEnabled;
    private final URI endpoint;

    public HttpClientSender(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.encoding = Encoding.JSON;
        this.messageMaxBytes = 1024 * 5; // 5MB by default
        this.compressionEnabled = true;
        this.endpoint = URI.create("http://localhost:9411/api/v2/spans");
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
//            httpClient.exchange(HttpRequest.POST(endpoint.toString(), ))
            return new Call<Void>() {
                @Override
                public Void execute() throws IOException {
                    return null;
                }

                @Override
                public void enqueue(Callback<Void> callback) {

                }

                @Override
                public void cancel() {

                }

                @Override
                public boolean isCanceled() {
                    return false;
                }

                @Override
                public Call<Void> clone() {
                    return null;
                }
            };
        }
        else {
            throw new IllegalStateException("HTTP Client Closed");
        }
    }

    @Override
    public CheckResult check() {
        return super.check();
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }
}
