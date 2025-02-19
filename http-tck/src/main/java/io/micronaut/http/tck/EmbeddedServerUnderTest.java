/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.tck;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Optional;

/**
 * {@link ServerUnderTest} implementation for {@link EmbeddedServer}.
 * @author Sergio del Amo
 * @since 3.0.0
 */
@Experimental
public class EmbeddedServerUnderTest implements ServerUnderTest {

    private final boolean isBlockingClient;
    private final EmbeddedServer embeddedServer;
    private HttpClient httpClient;
    private BlockingHttpClient client;

    public EmbeddedServerUnderTest(@NonNull Map<String, Object> properties) {
        properties.putIfAbsent("micronaut.server.port", -1);
        properties.putIfAbsent("micronaut.server.ssl.port", -1);
        this.embeddedServer = ApplicationContext.run(EmbeddedServer.class, properties);
        this.isBlockingClient = (boolean) properties.getOrDefault(BLOCKING_CLIENT_PROPERTY, true);
    }

    @Override
    public <I, O> HttpResponse<O> exchange(HttpRequest<I> request, Argument<O> bodyType) {
        if (isBlockingClient) {
            return getBlockingHttpClient().exchange(request, bodyType);
        } else {
            return Flux.from(getHttpClient().exchange(request, bodyType)).blockFirst();
        }
    }

    @Override
    public <I, O, E> HttpResponse<O> exchange(HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType) {
        if (isBlockingClient) {
            return getBlockingHttpClient().exchange(request, bodyType, errorType);
        } else {
            return Flux.from(getHttpClient().exchange(request, bodyType, errorType)).blockFirst();
        }
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return embeddedServer.getApplicationContext();
    }

    @Override
    public void close() throws IOException {
        if (client != null) {
            client.close();
        }
        if (httpClient != null) {
            httpClient.close();
        }
        if (embeddedServer != null) {
            embeddedServer.getApplicationContext().close();
            embeddedServer.close();
        }
    }

    @Override
    public Optional<String> getScheme() {
        return Optional.ofNullable(embeddedServer).map(EmbeddedServer::getScheme);
    }

    @Override
    @NonNull
    public Optional<Integer> getPort() {
        return Optional.ofNullable(embeddedServer).map(EmbeddedServer::getPort);
    }

    @Override
    @NonNull
    public Optional<URL> getURL() {
        return Optional.ofNullable(embeddedServer).map(EmbeddedServer::getURL);
    }

    @NonNull
    private HttpClient getHttpClient() {
        if (httpClient == null) {
            this.httpClient = getApplicationContext().createBean(HttpClient.class, embeddedServer.getURL());
        }
        return httpClient;
    }

    @NonNull
    private BlockingHttpClient getBlockingHttpClient() {
        if (client == null) {
            this.client = getHttpClient().toBlocking();
        }
        return client;
    }
}
