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
package io.micronaut.http.client.javanet;

import io.micronaut.core.annotation.AnnotationMetadataResolver;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.bind.DefaultRequestBinderRegistry;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.loadbalance.FixedLoadBalancer;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.codec.JsonMediaTypeCodec;
import io.micronaut.json.codec.JsonStreamMediaTypeCodec;
import io.micronaut.runtime.ApplicationConfiguration;
import org.reactivestreams.Publisher;

import java.net.URI;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

/**
 * {@link HttpClient} implementation for {@literal java.net.http.*} HTTP Client.
 * @author Sergio del Amo
 * @since 4.0.0
 */
@Internal
public final class JavanetHttpClient extends AbstractJavanetHttpClient implements HttpClient {

    public JavanetHttpClient(
        LoadBalancer loadBalancer,
        HttpVersionSelection httpVersion,
        HttpClientConfiguration configuration,
        String contextPath,
        MediaTypeCodecRegistry mediaTypeCodecRegistry,
        RequestBinderRegistry orElseGet,
        String clientId,
        ConversionService conversionService
    ) {
        super(loadBalancer, httpVersion, configuration, contextPath, mediaTypeCodecRegistry, orElseGet, clientId, conversionService);
    }

    public JavanetHttpClient(URI uri) {
        this(
            uri == null ? null : LoadBalancer.fixed(uri),
            null,
            new DefaultHttpClientConfiguration(),
            null,
            createDefaultMediaTypeRegistry(),
            new DefaultRequestBinderRegistry(ConversionService.SHARED),
            null,
            ConversionService.SHARED
        );
    }

    public JavanetHttpClient(
        URI uri,
        HttpClientConfiguration configuration,
        MediaTypeCodecRegistry mediaTypeCodecRegistry
    ) {
        this(
            uri == null ? null : LoadBalancer.fixed(uri),
            null,
            configuration,
            null,
            mediaTypeCodecRegistry,
            new DefaultRequestBinderRegistry(ConversionService.SHARED),
            null,
            ConversionService.SHARED
        );
    }

    private static MediaTypeCodecRegistry createDefaultMediaTypeRegistry() {
        JsonMapper mapper = JsonMapper.createDefault();
        ApplicationConfiguration configuration = new ApplicationConfiguration();
        return MediaTypeCodecRegistry.of(
            new JsonMediaTypeCodec(mapper, configuration, null),
            new JsonStreamMediaTypeCodec(mapper, configuration, null)
        );
    }

    @Override
    public BlockingHttpClient toBlocking() {
        return new JavanetBlockingHttpClient(loadBalancer, httpVersion, configuration, contextPath, mediaTypeCodecRegistry, requestBinderRegistry, clientId, conversionService);
    }

    @Override
    public <I, O, E> Publisher<HttpResponse<O>> exchange(@NonNull HttpRequest<I> request, @NonNull Argument<O> bodyType, @NonNull Argument<E> errorType) {
        URI base = (loadBalancer instanceof FixedLoadBalancer fixedLoadBalancer) ? fixedLoadBalancer.getUri() : null;
        if (base == null) {
            throw new UnsupportedOperationException("Load balancer " + loadBalancer + " not supported");
        }
        java.net.http.HttpRequest httpRequest = HttpRequestFactory.builder(
            UriBuilder.of(base).path(contextPath).path(request.getPath()).build(),
            request,
            conversionService
        ).build();

        CompletableFuture<java.net.http.HttpResponse<byte[]>> completableHttpResponse = java.net.http.HttpClient.newHttpClient().sendAsync(httpRequest, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
        CompletableFuture<HttpResponse<O>> response = completableHttpResponse.thenApply(netResponse -> getConvertedResponse(netResponse, bodyType));
        return Publishers.fromCompletableFuture(response);
    }

    @Override
    public boolean isRunning() {
        return false;
    }
}
