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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.bind.DefaultRequestBinderRegistry;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.client.AbstractHttpClient;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.filter.ClientFilterResolutionContext;
import io.micronaut.http.client.jdk.cookie.CompositeCookieDecoder;
import io.micronaut.http.client.jdk.cookie.CookieDecoder;
import io.micronaut.http.client.jdk.cookie.DefaultCookieDecoder;
import io.micronaut.http.client.loadbalance.LoadBalancerSelection;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.filter.HttpClientFilterResolver;
import io.micronaut.http.filter.HttpFilterResolver;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.codec.JsonMediaTypeCodec;
import io.micronaut.json.codec.JsonStreamMediaTypeCodec;
import io.micronaut.runtime.ApplicationConfiguration;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * {@link HttpClient} implementation for {@literal java.net.http.*} HTTP Client. The filters, the
 * redirects, the load balancing, the decoding of the responses, the streams and the
 * asynchronous views are those of {@link AbstractHttpClient}; the JDK client sends one request.
 *
 * @author Sergio del Amo
 * @since 4.0.0
 */
@Internal
@Experimental
public class DefaultJdkHttpClient extends AbstractHttpClient<JdkByteBodyResponse> implements JdkHttpClient {

    /**
     * The JDK client and its state: the cookies, the SSL context, and the client of raw
     * exchanges, shared with the blocking and the raw clients made from this client.
     */
    private final AbstractJdkHttpClient transport;
    /**
     * The JDK client of the transport, shared by the blocking clients.
     */
    private final java.net.http.HttpClient client;

    @SuppressWarnings({"java:S107", "checkstyle:parameternumber"}) // too many parameters
    public DefaultJdkHttpClient(
        @Nullable LoadBalancer loadBalancer,
        @Nullable HttpVersionSelection httpVersion,
        HttpClientConfiguration configuration,
        @Nullable String contextPath,
        @Nullable HttpClientFilterResolver<ClientFilterResolutionContext> filterResolver,
        @Nullable List<HttpFilterResolver.FilterEntry> clientFilterEntries,
        @Nullable
        MediaTypeCodecRegistry mediaTypeCodecRegistry,
        MessageBodyHandlerRegistry messageBodyHandlerRegistry,
        RequestBinderRegistry requestBinderRegistry,
        @Nullable
        String clientId,
        ConversionService conversionService,
        JdkClientSslBuilder sslBuilder,
        CookieDecoder cookieDecoder
    ) {
        super(
            configuration,
            LoggerFactory.getLogger(DefaultJdkHttpClient.class),
            contextPath,
            loadBalancer,
            mediaTypeCodecRegistry,
            messageBodyHandlerRegistry,
            filterResolver,
            clientFilterEntries,
            conversionService,
            clientId
        );
        this.transport = new Transport(
            log,
            loadBalancer,
            httpVersion,
            configuration,
            contextPath,
            filterResolver,
            this.clientFilterEntries,
            mediaTypeCodecRegistry,
            messageBodyHandlerRegistry,
            requestBinderRegistry,
            clientId,
            conversionService,
            sslBuilder,
            cookieDecoder
        );
        this.transport.http = this;
        this.client = transport.client;
    }

    public DefaultJdkHttpClient(@Nullable URI uri, ConversionService conversionService) {
        this(
            uri == null ? null : LoadBalancer.fixed(uri),
            null,
            new DefaultHttpClientConfiguration(),
            null,
            null,
            null,
            createDefaultMediaTypeRegistry(),
            JdkHttpClientFactory.createDefaultMessageBodyHandlerRegistry(),
            new DefaultRequestBinderRegistry(conversionService),
            null,
            conversionService,
            new JdkClientSslBuilder(new ResourceResolver()),
            new CompositeCookieDecoder(List.of(new DefaultCookieDecoder()))
        );
    }

