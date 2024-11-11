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
import io.micronaut.core.type.Argument;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.filter.ClientFilterResolutionContext;
import io.micronaut.http.client.jdk.cookie.CookieDecoder;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.filter.HttpClientFilterResolver;
import io.micronaut.http.filter.HttpFilterResolver;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * {@link io.micronaut.http.client.HttpClient} implementation for {@literal java.net.http.*} HTTP Client.
 * @author Sergio del Amo
 * @since 4.0.0
 */
@Internal
@Experimental
public class JdkBlockingHttpClient extends AbstractJdkHttpClient implements BlockingHttpClient {

    public JdkBlockingHttpClient(
        LoadBalancer loadBalancer,
        HttpVersionSelection httpVersion,
        HttpClientConfiguration configuration,
        String contextPath,
        @Nullable HttpClientFilterResolver<ClientFilterResolutionContext> filterResolver,
        @Nullable List<HttpFilterResolver.FilterEntry> clientFilterEntries,
        MediaTypeCodecRegistry mediaTypeCodecRegistry,
        MessageBodyHandlerRegistry messageBodyHandlerRegistry,
        RequestBinderRegistry requestBinderRegistry,
        String clientId,
        ConversionService conversionService,
        JdkClientSslBuilder sslBuilder,
        CookieDecoder cookieDecoder
    ) {
        super(
            configuration.getLoggerName().map(LoggerFactory::getLogger).orElseGet(() -> LoggerFactory.getLogger(JdkBlockingHttpClient.class)),
            loadBalancer,
            httpVersion,
            configuration,
            contextPath,
            filterResolver,
            clientFilterEntries,
            mediaTypeCodecRegistry,
            messageBodyHandlerRegistry,
            requestBinderRegistry,
            clientId,
            conversionService,
            sslBuilder,
            cookieDecoder
        );
    }

    @Override
    public <I, O, E> io.micronaut.http.HttpResponse<O> exchange(@NonNull io.micronaut.http.HttpRequest<I> request,
                                                                @Nullable Argument<O> bodyType,
                                                                @Nullable Argument<E> errorType) {
        return exchangeImpl(request, bodyType).blockFirst();
    }

    @Override
    public boolean isRunning() {
        // We cannot stop or close this client so is always running
        return true;
    }

    @Override
    public BlockingHttpClient start() {
        // Client always running, we do not need to start it
        return this;
    }

    @Override
    public BlockingHttpClient stop() {
        // Nothing to do here, we do not need to stop clients
        return this;
    }

    @Override
    public void close() {
        // Nothing to do here, we do not need to close clients
    }
}
