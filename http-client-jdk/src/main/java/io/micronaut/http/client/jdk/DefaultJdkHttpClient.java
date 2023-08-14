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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.bind.DefaultRequestBinderRegistry;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.exceptions.HttpClientExceptionUtils;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.jdk.cookie.CompositeCookieDecoder;
import io.micronaut.http.client.jdk.cookie.CookieDecoder;
import io.micronaut.http.client.jdk.cookie.DefaultCookieDecoder;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.codec.JsonMediaTypeCodec;
import io.micronaut.json.codec.JsonStreamMediaTypeCodec;
import io.micronaut.runtime.ApplicationConfiguration;
import org.reactivestreams.Publisher;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;

/**
 * {@link HttpClient} implementation for {@literal java.net.http.*} HTTP Client.
 * @author Sergio del Amo
 * @since 4.0.0
 */
@Internal
@Experimental
public class DefaultJdkHttpClient extends AbstractJdkHttpClient implements JdkHttpClient {

    public DefaultJdkHttpClient(
        @Nullable LoadBalancer loadBalancer,
        HttpVersionSelection httpVersion,
        @NonNull HttpClientConfiguration configuration,
        @Nullable String contextPath,
        MediaTypeCodecRegistry mediaTypeCodecRegistry,
        RequestBinderRegistry requestBinderRegistry,
        String clientId,
        ConversionService conversionService,
        JdkClientSslBuilder sslBuilder,
        CookieDecoder cookieDecoder
    ) {
        super(
            configuration.getLoggerName().map(LoggerFactory::getLogger).orElseGet(() -> LoggerFactory.getLogger(DefaultJdkHttpClient.class)),
            loadBalancer,
            httpVersion,
            configuration,
            contextPath,
            mediaTypeCodecRegistry,
            requestBinderRegistry,
            clientId,
            conversionService,
            sslBuilder,
            cookieDecoder
        );
    }

    public DefaultJdkHttpClient(URI uri, ConversionService conversionService) {
        this(
            uri == null ? null : LoadBalancer.fixed(uri),
            null,
            new DefaultHttpClientConfiguration(),
            null,
            createDefaultMediaTypeRegistry(),
            new DefaultRequestBinderRegistry(conversionService),
            null,
            conversionService,
            new JdkClientSslBuilder(new ResourceResolver()),
            new CompositeCookieDecoder(List.of(new DefaultCookieDecoder()))
        );
    }

    public DefaultJdkHttpClient(
        URI uri,
        HttpClientConfiguration configuration,
        MediaTypeCodecRegistry mediaTypeCodecRegistry,
        ConversionService conversionService
    ) {
        this(
            uri == null ? null : LoadBalancer.fixed(uri),
            null,
            configuration,
            null,
            mediaTypeCodecRegistry,
            new DefaultRequestBinderRegistry(conversionService),
            null,
            conversionService,
            new JdkClientSslBuilder(new ResourceResolver()),
            new CompositeCookieDecoder(List.of(new DefaultCookieDecoder()))
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
        return new JdkBlockingHttpClient(loadBalancer, httpVersion, configuration, contextPath, mediaTypeCodecRegistry, requestBinderRegistry, clientId, conversionService, sslBuilder, cookieDecoder);
    }

    @Override
    public <I, O, E> Publisher<HttpResponse<O>> exchange(@NonNull HttpRequest<I> request, @NonNull Argument<O> bodyType, @NonNull Argument<E> errorType) {
        return mapToHttpRequest(request, bodyType)
            .map(httpRequest -> {
                if (log.isDebugEnabled()) {
                    log.debug("Client {} Sending HTTP Request: {}", clientId, httpRequest);
                }
                if (log.isTraceEnabled()) {
                    httpRequest.headers().map().forEach((k, v) -> log.trace("Client {} Sending HTTP Request Header: {}={}", clientId, k, v));
                }
                return client.sendAsync(httpRequest, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            })
            .flatMap(Mono::fromCompletionStage)
            .map(netResponse -> {
                if (log.isDebugEnabled()) {
                    log.error("Client {} Received HTTP Response: {} {}", clientId, netResponse.statusCode(), netResponse.uri());
                }
                boolean errorStatus = netResponse.statusCode() >= 400;
                if (errorStatus && configuration.isExceptionOnErrorStatus()) {
                    throw HttpClientExceptionUtils.populateServiceId(new HttpClientResponseException(HttpStatus.valueOf(netResponse.statusCode()).getReason(),
                        response(netResponse, bodyType)), clientId, configuration);
                }
                return response(netResponse, bodyType);
            });
    }

    @Override
    public boolean isRunning() {
        return false;
    }
}
