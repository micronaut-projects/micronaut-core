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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.loadbalance.FixedLoadBalancer;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.uri.UriBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * {@link io.micronaut.http.client.HttpClient} implementation for {@literal java.net.http.*} HTTP Client.
 * @author Sergio del Amo
 * @since 4.0.0
 */
@Internal
public final class JavanetBlockingHttpClient extends AbstractJavanetHttpClient implements BlockingHttpClient {

    public JavanetBlockingHttpClient(
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

    @Override
    public <I, O, E> io.micronaut.http.HttpResponse<O> exchange(io.micronaut.http.HttpRequest<I> request,
                                              Argument<O> bodyType,
                                              Argument<E> errorType) {
        URI base = (loadBalancer instanceof FixedLoadBalancer fixedLoadBalancer) ? fixedLoadBalancer.getUri() : null;
        if (base == null) {
            throw new UnsupportedOperationException("Load balancer " + loadBalancer + " not supported");
        }
        HttpRequest httpRequest = HttpRequestFactory.builder(
            UriBuilder.of(base).path(contextPath).path(request.getPath()).build(),
            request,
            conversionService
        ).build();
        try {
            HttpResponse<byte[]> httpResponse = HttpClient.newHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            return getConvertedResponse(httpResponse, bodyType);
        } catch (IOException | InterruptedException e) {
            throw new HttpClientException("error sending request", e);
        }
    }

    @Override
    public void close() throws IOException {

    }
}
