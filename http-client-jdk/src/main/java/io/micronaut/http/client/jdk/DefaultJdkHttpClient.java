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
import io.micronaut.http.client.AsyncHttpClient;
import io.micronaut.http.client.AsyncStreamingHttpClient;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.http.client.filter.ClientFilterResolutionContext;
import io.micronaut.http.client.jdk.cookie.CompositeCookieDecoder;
import io.micronaut.http.client.jdk.cookie.NettyCookieDecoder;
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
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final AsyncStreamingHttpClient asyncClient;

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
            configuration,
            mediaTypeCodecRegistry,
            messageBodyHandlerRegistry,
            clientId,
            conversionService,
            sslBuilder,
            cookieDecoder
        );
        this.transport.http = this;
        this.asyncClient = super.toAsyncStreaming();
    }

    /**
     * A client with the pipeline of the given client, which sends its requests with the given
     * transport, e.g. a blocking client whose codecs were replaced.
     *
     * @param parent    The client whose pipeline is copied
     * @param transport The transport, which encodes the requests with its codecs
     */
    DefaultJdkHttpClient(DefaultJdkHttpClient parent, AbstractJdkHttpClient transport) {
        super(
            parent.configuration,
            parent.log,
            parent.contextPath,
            parent.loadBalancer,
            transport.mediaTypeCodecRegistry,
            transport.messageBodyHandlerRegistry == null ? parent.handlerRegistry : transport.messageBodyHandlerRegistry,
            parent.filterResolver,
            parent.clientFilterEntries,
            parent.conversionService,
            parent.informationalServiceId
        );
        this.transport = transport;
        this.asyncClient = super.toAsyncStreaming();
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
            defaultCookieDecoder(conversionService)
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
            defaultCookieDecoder(conversionService)
        );
    }

    private static CompositeCookieDecoder defaultCookieDecoder(ConversionService conversionService) {
        if (nettyRequestType() != null) {
            return new CompositeCookieDecoder(List.of(new NettyCookieDecoder(conversionService), new DefaultCookieDecoder()));
        }
        return new CompositeCookieDecoder(List.of(new DefaultCookieDecoder()));
    }

    private static @Nullable Class<?> nettyRequestType() {
        try {
            return io.micronaut.http.client.netty.NettyClientHttpRequest.class;
        } catch (NoClassDefFoundError e) {
            return null;
        }
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

    @Override
    public AsyncHttpClient toAsync() {
        return asyncClient;
    }

    @Override
    public AsyncStreamingHttpClient toAsyncStreaming() {
        return asyncClient;
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
        return transport.sendRequest(request, selection, null);
    }

    @Override
    protected ExecutionFlow<JdkByteBodyResponse> sendBuffered(PropagatedContext propagatedContext,
                                                              AtomicReference<ScheduledExecutorService> preferredScheduler,
                                                              @Nullable Thread blockedThread,
                                                              MutableHttpRequest<?> request,
                                                              @Nullable LoadBalancerSelection selection,
                                                              AtomicBoolean headersReceived) {
        return transport.sendRequest(request, selection, headersReceived);
    }

    /**
     * The read timeout of the JDK client applies to the response headers, see
     * {@link HttpRequestFactory}, and a body that keeps coming is read to its end, as before
     * the JDK client shared the pipeline: no overall timeout is derived from the read timeout,
     * and the request timeout only applies when the configuration opts in, see
     * {@link HttpClientConfiguration.JdkConfiguration#isApplyRequestTimeout()}.
     *
     * @return The request timeout, or {@code null}
     */
    @Override
    protected @Nullable Duration requestTimeout() {
        return configuration.getJdk().isApplyRequestTimeout() ? configuration.getRequestTimeout() : null;
    }

    /**
     * The JDK client follows the redirects itself, as it always did, unless the configuration
     * opts in to the redirects of the pipeline, see
     * {@link HttpClientConfiguration.JdkConfiguration#isUseMicronautRedirects()}.
     *
     * @param request The request
     * @return Whether its redirects are followed by the pipeline
     */
    @Override
    protected boolean followsRedirects(MutableHttpRequest<?> request) {
        return transport.micronautRedirects && super.followsRedirects(request);
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
        // the JDK client returns the response of an error status, decoded into the body type, when
        // it does not fail on an error status
        boolean error = response.code() >= 400 && configuration.isExceptionOnErrorStatus();
        boolean decodeErrorType = configuration.getJdk().isDecodeErrorType();
        HttpResponseAdapter<O> full = new HttpResponseAdapter<>(
            new BufferedJdkResponse(response.jdkResponse(), bytes),
            error && decodeErrorType ? null : bodyType,
            conversionService,
            mediaTypeCodecRegistry,
            handlerRegistry
        );
        if (!error) {
            return ExecutionFlow.just(full);
        }
        if (decodeErrorType) {
            return ExecutionFlow.error(errorStatusException(errorType, full));
        }
        // as the JDK client always did: the response decodes its body into the body type, and
        // the error type is not used
        return ExecutionFlow.error(decorate(new HttpClientResponseException(full.reason(), full)));
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
        return sameServer(first, second);
    }

    @Override
    protected @Nullable HttpClientException mapReadFailure(Throwable cause) {
        if (cause instanceof IOException io) {
            // a failure of the body: the end of the body reported the outcome
            HttpClientException failure = transport.sendError(null, null, io, true);
            // a shared timeout cannot take the service id of this client
            return decorate(failure instanceof ReadTimeoutException timeout ? new ReadTimeoutException(timeout.isHeadersReceived()) : failure);
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
        @SuppressWarnings("java:S107") // too many parameters
        Transport(org.slf4j.Logger log,
                  HttpClientConfiguration configuration,
                  @Nullable MediaTypeCodecRegistry mediaTypeCodecRegistry,
                  @Nullable MessageBodyHandlerRegistry messageBodyHandlerRegistry,
                  @Nullable String clientId,
                  ConversionService conversionService,
                  JdkClientSslBuilder sslBuilder,
                  CookieDecoder cookieDecoder) {
            super(log, configuration, mediaTypeCodecRegistry, messageBodyHandlerRegistry, clientId, conversionService, sslBuilder, cookieDecoder);
        }
    }

    /**
     * The scheduler of the request timeouts of the JDK clients.
     */
    private static final class Timeouts {
        static final ScheduledExecutorService SCHEDULER = scheduler();

        private static ScheduledExecutorService scheduler() {
            ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
                Thread thread = new Thread(runnable, "jdk-http-client-timeouts");
                thread.setDaemon(true);
                return thread;
            });
            // the timeout of an exchange that completed in time is removed at once, instead of
            // staying queued until it would have elapsed
            executor.setRemoveOnCancelPolicy(true);
            return executor;
        }
    }
}
