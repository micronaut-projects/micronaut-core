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
import org.jspecify.annotations.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.client.AbstractHttpClient;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.filter.ClientFilterResolutionContext;
import io.micronaut.http.client.jdk.cookie.CookieDecoder;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.filter.HttpClientFilterResolver;
import io.micronaut.http.filter.HttpFilterResolver;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Objects;

/**
 * {@link io.micronaut.http.client.HttpClient} implementation for {@literal java.net.http.*} HTTP Client.
 * @author Sergio del Amo
 * @since 4.0.0
 */
@Internal
@Experimental
public class JdkBlockingHttpClient extends AbstractJdkHttpClient implements BlockingHttpClient {
    /**
     * The pipeline of this client once its codecs were replaced, see
     * {@link #setMediaTypeCodecRegistry}, else {@code null} for the one of its parent.
     */
    @Nullable
    private volatile DefaultJdkHttpClient ownPipeline;

    JdkBlockingHttpClient(AbstractJdkHttpClient prototype) {
        super(prototype);
    }

    // too many parameters; the client keeps the transport of the pipeline, which is never closed, see close()
    @SuppressWarnings({"java:S107", "checkstyle:parameternumber", "java:S2095"})
    public JdkBlockingHttpClient(
        @Nullable
        LoadBalancer loadBalancer,
        @Nullable
        HttpVersionSelection httpVersion,
        HttpClientConfiguration configuration,
        @Nullable
        String contextPath,
        @Nullable HttpClientFilterResolver<ClientFilterResolutionContext> filterResolver,
        @Nullable List<HttpFilterResolver.FilterEntry> clientFilterEntries,
        @Nullable
        MediaTypeCodecRegistry mediaTypeCodecRegistry,
        @Nullable
        MessageBodyHandlerRegistry messageBodyHandlerRegistry,
        RequestBinderRegistry requestBinderRegistry,
        @Nullable
        String clientId,
        ConversionService conversionService,
        JdkClientSslBuilder sslBuilder,
        CookieDecoder cookieDecoder
    ) {
        this(new DefaultJdkHttpClient(
            loadBalancer,
            httpVersion,
            configuration,
            contextPath,
            filterResolver,
            clientFilterEntries,
            mediaTypeCodecRegistry,
            messageBodyHandlerRegistry == null ? JdkHttpClientFactory.createDefaultMessageBodyHandlerRegistry() : messageBodyHandlerRegistry,
            requestBinderRegistry,
            clientId,
            conversionService,
            sslBuilder,
            cookieDecoder
        ).transport());
    }

    @Override
    public <I, O, E> io.micronaut.http.HttpResponse<O> exchange(io.micronaut.http.HttpRequest<I> request,
                                                                @Nullable Argument<O> bodyType,
                                                                @Nullable Argument<E> errorType) {
        if (Schedulers.isInNonBlockingThread()) {
            // same check (and message) as reactor's blockFirst(), which this client used before
            throw new IllegalStateException("block()/blockFirst()/blockLast() are blocking, which is not supported in thread " + Thread.currentThread().getName());
        }
        // the exchange of the client this client was made from
        Argument<?> error = errorType == null ? HttpClient.DEFAULT_ERROR_TYPE : errorType;
        DefaultJdkHttpClient own = ownPipeline;
        DefaultJdkHttpClient pipeline = own == null ? pipelineClient() : own;
        return Objects.requireNonNull(
            AbstractHttpClient.awaitFlow(pipeline.exchangeFlow(request, bodyType, error)),
            "The blocking HTTP client returned no response"
        );
    }

    /**
     * Replaces the codecs of this client only, as before the client shared the pipeline of the
     * client it was made from.
     *
     * @param mediaTypeCodecRegistry The {@link MediaTypeCodecRegistry}
     */
    @Override
    public void setMediaTypeCodecRegistry(MediaTypeCodecRegistry mediaTypeCodecRegistry) {
        super.setMediaTypeCodecRegistry(mediaTypeCodecRegistry);
        ownPipeline = new DefaultJdkHttpClient(pipelineClient(), this);
    }

    /**
     * Replaces the body handlers of this client only, as before the client shared the pipeline
     * of the client it was made from.
     *
     * @param messageBodyHandlerRegistry The {@link MessageBodyHandlerRegistry}
     */
    @Override
    public void setMessageBodyHandlerRegistry(MessageBodyHandlerRegistry messageBodyHandlerRegistry) {
        super.setMessageBodyHandlerRegistry(messageBodyHandlerRegistry);
        ownPipeline = new DefaultJdkHttpClient(pipelineClient(), this);
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