    public DefaultJdkHttpClient(
        @Nullable URI uri,
        HttpClientConfiguration configuration,
        @Nullable
        MediaTypeCodecRegistry mediaTypeCodecRegistry,
        MessageBodyHandlerRegistry messageBodyHandlerRegistry,
        ConversionService conversionService
    ) {
        this(
            uri == null ? null : LoadBalancer.fixed(uri),
            null,
            configuration,
            null,
            null,
            null,
            mediaTypeCodecRegistry,
            messageBodyHandlerRegistry,
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

    /**
     * @return The JDK client and its state
     */
    AbstractJdkHttpClient transport() {
        return transport;
    }

    @Override
    public BlockingHttpClient toBlocking() {
        return new JdkBlockingHttpClient(transport);
    }

    @Override
    public boolean isRunning() {
        return false;
    }

    /**
     * @return The {@link MessageBodyHandlerRegistry}
     */
    public MessageBodyHandlerRegistry getMessageBodyHandlerRegistry() {
        return getHandlerRegistry();
    }

    /**
     * @param messageBodyHandlerRegistry The {@link MessageBodyHandlerRegistry}
     */
    public void setMessageBodyHandlerRegistry(MessageBodyHandlerRegistry messageBodyHandlerRegistry) {
        setHandlerRegistry(messageBodyHandlerRegistry);
    }

    @Override
    public void setHandlerRegistry(MessageBodyHandlerRegistry handlerRegistry) {
        super.setHandlerRegistry(handlerRegistry);
        transport.setMessageBodyHandlerRegistry(handlerRegistry);
    }

    @Override
    public void setMediaTypeCodecRegistry(@Nullable MediaTypeCodecRegistry mediaTypeCodecRegistry) {
        super.setMediaTypeCodecRegistry(mediaTypeCodecRegistry);
        if (mediaTypeCodecRegistry != null) {
            transport.setMediaTypeCodecRegistry(mediaTypeCodecRegistry);
        }
    }

    @Override
    protected ExecutionFlow<JdkByteBodyResponse> send(PropagatedContext propagatedContext,
                                                      AtomicReference<ScheduledExecutorService> preferredScheduler,
                                                      @Nullable Thread blockedThread,
                                                      MutableHttpRequest<?> request,
                                                      @Nullable LoadBalancerSelection selection) {
        return transport.sendRequest(request, selection);
    }

    @Override
    protected <O, E> ExecutionFlow<? extends HttpResponse<O>> readFullResponse(JdkByteBodyResponse response,
                                                                              @Nullable Argument<O> bodyType,
                                                                              Argument<E> errorType,
                                                                              Function<Throwable, Throwable> readFailure) {
        byte[] bytes = response.bytes();
        if (bytes == null) {
            return super.readFullResponse(response, bodyType, errorType, readFailure);
        }
        return fullResponse(bodyType, errorType, response, bytes);
    }

    @Override
    protected <O, E> ExecutionFlow<? extends HttpResponse<O>> fullResponse(@Nullable Argument<O> bodyType,
                                                                           Argument<E> errorType,
                                                                           JdkByteBodyResponse response,
                                                                           CloseableAvailableByteBody body) {
        byte[] bytes;
        try (body) {
            bytes = body.toByteArray();
        }
        return fullResponse(bodyType, errorType, response, bytes);
    }

    private <O, E> ExecutionFlow<? extends HttpResponse<O>> fullResponse(@Nullable Argument<O> bodyType,
                                                                         Argument<E> errorType,
                                                                         JdkByteBodyResponse response,
                                                                         byte[] bytes) {
        boolean convert = convertsWithBodyType(response.code(), bodyType, errorType);
        HttpResponseAdapter<O> full = new HttpResponseAdapter<>(
            new BufferedJdkResponse(response.jdkResponse(), bytes),
            convert ? bodyType : null,
            conversionService,
            mediaTypeCodecRegistry,
            handlerRegistry
        );
        if (convert) {
            return ExecutionFlow.just(full);
        }
        return ExecutionFlow.error(errorStatusException(errorType, full));
    }

    @Override
    protected ScheduledExecutorService scheduler() {
        return Timeouts.SCHEDULER;
    }

    @Override
    protected ByteBufferFactory<?, ?> byteBufferFactory() {
        return ByteArrayBufferFactory.INSTANCE;
    }

    @Override
    protected boolean isSameOrigin(URI first, URI second) {
        return AbstractJdkHttpClient.sameServer(first, second);
    }

    @Override
    protected @Nullable HttpClientException mapReadFailure(Throwable cause) {
        if (cause instanceof IOException io) {
            // a failure of the body: the end of the body reported the outcome
            return decorate(transport.sendError(null, null, io, true));
        }
        return null;
    }

    @Override
    protected void copyRedirectAttributes(MutableHttpRequest<?> request, MutableHttpRequest<?> redirect) {
        super.copyRedirectAttributes(request, redirect);
        request.getAttribute(AbstractJdkHttpClient.RAW_ATTRIBUTE).ifPresent(raw -> redirect.setAttribute(AbstractJdkHttpClient.RAW_ATTRIBUTE, raw));
    }

    /**
     * The exchange of a raw request: the response is the one of the JDK client, whose body bytes
     * are streamed.
     *
     * @param request       The raw request
     * @param blockedThread The thread that blocks on the response, if any
     * @return The flow of the response
     */
    ExecutionFlow<HttpResponse<?>> rawExchangeFlow(MutableHttpRequest<?> request, @Nullable Thread blockedThread) {
        setupConversionService(request);
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        request.setAttribute(AbstractJdkHttpClient.RAW_ATTRIBUTE, Boolean.TRUE);
        return resolveRequestURI(request).flatMap(target -> sendRequestWithRedirects(
            propagatedContext,
            blockedThread,
            request.uri(target.uri()),
            target.selection(),
            (req, resp) -> ExecutionFlow.just(resp)
        ));
    }

    /**
     * The JDK client and its state, for this client.
     */
    private static final class Transport extends AbstractJdkHttpClient {
        @SuppressWarnings({"java:S107", "checkstyle:parameternumber"}) // too many parameters
        Transport(org.slf4j.Logger log,
                  @Nullable LoadBalancer loadBalancer,
                  @Nullable HttpVersionSelection httpVersion,
                  HttpClientConfiguration configuration,
                  @Nullable String contextPath,
                  @Nullable HttpClientFilterResolver<ClientFilterResolutionContext> filterResolver,
                  @Nullable List<HttpFilterResolver.FilterEntry> clientFilterEntries,
                  @Nullable MediaTypeCodecRegistry mediaTypeCodecRegistry,
                  @Nullable MessageBodyHandlerRegistry messageBodyHandlerRegistry,
                  RequestBinderRegistry requestBinderRegistry,
                  @Nullable String clientId,
                  ConversionService conversionService,
                  JdkClientSslBuilder sslBuilder,
                  CookieDecoder cookieDecoder) {
            super(log, loadBalancer, httpVersion, configuration, contextPath, filterResolver, clientFilterEntries, mediaTypeCodecRegistry,
                messageBodyHandlerRegistry, requestBinderRegistry, clientId, conversionService, sslBuilder, cookieDecoder);
        }
    }

    /**
     * The scheduler of the request timeouts of the JDK clients.
     */
    private static final class Timeouts {
        static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "jdk-http-client-timeouts");
            thread.setDaemon(true);
            return thread;
        });
    }
}
