/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.client;

import io.micronaut.http.client.internal.ElementsResponse;
import io.micronaut.http.client.internal.ElementsStages;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.propagation.ReactivePropagation;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.ConversionServiceAware;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.execution.ImperativeExecutionFlow;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpRequestWrapper;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.body.ChunkedMessageBodyReader;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.InternalByteBody;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.PieceReader;
import io.micronaut.http.body.stream.AvailableByteArrayBody;
import io.micronaut.http.body.stream.BodyElementsPublisher;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.ByteBodyElements;
import io.micronaut.http.body.stream.PieceReaders;
import io.micronaut.http.client.exceptions.ContentLengthExceededException;
import io.micronaut.http.client.exceptions.HttpClientErrorDecoder;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientExceptionUtils;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.exceptions.NoHostException;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.http.client.exceptions.UnprocessedRequestException;
import io.micronaut.http.client.filter.ClientFilterResolutionContext;
import io.micronaut.http.client.filter.DefaultHttpClientFilterResolver;
import io.micronaut.http.client.loadbalance.FixedLoadBalancer;
import io.micronaut.http.client.loadbalance.LoadBalancerKey;
import io.micronaut.http.client.loadbalance.LoadBalancerSelection;
import io.micronaut.http.client.sse.EventStreams;
import io.micronaut.http.client.sse.SseClient;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.context.ContextPathUtils;
import io.micronaut.http.exceptions.BufferLengthExceededException;
import io.micronaut.http.filter.FilterRunner;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.http.filter.HttpClientFilterResolver;
import io.micronaut.http.filter.HttpFilterResolver;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import io.micronaut.http.sse.Event;
import io.micronaut.http.util.HttpHeadersUtil;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The part of an HTTP client that does not depend on its transport: the state of the client, the
 * resolution of the request URI and the load balancing, the filters and the redirects, the
 * decoding of streamed elements and events, and the reactive, blocking and asynchronous views
 * of the exchanges. A transport sends one request and produces the raw response, see
 * {@link #send}, and builds a response whose body is fully read, see {@link #fullResponse}.
 *
 * @param <R> The type of a raw response of the transport, whose body bytes are not read yet
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public abstract class AbstractHttpClient<R extends ByteBodyHttpResponse<?>> implements HttpClient, StreamingHttpClient, SseClient {

    /**
     * Request attribute that disables decompression for one exchange, see
     * {@link RawRequestOptions#isDecompress()}.
     */
    public static final String NO_DECOMPRESSION = "micronaut.http.client.raw.no-decompression";

    /**
     * Request attribute with the {@link RawRequestOptions#getReadIdleTimeout() read idle timeout}
     * of an exchange.
     */
    public static final String READ_IDLE_TIMEOUT = "micronaut.http.client.raw.read-idle-timeout";

    /**
     * Request attribute that disables following redirects for one exchange, see
     * {@link RawRequestOptions#isFollowRedirects()}.
     */
    public static final String NO_FOLLOW_REDIRECTS = "micronaut.http.client.raw.no-follow-redirects";

    private static final String REDIRECT_COUNT = "micronaut.http.client.redirect-count";

    protected final HttpClientConfiguration configuration;
    /**
     * Size limits for response bodies, derived from {@link #configuration} once.
     */
    protected final BodySizeLimits sizeLimits;
    @Nullable
    protected final String contextPath;
    protected final Charset defaultCharset;
    protected final Logger log;
    @Nullable
    protected final LoadBalancer loadBalancer;
    @Nullable
    protected final HttpClientFilterResolver<ClientFilterResolutionContext> filterResolver;
    protected final List<HttpFilterResolver.FilterEntry> clientFilterEntries;
    /**
     * {@code true} when the default resolver is used and no filter entry applies to this client,
     * so per-request filter resolution and sorting can be skipped.
     */
    protected final boolean noFilters;
    @Nullable
    protected final String informationalServiceId;
    protected final ConversionService conversionService;
    @Nullable
    protected MediaTypeCodecRegistry mediaTypeCodecRegistry;
    protected MessageBodyHandlerRegistry handlerRegistry;

    private final Set<String> redirectSameOriginPreserveBodyHeaders;
    private final Set<String> redirectCrossOriginPreserveBodyHeaders;
    private final Set<String> redirectSameOriginNonPreserveBodyHeaders;
    private final Set<String> redirectCrossOriginNonPreserveBodyHeaders;

    /**
     * @param configuration          The configuration of the client
     * @param defaultLog             The logger, unless the configuration names one
     * @param contextPath            The context path prepended to relative request URIs, or {@code null}
     * @param loadBalancer           The load balancer of relative request URIs, or {@code null}
     * @param mediaTypeCodecRegistry The codecs, or {@code null}
     * @param handlerRegistry        The body readers and writers
     * @param filterResolver         The resolver of the filters, or {@code null} for no filters
     * @param clientFilterEntries    The filter entries of this client, or {@code null} to resolve them
     * @param conversionService      The conversion service
     * @param informationalServiceId The service id of the client for its exceptions, or {@code null}
     */
    protected AbstractHttpClient(HttpClientConfiguration configuration,
                                 Logger defaultLog,
                                 @Nullable String contextPath,
                                 @Nullable LoadBalancer loadBalancer,
                                 @Nullable MediaTypeCodecRegistry mediaTypeCodecRegistry,
                                 MessageBodyHandlerRegistry handlerRegistry,
                                 @Nullable HttpClientFilterResolver<ClientFilterResolutionContext> filterResolver,
                                 @Nullable List<HttpFilterResolver.FilterEntry> clientFilterEntries,
                                 ConversionService conversionService,
                                 @Nullable String informationalServiceId) {
        this.configuration = configuration;
        this.sizeLimits = new BodySizeLimits(Long.MAX_VALUE, configuration.getMaxContentLength());
        this.defaultCharset = configuration.getDefaultCharset();
        if (StringUtils.isNotEmpty(contextPath)) {
            this.contextPath = contextPath.charAt(0) != '/' ? '/' + contextPath : contextPath;
        } else {
            this.contextPath = null;
        }
        this.loadBalancer = loadBalancer;
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        this.handlerRegistry = handlerRegistry;
        this.log = configuration.getLoggerName().map(LoggerFactory::getLogger).orElse(defaultLog);
        this.filterResolver = filterResolver;
        if (clientFilterEntries != null) {
            this.clientFilterEntries = clientFilterEntries;
        } else if (filterResolver != null) {
            this.clientFilterEntries = filterResolver.resolveFilterEntries(new ClientFilterResolutionContext(null, AnnotationMetadata.EMPTY_METADATA));
        } else {
            this.clientFilterEntries = List.of();
        }
        this.noFilters = filterResolver == null || this.clientFilterEntries.isEmpty() && filterResolver.getClass() == DefaultHttpClientFilterResolver.class;
        this.conversionService = conversionService;
        this.informationalServiceId = informationalServiceId;
        this.redirectSameOriginPreserveBodyHeaders = redirectFilteredHeaders(false, true);
        this.redirectCrossOriginPreserveBodyHeaders = redirectFilteredHeaders(true, true);
        this.redirectSameOriginNonPreserveBodyHeaders = redirectFilteredHeaders(false, false);
        this.redirectCrossOriginNonPreserveBodyHeaders = redirectFilteredHeaders(true, false);
    }

    // ---- the transport

    /**
     * Send one request, without filters and without following redirects.
     *
     * @param propagatedContext  The context propagated from the original client call
     * @param preferredScheduler A reference holding the preferred scheduler for timeouts, which
     *                           the transport may replace with the one of its connection
     * @param blockedThread      The thread that blocks on the response, if any
     * @param request            The request to send, with a resolved absolute URI
     * @param selection          The selection of the load balancer for the request, or {@code null}
     * @return The flow of the raw response, whose body bytes are not read yet
     */
    protected abstract ExecutionFlow<R> send(PropagatedContext propagatedContext,
                                             AtomicReference<ScheduledExecutorService> preferredScheduler,
                                             @Nullable Thread blockedThread,
                                             MutableHttpRequest<?> request,
                                             @Nullable LoadBalancerSelection selection);

    /**
     * Send one request of an exchange whose response body is read whole, see
     * {@link #readFullResponse}, without filters and without following redirects: a transport may
     * read the body into memory right away. By default, see {@link #send}.
     *
     * @param propagatedContext  The context propagated from the original client call
     * @param preferredScheduler A reference holding the preferred scheduler for timeouts, which
     *                           the transport may replace with the one of its connection
     * @param blockedThread      The thread that blocks on the response, if any
     * @param request            The request to send, with a resolved absolute URI
     * @param selection          The selection of the load balancer for the request, or {@code null}
     * @param headersReceived    Set by a transport that reads the body before the flow completes,
     *                           once the response headers arrived: a timeout of the exchange then
     *                           elapsed while the body was read
     * @return The flow of the raw response
     */
    protected ExecutionFlow<R> sendBuffered(PropagatedContext propagatedContext,
                                            AtomicReference<ScheduledExecutorService> preferredScheduler,
                                            @Nullable Thread blockedThread,
                                            MutableHttpRequest<?> request,
                                            @Nullable LoadBalancerSelection selection,
                                            AtomicBoolean headersReceived) {
        return send(propagatedContext, preferredScheduler, blockedThread, request, selection);
    }

    /**
     * Build the response of an exchange whose body was read, decoded into the body type, or the
     * error decoded into the error type.
     *
     * @param bodyType  The body type, or {@code null}
     * @param errorType The error type
     * @param response  The raw response
     * @param body      The body bytes, which this takes over
     * @param <O>       The body type
     * @param <E>       The error type
     * @return The flow of the response, or of the error of an error status
     */
    protected abstract <O, E> ExecutionFlow<? extends HttpResponse<O>> fullResponse(@Nullable Argument<O> bodyType,
                                                                                    Argument<E> errorType,
                                                                                    R response,
                                                                                    CloseableAvailableByteBody body);

    /**
     * @return The scheduler of the timeouts of the exchanges
     */
    protected abstract ScheduledExecutorService scheduler();

    /**
     * @return The factory of the buffers of the event data of {@link #eventStream(HttpRequest)}
     */
    protected abstract ByteBufferFactory<?, ?> byteBufferFactory();

    /**
     * Whether two request URIs have the same origin, to tell which headers a redirect keeps.
     *
     * @param first  The first URI
     * @param second The second URI
     * @return Whether they have the same origin
     */
    protected abstract boolean isSameOrigin(URI first, URI second);

    /**
     * Map a failure of the transport while a response is read, e.g. a read timeout of the
     * transport, to a client exception.
     *
     * @param cause The failure
     * @return The client exception, or {@code null} for the default mapping
     */
    protected @Nullable HttpClientException mapReadFailure(Throwable cause) {
        return null;
    }

    // ---- state

    /**
     * @return The configuration used by this client
     */
    public HttpClientConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * @return The client-specific logger name
     */
    public Logger getLog() {
        return log;
    }

    /**
     * @return The {@link MediaTypeCodecRegistry} used by this client
     */
    public @Nullable MediaTypeCodecRegistry getMediaTypeCodecRegistry() {
        return mediaTypeCodecRegistry;
    }

    /**
     * Sets the {@link MediaTypeCodecRegistry} used by this client.
     *
     * @param mediaTypeCodecRegistry The registry to use. Should not be null
     */
    public void setMediaTypeCodecRegistry(@Nullable MediaTypeCodecRegistry mediaTypeCodecRegistry) {
        if (mediaTypeCodecRegistry != null) {
            this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        }
    }

    /**
     * @return The handler registry
     */
    public MessageBodyHandlerRegistry getHandlerRegistry() {
        return handlerRegistry;
    }

    /**
     * @param handlerRegistry The handler registry
     */
    public void setHandlerRegistry(MessageBodyHandlerRegistry handlerRegistry) {
        this.handlerRegistry = handlerRegistry;
    }

    /**
     * @return The size limits of response bodies
     */
    public BodySizeLimits sizeLimits() {
        return sizeLimits;
    }

    /**
     * @return The conversion service
     */
    public ConversionService conversionService() {
        return conversionService;
    }

    // ---- requests

    /**
     * @param request The request
     * @return Whether the request accepts only {@code text/event-stream}
     */
    private static boolean isAcceptEvents(HttpRequest<?> request) {
        String acceptHeader = request.getHeaders().get(HttpHeaders.ACCEPT);
        return acceptHeader != null && acceptHeader.equalsIgnoreCase(MediaType.TEXT_EVENT_STREAM);
    }

    /**
     * @param request The request
     * @return Whether the request accepts {@code text/event-stream}, possibly among other types
     */
    protected static boolean acceptsEvents(HttpRequest<?> request) {
        for (MediaType accepted : request.getHeaders().accept()) {
            if (accepted.matches(MediaType.TEXT_EVENT_STREAM_TYPE)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A form or multipart body that is already encoded is written as is, like any other raw body.
     *
     * @param bodyValue The body value
     * @return Whether the body is already encoded
     */
    protected static boolean isEncodedFormBody(Object bodyValue) {
        return bodyValue instanceof CharSequence || bodyValue instanceof byte[] || bodyValue instanceof ByteBuffer<?>;
    }

    /**
     * @param response The response
     * @return Whether the response has a body
     */
    protected static boolean hasBody(HttpResponse<?> response) {
        if (response.code() >= HttpStatus.CONTINUE.getCode() && response.code() < HttpStatus.OK.getCode()) {
            return false;
        }
        if (response.code() == HttpStatus.NO_CONTENT.getCode() || response.code() == HttpStatus.NOT_MODIFIED.getCode()) {
            return false;
        }
        OptionalLong contentLength = response.getHeaders().contentLength();
        return contentLength.isEmpty() || contentLength.getAsLong() != 0;
    }

    /**
     * @param request The request
     * @param <I>     The request body type
     * @return The request, mutable
     */
    protected <I> MutableHttpRequest<?> toMutableRequest(HttpRequest<I> request) {
        return MutableHttpRequestWrapper.wrapIfNecessary(conversionService, request);
    }

    /**
     * @param httpRequest The request, given the conversion service of this client
     */
    protected void setupConversionService(HttpRequest<?> httpRequest) {
        if (httpRequest instanceof ConversionServiceAware aware) {
            aware.setConversionService(conversionService);
        }
    }

    /**
     * @param exc The exception
     * @param <E> The exception type
     * @return The exception, with the service id of this client
     */
    public <E extends HttpClientException> E decorate(E exc) {
        return HttpClientExceptionUtils.populateServiceId(exc, informationalServiceId, configuration);
    }

    /**
     * Complete a flow exceptionally, unless it completed already.
     *
     * @param flow The flow
     * @param exc  The failure
     */
    public void completeExceptionallySafe(DelayedExecutionFlow<?> flow, Throwable exc) {
        if (!flow.tryCompleteExceptionally(exc)) {
            log.debug("Client exception suppressed because response flow already completed", exc);
        }
    }

    // ---- resolution of the request URI

    /**
     * @param request The request
     * @param <I>     The input type
     * @return A flow with the resolved target
     */
    public <I> ExecutionFlow<ResolvedTarget> resolveRequestURI(HttpRequest<I> request) {
        return resolveRequestURI(request, true);
    }

    /**
     * @param request            The request
     * @param includeContextPath Whether to prepend the client context path
     * @param <I>                The input type
     * @return A flow with the resolved target
     */
    public <I> ExecutionFlow<ResolvedTarget> resolveRequestURI(HttpRequest<I> request, boolean includeContextPath) {
        URI requestURI = request.getUri();
        if (requestURI.getScheme() != null) {
            // if the request URI includes a scheme then it is fully qualified so use the direct server
            return ExecutionFlow.just(new ResolvedTarget(requestURI, null));
        } else {
            return resolveURI(request, includeContextPath);
        }
    }

    /**
     * @param parentRequest The parent request
     * @param request       The redirect location request
     * @param <I>           The input type
     * @return A flow with the resolved target
     */
    public <I> ExecutionFlow<ResolvedTarget> resolveRedirectURI(@Nullable HttpRequest<?> parentRequest, HttpRequest<I> request) {
        URI requestURI = request.getUri();
        if (requestURI.getScheme() != null) {
            // if the request URI includes a scheme then it is fully qualified so use the direct server
            return ExecutionFlow.just(new ResolvedTarget(requestURI, null));
        } else {
            if (parentRequest == null || parentRequest.getUri().getHost() == null) {
                return resolveURI(request, false);
            } else {
                URI redirectedURI = parentRequest.getUri().resolve(requestURI).normalize();
                return ExecutionFlow.just(new ResolvedTarget(redirectedURI, null));
            }
        }
    }

    /**
     * @param request The request object
     * @return The discriminator to use when selecting a server for the purposes of load balancing (defaults to {@link HttpRequest})
     */
    public Object getLoadBalancerDiscriminator(HttpRequest<?> request) {
        return LoadBalancerKey.discriminator(request, configuration);
    }

    private <I> ExecutionFlow<ResolvedTarget> resolveURI(HttpRequest<I> request, boolean includeContextPath) {
        URI requestURI = request.getUri();
        if (loadBalancer == null) {
            return ExecutionFlow.error(decorate(new NoHostException("Request URI specifies no host to connect to")));
        }
        ExecutionFlow<ServiceInstance> selected;
        if (loadBalancer instanceof FixedLoadBalancer fixed) {
            selected = ExecutionFlow.just(fixed.getServiceInstance());
        } else {
            // a synchronous balancer (round-robin) completes right away, so the request proceeds
            // without a Reactor chain
            selected = ReactiveExecutionFlow.fromPublisherEager(loadBalancer.select(getLoadBalancerDiscriminator(request)), PropagatedContext.getOrEmpty());
        }

        LoadBalancer balancer = loadBalancer;
        return selected.map(server -> {
                LoadBalancerSelection selection = new LoadBalancerSelection(balancer, server);
                Optional<String> authInfo = server.getMetadata().get(HttpHeaders.AUTHORIZATION_INFO, String.class);
                if (request instanceof MutableHttpRequest<?> httpRequest && authInfo.isPresent()) {
                    httpRequest.getHeaders().auth(authInfo.get());
                }

                try {
                    return new ResolvedTarget(resolveAgainst(server, requestURI, includeContextPath), selection);
                } catch (RuntimeException e) {
                    selection.release();
                    throw e;
                }
            }
        );
    }

    // ---- filters and redirects

    /**
     * Send a request through the filters, and follow the redirects.
     *
     * @param propagatedContext The context propagated from the original client call
     * @param blockedThread     The thread that blocks on the response, if any
     * @param request           The request to send, with a resolved absolute URI
     * @param selection         The selection of the load balancer for the request, or {@code null}
     * @param readResponse      Reads the response from the raw response, see {@link #sendRequestWithRedirects(PropagatedContext, AtomicReference, Thread, MutableHttpRequest, LoadBalancerSelection, BiFunction)}
     * @return The flow of the response
     */
    protected ExecutionFlow<HttpResponse<?>> sendRequestWithRedirects(
        PropagatedContext propagatedContext,
        @Nullable Thread blockedThread,
        MutableHttpRequest<?> request,
        @Nullable LoadBalancerSelection selection,
        BiFunction<MutableHttpRequest<?>, R, ? extends ExecutionFlow<? extends HttpResponse<?>>> readResponse
    ) {
        return sendRequestWithRedirects(propagatedContext, new AtomicReference<>(), blockedThread, request, selection, readResponse);
    }

    /**
     * This is the high-level request method. It sits above {@link #send} and handles things like
     * filters, redirects and response parsing.
     *
     * @param propagatedContext  The context propagated from the original client call
     * @param preferredScheduler A reference holding the preferred scheduler for timeouts. This is
     *                           replaced by the transport ASAP so that callers can take advantage
     *                           of locality
     * @param blockedThread      The thread that blocks on the response, if any
     * @param request            The request to send. Must have resolved absolute URI (see {@link #resolveRequestURI})
     * @param selection          The selection of the load balancer for the request, or
     *                           {@code null} if the request was not load balanced. It is
     *                           released when the exchange ends without reporting an outcome
     * @param readResponse       Function that reads the response from the raw response. This is
     *                           run exactly once, but if there is a redirect, it potentially runs
     *                           with a different request than the original (which is why it has a
     *                           request parameter)
     * @return A flow containing the response
     */
    protected ExecutionFlow<HttpResponse<?>> sendRequestWithRedirects(
        PropagatedContext propagatedContext,
        AtomicReference<ScheduledExecutorService> preferredScheduler,
        @Nullable Thread blockedThread,
        MutableHttpRequest<?> request,
        @Nullable LoadBalancerSelection selection,
        BiFunction<MutableHttpRequest<?>, R, ? extends ExecutionFlow<? extends HttpResponse<?>>> readResponse
    ) {
        return sendRequestWithRedirects(propagatedContext, preferredScheduler, blockedThread, request, selection, null, readResponse);
    }

    /**
     * @param propagatedContext  The context propagated from the original client call
     * @param preferredScheduler A reference holding the preferred scheduler for timeouts
     * @param blockedThread      The thread that blocks on the response, if any
     * @param request            The request to send
     * @param selection          The selection of the load balancer for the request, or {@code null}
     * @param bufferedHeaders    For an exchange whose response body is read whole, see
     *                           {@link #sendBuffered}, else {@code null}. It is kept out of the
     *                           request, which may be the object of the caller
     * @param readResponse       Reads the response from the raw response
     * @return A flow containing the response
     */
    private ExecutionFlow<HttpResponse<?>> sendRequestWithRedirects(
        PropagatedContext propagatedContext,
        AtomicReference<ScheduledExecutorService> preferredScheduler,
        @Nullable Thread blockedThread,
        MutableHttpRequest<?> request,
        @Nullable LoadBalancerSelection selection,
        @Nullable AtomicBoolean bufferedHeaders,
        BiFunction<MutableHttpRequest<?>, R, ? extends ExecutionFlow<? extends HttpResponse<?>>> readResponse
    ) {
        if (informationalServiceId != null && BasicHttpAttributes.getServiceId(request).isEmpty()) {
            ClientAttributes.setServiceId(request, informationalServiceId);
        }

        List<GenericHttpFilter> filters;
        if (noFilters || filterResolver == null) {
            filters = List.of();
        } else {
            filters = filterResolver.resolveFilters(request, clientFilterEntries);
            FilterRunner.sortReverse(filters);
        }

        URI resolvedUri = request.getUri();
        ExecutionFlow<HttpResponse<?>> flow;
        if (filters.isEmpty()) {
            // what the filter runner does without a filter
            flow = sendFiltered(propagatedContext, preferredScheduler, blockedThread, resolvedUri, request, selection, bufferedHeaders, readResponse);
        } else {
            FilterRunner runner = new FilterRunner(filters) {
                @Override
                protected ExecutionFlow<HttpResponse<?>> provideResponse(HttpRequest<?> request, PropagatedContext propagatedContext) {
                    return sendFiltered(propagatedContext, preferredScheduler, blockedThread, resolvedUri, request, selection, bufferedHeaders, readResponse);
                }
            };
            flow = runner.run(request, propagatedContext);
        }
        if (selection == null) {
            return flow;
        }
        // the selection ends with the exchange, whether it was sent, failed before, was
        // cancelled, or a filter answered without it: unless the response handling took it over
        return releaseWhenDone(flow, selection);
    }

    private static ExecutionFlow<HttpResponse<?>> releaseWhenDone(ExecutionFlow<HttpResponse<?>> flow, LoadBalancerSelection selection) {
        DelayedExecutionFlow<HttpResponse<?>> released = DelayedExecutionFlow.create();
        flow.onComplete((response, failure) -> {
            selection.releaseUnclaimed();
            if (failure != null) {
                released.completeExceptionally(failure);
            } else {
                released.complete(response);
            }
        });
        released.onCancel(() -> {
            flow.cancel();
            selection.releaseUnclaimed();
        });
        return released;
    }

    private ExecutionFlow<HttpResponse<?>> sendFiltered(
        PropagatedContext propagatedContext,
        AtomicReference<ScheduledExecutorService> preferredScheduler,
        @Nullable Thread blockedThread,
        URI resolvedUri,
        HttpRequest<?> request,
        @Nullable LoadBalancerSelection selection,
        @Nullable AtomicBoolean bufferedHeaders,
        BiFunction<MutableHttpRequest<?>, R, ? extends ExecutionFlow<? extends HttpResponse<?>>> readResponse
    ) {
        try {
            MutableHttpRequest<?> filtered = MutableHttpRequestWrapper.wrapIfNecessary(conversionService, request);
            // a filter may have changed the URI of the request
            return afterFilters(resolvedUri, selection, filtered).flatMap(sent -> {
                ExecutionFlow<HttpResponse<?>> sending = propagatedContext.propagate(() -> sendRequestWithRedirectsNoFilter(
                    propagatedContext,
                    preferredScheduler,
                    blockedThread,
                    sent.uri().equals(filtered.getUri()) ? filtered : filtered.uri(sent.uri()),
                    sent.selection(),
                    bufferedHeaders,
                    readResponse
                ));
                LoadBalancerSelection other = sent.selection();
                // a selection made for the filtered request ends with its exchange too
                return other == null || other == selection ? sending : releaseWhenDone(sending, other);
            });
        } catch (Throwable e) {
            return ExecutionFlow.error(e);
        }
    }

    /**
     * The target of a request once the client filters ran, since a filter may have changed its
     * URI: the request stays with the instance the load balancer selected as long as it goes to
     * the same scheme, host and port, and the load balancer is not asked again.
     *
     * @param resolved  The URI resolved before the filters ran
     * @param selection The selection of the load balancer before the filters ran, or {@code null}
     * @param request   The request the filters passed on
     * @return The target the request is sent to
     */
    protected ExecutionFlow<ResolvedTarget> afterFilters(URI resolved, @Nullable LoadBalancerSelection selection, HttpRequest<?> request) {
        URI filtered = request.getUri();
        if (filtered.equals(resolved)) {
            return ExecutionFlow.just(new ResolvedTarget(resolved, selection));
        }
        if (filtered.getScheme() != null) {
            return ExecutionFlow.just(new ResolvedTarget(filtered, sameServer(filtered, resolved) ? selection : null));
        }
        if (selection == null) {
            return resolveRequestURI(request);
        }
        try {
            return ExecutionFlow.just(new ResolvedTarget(resolveAgainst(selection.instance(), filtered, true), selection));
        } catch (RuntimeException e) {
            return ExecutionFlow.error(e);
        }
    }

    /**
     * @param a The first URI
     * @param b The second URI
     * @return Whether both URIs name the same server: scheme and host ignoring case, and port,
     * the default port of the scheme when there is none
     */
    protected static boolean sameServer(URI a, URI b) {
        return a.getScheme() != null && a.getScheme().equalsIgnoreCase(b.getScheme())
            && a.getHost() != null && a.getHost().equalsIgnoreCase(b.getHost())
            && effectivePort(a) == effectivePort(b);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) || "wss".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    /**
     * @param instance           The service instance
     * @param requestUri         The relative request URI
     * @param includeContextPath Whether to prepend the context path of the client
     * @return The request URI resolved against the instance
     */
    private URI resolveAgainst(ServiceInstance instance, URI requestUri, boolean includeContextPath) {
        try {
            return instance.resolve(includeContextPath ? ContextPathUtils.prepend(requestUri, contextPath) : requestUri);
        } catch (URISyntaxException e) {
            throw decorate(new HttpClientException("Failed to construct the request URI", e));
        }
    }

    private ExecutionFlow<HttpResponse<?>> sendRequestWithRedirectsNoFilter(
        PropagatedContext propagatedContext,
        AtomicReference<ScheduledExecutorService> preferredScheduler,
        @Nullable Thread blockedThread,
        MutableHttpRequest<?> request,
        @Nullable LoadBalancerSelection selection,
        @Nullable AtomicBoolean bufferedHeaders,
        BiFunction<MutableHttpRequest<?>, R, ? extends ExecutionFlow<? extends HttpResponse<?>>> readResponse
    ) {
        ExecutionFlow<R> sending = bufferedHeaders == null
            ? send(propagatedContext, preferredScheduler, blockedThread, request, selection)
            : sendBuffered(propagatedContext, preferredScheduler, blockedThread, request, selection, bufferedHeaders);
        return sending.flatMap(byteBodyResponse -> {
            // handle redirects or map the response bytes
            int code = byteBodyResponse.code();
            String location = byteBodyResponse.getHeaders().get(HttpHeaders.LOCATION);
            if (code > 300 && code < 400 && followsRedirects(request) && location != null) {
                byteBodyResponse.close();
                if (bufferedHeaders != null) {
                    // the headers of the redirect are not those of the response
                    bufferedHeaders.set(false);
                }

                MutableHttpRequest<Object> redirectRequest;
                boolean isRedirectWithBody = code == 307 || code == 308;
                boolean isQueryRedirect = (code == 301 || code == 302) && request.getMethod() == HttpMethod.QUERY;
                boolean preserveBody = isRedirectWithBody || isQueryRedirect;
                if (preserveBody) {
                    redirectRequest = HttpRequest.create(request.getMethod(), location);
                    request.getBody().ifPresent(redirectRequest::body);
                } else {
                    redirectRequest = HttpRequest.GET(location);
                }
                int redirectCount = request.getAttribute(REDIRECT_COUNT, Integer.class).orElse(0) + 1;
                if (redirectCount > configuration.getMaxRedirects()) {
                    return ExecutionFlow.error(decorate(new HttpClientException("Maximum number of redirects exceeded at redirect count: " + redirectCount)));
                }
                redirectRequest.setAttribute(REDIRECT_COUNT, redirectCount);
                // the per-exchange options apply to the whole exchange, redirects included
                copyRedirectAttributes(request, redirectRequest);
                return resolveRedirectURI(request, redirectRequest)
                    .flatMap(target -> {
                        setRedirectHeaders(request, redirectRequest.uri(target.uri()), preserveBody);
                        return sendRequestWithRedirects(propagatedContext, new AtomicReference<>(), blockedThread, redirectRequest.uri(target.uri()), target.selection(), bufferedHeaders, readResponse);
                    })
                    .onErrorResume(e -> {
                        // the body went to the server that redirected, it is not unsent
                        if (e instanceof UnprocessedRequestException unprocessed) {
                            unprocessed.markBodySent();
                        }
                        return ExecutionFlow.error(e);
                    });
            } else {
                HttpHeaders headers = byteBodyResponse.getHeaders();
                if (log.isTraceEnabled()) {
                    log.trace("HTTP Client Response Received ({}) for Request: {} {}", byteBodyResponse.code(), request.getMethodName(), request.getUri());
                    HttpHeadersUtil.trace(log, headers.names(), headers::getAll);
                }
                return readResponse.apply(request, byteBodyResponse);
            }
        });
    }

    /**
     * Whether the redirects of a request are followed here: by default, as configured, unless the
     * request disables them, see {@link #NO_FOLLOW_REDIRECTS}. A transport whose client follows
     * the redirects itself returns {@code false}.
     *
     * @param request The request
     * @return Whether its redirects are followed
     */
    protected boolean followsRedirects(MutableHttpRequest<?> request) {
        return configuration.isFollowRedirects() && request.getAttribute(NO_FOLLOW_REDIRECTS).isEmpty();
    }

    /**
     * Copy the attributes of a request that apply to the whole exchange to the request of a
     * redirect.
     *
     * @param request  The request that was redirected
     * @param redirect The request of the redirect
     */
    protected void copyRedirectAttributes(MutableHttpRequest<?> request, MutableHttpRequest<?> redirect) {
        request.getAttribute(NO_DECOMPRESSION).ifPresent(noDecompression -> redirect.setAttribute(NO_DECOMPRESSION, noDecompression));
        request.getAttribute(READ_IDLE_TIMEOUT).ifPresent(timeout -> redirect.setAttribute(READ_IDLE_TIMEOUT, timeout));
    }

    private void setRedirectHeaders(@Nullable HttpRequest<?> request,
                                    MutableHttpRequest<Object> redirectRequest,
                                    boolean preserveBody) {
        if (request == null) {
            return;
        }
        boolean sameOrigin;
        try {
            sameOrigin = isSameOrigin(request.getUri(), redirectRequest.getUri());
        } catch (Exception e) {
            // fallback
            sameOrigin = false;
        }
        Set<String> headersToBlock = resolveRedirectFilteredHeaders(!sameOrigin, preserveBody);
        for (Map.Entry<String, List<String>> originalHeader : request.getHeaders()) {
            String headerName = originalHeader.getKey();
            if (headersToBlock.contains(headerName)) {
                continue;
            }
            final List<String> originalHeaderValue = originalHeader.getValue();
            if (originalHeaderValue != null && !originalHeaderValue.isEmpty()) {
                for (String value : originalHeaderValue) {
                    if (value != null) {
                        redirectRequest.header(headerName, value);
                    }
                }
            }
        }
    }

    private Set<String> resolveRedirectFilteredHeaders(boolean crossOrigin, boolean preserveBody) {
        if (crossOrigin) {
            return preserveBody ? redirectCrossOriginPreserveBodyHeaders : redirectCrossOriginNonPreserveBodyHeaders;
        }
        return preserveBody ? redirectSameOriginPreserveBodyHeaders : redirectSameOriginNonPreserveBodyHeaders;
    }

    private Set<String> redirectFilteredHeaders(boolean crossOrigin, boolean preserveBody) {
        // header names are case-insensitive
        Set<String> headers = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        headers.addAll(configuration.getRedirectAlwaysFilteredHeaders());
        if (crossOrigin) {
            headers.addAll(configuration.getRedirectCrossOriginFilteredHeaders());
        }
        if (!preserveBody) {
            headers.addAll(configuration.getRedirectAdditionalNonPreserveBodyFilteredHeaders());
        }
        return headers;
    }

    /**
     * Read the whole body of a response, and build the response of the exchange, see
     * {@link #fullResponse}.
     *
     * @param response   The raw response
     * @param bodyType   The body type, or {@code null}
     * @param errorType  The error type
     * @param readFailure Maps a failure to read the body
     * @param <O>        The body type
     * @param <E>        The error type
     * @return The flow of the response, or of the error of an error status
     */
    protected <O, E> ExecutionFlow<? extends HttpResponse<O>> readFullResponse(R response,
                                                                              @Nullable Argument<O> bodyType,
                                                                              Argument<E> errorType,
                                                                              Function<Throwable, Throwable> readFailure) {
        return InternalByteBody.bufferFlow(response.byteBody())
            .onErrorResume(t -> ExecutionFlow.error(readFailure.apply(t)))
            .flatMap(av -> fullResponse(bodyType, errorType, response, av));
    }

    // ---- errors

    /**
     * Map a failure of a response, before or after its headers, to a client exception. The
     * outcome for the load balancer is not reported here.
     *
     * @param finalRequest The request
     * @param instance     The service instance the load balancer selected, or {@code null}
     * @param cause        The failure
     * @return The client exception
     */
    public HttpClientException handleResponseError(HttpRequest<?> finalRequest, @Nullable ServiceInstance instance, Throwable cause) {
        String message = cause.getMessage();
        if (message == null) {
            message = cause.getClass().getSimpleName();
        }
        if (log.isTraceEnabled()) {
            log.trace("HTTP Client exception ({}) occurred for request : {} {}",
                message, finalRequest.getMethodName(), finalRequest.getUri());
        }

        HttpClientException result;
        HttpClientException transport = mapReadFailure(cause);
        if (transport != null) {
            result = transport;
        } else if (cause instanceof io.micronaut.http.exceptions.ContentLengthExceededException clee) {
            result = decorate(new ContentLengthExceededException(Objects.requireNonNull(clee.getMessage(), "Content length exceeded")));
        } else if (cause instanceof BufferLengthExceededException blee) {
            result = decorate(new ContentLengthExceededException(blee.getAdvertisedLength(), blee.getReceivedLength()));
        } else if (cause instanceof ReadTimeoutException rte) {
            // already mapped (e.g. a body timeout from the request-timeout path), keep it as-is
            result = rte;
        } else if (cause instanceof HttpClientException hce) {
            result = decorate(hce);
        } else {
            result = decorate(new HttpClientException("Error occurred reading HTTP response: " + message, cause));
        }
        if (result instanceof UnprocessedRequestException unprocessed) {
            unprocessed.setTarget(finalRequest.getUri(), instance);
            if (unprocessed.getServiceId() == null) {
                decorate(unprocessed);
            }
        }
        return result;
    }

    /**
     * Whether the body of a response is decoded into the body type: unless the status is an
     * error, which fails the exchange, except when the body type is the error type and the client
     * does not fail on an error status.
     *
     * @param code      The status of the response
     * @param bodyType  The body type, or {@code null}
     * @param errorType The error type
     * @param <O>       The body type
     * @param <E>       The error type
     * @return Whether the body is decoded into the body type
     */
    protected <O, E> boolean convertsWithBodyType(int code, @Nullable Argument<O> bodyType, Argument<E> errorType) {
        if (code < 400) {
            return true;
        }
        return !configuration.isExceptionOnErrorStatus() && bodyType != null && bodyType.equalsType(errorType);
    }

    /**
     * The failure of an exchange whose response has an error status: its body is decoded into
     * the error type.
     *
     * @param errorType The error type, or {@code null}
     * @param response  The response
     * @return The failure
     */
    protected HttpClientResponseException errorStatusException(@Nullable Argument<?> errorType, HttpResponse<?> response) {
        if (errorType != null && errorType != HttpClient.DEFAULT_ERROR_TYPE) {
            return decorate(new HttpClientResponseException(
                response.reason(),
                null,
                response,
                new HttpClientErrorDecoder() {
                    @Override
                    public Argument<?> getErrorType(MediaType mediaType) {
                        return errorType;
                    }
                }
            ));
        }
        return decorate(new HttpClientResponseException(response.reason(), response));
    }

    /**
     * The failure of a streaming exchange whose response has an error status, with the error body
     * decoded into the error type.
     *
     * @param errorType The error type
     * @param response  The raw response
     * @param body      The error body, which this takes over
     * @return The flow of the failure
     */
    private ExecutionFlow<HttpResponse<?>> errorStatusResponse(Argument<?> errorType, R response, CloseableAvailableByteBody body) {
        // a transport may return the response of an error status of an exchange, e.g. when the
        // client does not fail on an error status: a stream fails in any case
        return fullResponse(null, errorType, response, body)
            .flatMap(full -> ExecutionFlow.error(errorStatusException(errorType, full)));
    }

    /**
     * Whether the body of an error response of a streaming call should be read and attached to
     * the {@link HttpClientResponseException}.
     *
     * @param errorType The error type
     * @return {@code true} if the error body should be buffered
     */
    protected boolean shouldBufferErrorBody(@Nullable Argument<?> errorType) {
        return errorType != null && (errorType != HttpClient.DEFAULT_ERROR_TYPE || configuration.isBufferErrorBodyForStreaming());
    }

    /**
     * Report the outcome of an exchange to the load balancer that selected its instance, if any.
     *
     * @param selection The selection of the load balancer, or {@code null}
     * @param outcome   The outcome
     */
    public static void report(@Nullable LoadBalancerSelection selection, LoadBalancer.Outcome outcome) {
        if (selection != null) {
            selection.report(outcome);
        }
    }

    /**
     * Release the selection of the load balancer, if any, unless an outcome was reported already.
     *
     * @param selection The selection of the load balancer, or {@code null}
     */
    public static void releaseSelection(@Nullable LoadBalancerSelection selection) {
        if (selection != null) {
            selection.release();
        }
    }

    // ---- exchanges

    @Override
    public <I, O, E> Publisher<HttpResponse<O>> exchange(HttpRequest<I> request, @Nullable Argument<O> bodyType, Argument<E> errorType) {
        return Flux.defer(() -> toMono(exchangeFlow(request, bodyType, errorType, null), PropagatedContext.getOrEmpty()).flux());
    }

    /**
     * The exchange request.
     *
     * @param request   The request
     * @param bodyType  The body argument
     * @param errorType The error argument
     * @param <I>       The request type
     * @param <O>       The body type
     * @param <E>       The error type
     * @return The execution flow
     */
    public <I, O, E> ExecutionFlow<HttpResponse<O>> exchangeFlow(HttpRequest<I> request,
                                                                 @Nullable Argument<O> bodyType,
                                                                 Argument<E> errorType) {
        return exchangeFlow(request, bodyType, errorType, null);
    }

    /**
     * The exchange request.
     *
     * @param request       The request
     * @param bodyType      The body argument
     * @param errorType     The error argument
     * @param blockedThread The thread that blocks on the response, if any
     * @param <I>           The request type
     * @param <O>           The body type
     * @param <E>           The error type
     * @return The execution flow
     */
    protected <I, O, E> ExecutionFlow<HttpResponse<O>> exchangeFlow(HttpRequest<I> request,
                                                                    @Nullable Argument<O> bodyType,
                                                                    Argument<E> errorType,
                                                                    @Nullable Thread blockedThread) {
        setupConversionService(request);
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        // if a connection is available immediately, we can use its executor for the timeout
        // instead of a random executor for the whole group
        AtomicReference<ScheduledExecutorService> scheduler = new AtomicReference<>(scheduler());
        // whether a response arrived whose body is still read, to tell a request timeout while
        // the body is read from one before the response; cleared once the body ended
        AtomicBoolean headersReceived = new AtomicBoolean();
        ExecutionFlow<HttpResponse<O>> flow = resolveRequestURI(request).flatMap(target -> {
            MutableHttpRequest<?> mutableRequest = toMutableRequest(request).uri(target.uri());
            //noinspection unchecked
            return sendRequestWithRedirects(
                propagatedContext,
                scheduler,
                blockedThread,
                mutableRequest,
                target.selection(),
                headersReceived,
                (req, resp) -> {
                    headersReceived.set(true);
                    return readFullResponse(resp, bodyType, errorType, t -> {
                        headersReceived.set(false);
                        return handleResponseError(mutableRequest, target.instance(), t);
                    }).map(r -> {
                        headersReceived.set(false);
                        return r;
                    });
                }
            ).map(r -> (HttpResponse<O>) r);
        });

        Duration requestTimeout = requestTimeout();
        if (requestTimeout != null) {
            if (!requestTimeout.isNegative()) {
                flow = flow.timeout(requestTimeout, Objects.requireNonNull(scheduler.get()), null)
                    .onErrorResume(throwable -> {
                        if (throwable instanceof TimeoutException) {
                            return ExecutionFlow.error(decorate(new ReadTimeoutException(headersReceived.get())));
                        }
                        return ExecutionFlow.error(throwable);
                    });
            }
        }
        return flow;
    }

    /**
     * The timeout of an exchange whose response body is read whole: the
     * {@link HttpClientConfiguration#getRequestTimeout() request timeout}, or, when none is
     * configured, for compatibility, the read timeout plus one second.
     *
     * @return The timeout, or {@code null} for none
     */
    protected @Nullable Duration requestTimeout() {
        Duration requestTimeout = configuration.getRequestTimeout();
        if (requestTimeout != null) {
            return requestTimeout;
        }
        return configuration.getReadTimeout()
            .filter(d -> !d.isNegative())
            .map(d -> d.plusSeconds(1)).orElse(null);
    }

    @Override
    public <I, O, E> Publisher<O> retrieve(HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType) {
        setupConversionService(request);
        // mostly same as default impl, but with exception customization
        Flux<HttpResponse<O>> exchange = Flux.from(exchange(request, bodyType, errorType));
        if (bodyType.getType() == void.class) {
            // exchange() returns a HttpResponse<Void>, we can't map the Void body properly, so just drop it and complete
            return (Publisher<O>) exchange.ignoreElements();
        }
        return exchange.map(response -> {
            if (bodyType.getType() == HttpStatus.class) {
                return (O) response.getStatus();
            } else {
                Optional<O> body = response.getBody();
                if (body.isEmpty() && response.getBody(byte[].class).isPresent()) {
                    throw decorate(new HttpClientResponseException(
                        "Failed to decode the body for the given content type [%s]".formatted(response.getContentType().orElse(null)),
                        response
                    ));
                } else {
                    return body.orElseThrow(() -> decorate(new HttpClientResponseException(
                        "Empty body",
                        response
                    )));
                }
            }
        });
    }

    // ---- asynchronous views

    @Override
    public AsyncHttpClient toAsync() {
        return new DefaultAsyncHttpClient(this);
    }

    @Override
    public AsyncStreamingHttpClient toAsyncStreaming() {
        return new DefaultAsyncHttpClient(this);
    }

    /**
     * The {@link #exchangeEventStream} of {@link DefaultAsyncHttpClient}, without Reactor.
     * The flow completes with the status and the headers of the response, and its events are read
     * from the response body as they are pulled.
     *
     * @param request   The request
     * @param eventType The event data type
     * @param errorType The error type
     * @param <I>       The request body type
     * @param <B>       The event data type
     * @return The flow of the response, whose body is the events
     */
    final <I, B> ExecutionFlow<HttpResponse<BodyElements<Event<B>>>> exchangeEventStreamFlow(HttpRequest<I> request, Argument<B> eventType, Argument<?> errorType) {
        setupConversionService(request);
        return exchangeEventStreamFlow(PropagatedContext.getOrEmpty(), toMutableRequest(request), eventType, errorType);
    }

    private <B> ExecutionFlow<HttpResponse<BodyElements<Event<B>>>> exchangeEventStreamFlow(PropagatedContext propagatedContext, MutableHttpRequest<?> request, Argument<B> eventType, Argument<?> errorType) {
        EventStreams.acceptEvents(request);
        return exchangeElementsFlow(propagatedContext, request, errorType, true,
            (req, response) -> EventStreams.response(response, handlerRegistry, eventType, sizeLimits().maxBufferSize(), this::decorate));
    }

    /**
     * The {@link #exchangeStream} of {@link DefaultAsyncHttpClient}, without Reactor: the pieces of
     * the response body are read as they are pulled.
     *
     * @param request   The request
     * @param errorType The error type
     * @param <I>       The request body type
     * @return The flow of the response, whose body is the pieces of the response body
     */
    final <I> ExecutionFlow<HttpResponse<BodyElements<ByteBuffer<?>>>> exchangeStreamFlow(HttpRequest<I> request, Argument<?> errorType) {
        setupConversionService(request);
        return exchangeElementsFlow(PropagatedContext.getOrEmpty(), toMutableRequest(request), errorType, true,
            (req, response) -> ElementsResponse.of(response, BodyPieces.elements(response.byteBody().move())));
    }

    /** Native response pieces: ownership passes to the caller, without copying. */
    final <I> ExecutionFlow<HttpResponse<BodyElements<ReadBuffer>>> exchangeReadBuffersFlow(HttpRequest<I> request, Argument<?> errorType) {
        setupConversionService(request);
        return exchangeElementsFlow(PropagatedContext.getOrEmpty(), toMutableRequest(request), errorType, true,
            (req, response) -> ElementsResponse.of(response, response.byteBody().move().toReadBufferElements()));
    }

    /**
     * The pieces of {@link #dataStream} and {@link #exchangeStream}: the body of an event stream
     * that the request accepts is split into lines, and the error body is read per the
     * configuration.
     *
     * @param propagatedContext The context the request is sent with
     * @param request           The request
     * @param errorType         The error type
     * @return The flow of the response, whose body is the pieces of the response body
     */
    private ExecutionFlow<HttpResponse<BodyElements<ByteBuffer<?>>>> dataStreamFlow(PropagatedContext propagatedContext, MutableHttpRequest<?> request, Argument<?> errorType) {
        return exchangeElementsFlow(propagatedContext, request, errorType, shouldBufferErrorBody(errorType),
            (req, response) -> ElementsResponse.of(response, streamPieces(response.byteBody().move(), isAcceptEvents(req))));
    }

    /**
     * The pieces of the body of {@link #dataStream} and {@link #exchangeStream}: the lines of an
     * event stream that the request accepts, or the pieces of the body as they are read. As a
     * publisher, the body is read as it arrives, with the bytes that wait for the subscriber
     * limited by {@code max-content-length}.
     *
     * @param body  The body, which the pieces take over
     * @param lines Whether the body is split into the lines of an event stream
     * @return The pieces of the body
     */
    protected BodyElements<ByteBuffer<?>> streamPieces(CloseableByteBody body, boolean lines) {
        return new StreamedPieces(body, lines, sizeLimits().maxBufferSize());
    }

    /**
     * The publisher of the pieces of {@link #dataStream} and {@link #exchangeStream}.
     *
     * @param pieces The pieces of {@link #streamPieces}, or other pieces, e.g. of a response that
     *               a client filter replaced
     * @return The publisher of the pieces, for one subscriber
     */
    protected Publisher<ByteBuffer<?>> streamPiecesPublisher(BodyElements<ByteBuffer<?>> pieces) {
        if (pieces instanceof StreamedPieces streamed) {
            return streamed.publisher();
        }
        // e.g. the empty body of a response without one
        return publisher(pieces);
    }

    /**
     * The {@link #jsonStream} of {@link DefaultAsyncHttpClient}, without Reactor: the elements are
     * split from the pieces of the body and decoded by the piece reader of the JSON reader as they
     * are pulled.
     *
     * @param request   The request
     * @param type      The type of an element
     * @param errorType The error type
     * @param <I>       The request body type
     * @param <O>       The type of an element
     * @return The flow of the response, whose body is the elements
     */
    final <I, O> ExecutionFlow<HttpResponse<BodyElements<O>>> jsonStreamFlow(HttpRequest<I> request, Argument<O> type, Argument<?> errorType) {
        setupConversionService(request);
        return jsonStreamFlow(PropagatedContext.getOrEmpty(), toMutableRequest(request), type, errorType, true);
    }

    /**
     * @param propagatedContext The context the request is sent with
     * @param request           The request
     * @param type              The type of an element
     * @param errorType         The error type
     * @param bufferErrorBody   Whether the error body is read, to be decoded into the error type
     * @param <O>               The type of an element
     * @return The flow of the response, whose body is the elements
     */
    private <O> ExecutionFlow<HttpResponse<BodyElements<O>>> jsonStreamFlow(PropagatedContext propagatedContext, MutableHttpRequest<?> request, Argument<O> type, Argument<?> errorType, boolean bufferErrorBody) {
        return exchangeElementsFlow(propagatedContext, request, errorType, bufferErrorBody, (req, response) -> {
            // could also be application/json, in which case the elements of an array are read
            MediaType mediaType = response.getContentType().orElse(MediaType.APPLICATION_JSON_STREAM_TYPE);
            if (!(handlerRegistry.getReader(type, List.of(mediaType)) instanceof ChunkedMessageBodyReader<O> reader)) {
                throw new CodecException("No reader of the elements of a [" + mediaType + "] body");
            }
            HttpHeaders headers = response.getHeaders();
            // an element is decoded in memory: it is limited like buffered content
            long maxElementSize = sizeLimits().maxBufferSize();
            // without Reactor: the pieces are split into elements as they are pulled
            PieceReader<O> pieceReader = PieceReaders.open(reader, type, mediaType, headers, maxElementSize);
            CloseableByteBody body = response.byteBody().move();
            return ElementsResponse.of(response, new ByteBodyElements<>(body, pieceReader, Function.identity()));
        });
    }

    /**
     * An exchange whose response body is read as elements as they are pulled, without Reactor.
     * The flow completes with the status and the headers of the response. An error status fails
     * it with the error body decoded into the error type, as for {@link #exchange}.
     *
     * @param propagatedContext The context the request is sent with, which the filters see: the
     *                          context of the caller of the client
     * @param mutableRequest    The request, with its conversion service and Accept header set up
     * @param errorType         The error type
     * @param bufferErrorBody   Whether the error body is read, to be decoded into the error type,
     *                          else the error has the status and the headers only
     * @param elements          The response with the elements of the body, taking over the body
     * @param <T>               The type of an element
     * @return The flow of the response, whose body is the elements
     */
    private <T> ExecutionFlow<HttpResponse<BodyElements<T>>> exchangeElementsFlow(PropagatedContext propagatedContext,
                                                                                MutableHttpRequest<?> mutableRequest,
                                                                                Argument<?> errorType,
                                                                                boolean bufferErrorBody,
                                                                                 BiFunction<HttpRequest<?>, R, HttpResponse<BodyElements<T>>> elements) {
        return exchangeStreamingFlow(propagatedContext, mutableRequest, errorType, bufferErrorBody, elements,
            BodyElements.class, () -> {
                @SuppressWarnings("unchecked")
                BodyElements<T> none = (BodyElements<T>) (BodyElements<?>) BodyPieces.elements(AvailableByteArrayBody.create(ByteArrayBufferFactory.INSTANCE, new byte[0]));
                return none;
            }, ElementsStages::closeElements);
    }

    private <T> ExecutionFlow<HttpResponse<T>> exchangeStreamingFlow(PropagatedContext propagatedContext,
                                                                    MutableHttpRequest<?> mutableRequest,
                                                                    Argument<?> errorType,
                                                                    boolean bufferErrorBody,
                                                                    BiFunction<HttpRequest<?>, R, HttpResponse<T>> elements,
                                                                    Class<?> bodyType,
                                                                    java.util.function.Supplier<T> empty,
                                                                    java.util.function.Consumer<HttpResponse<T>> close) {
        // the last response with elements, closed if a filter replaces it
        AtomicReference<@Nullable HttpResponse<T>> created = new AtomicReference<>();
        return resolveRequestURI(mutableRequest).flatMap(target -> sendRequestWithRedirects(
            propagatedContext,
            null,
            mutableRequest.uri(target.uri()),
            target.selection(),
            (req, resp) -> {
                if (resp.code() >= 400 && !bufferErrorBody) {
                    // The error body will never be consumed by the caller, so discard it right
                    // away. Otherwise the connection would stay reserved until the read timeout.
                    resp.close();
                    return ExecutionFlow.error(decorate(new HttpClientResponseException(resp.reason(), new ElementResponse<>(resp, null))));
                }
                if (resp.code() >= 400) {
                    // the error body is decoded into the error type, as for exchange
                    return InternalByteBody.bufferFlow(resp.byteBody())
                        .onErrorResume(t -> ExecutionFlow.error(handleResponseError(mutableRequest, target.instance(), t)))
                        .flatMap(av -> errorStatusResponse(errorType, resp, av));
                }
                if (!hasBody(resp)) {
                    // no element
                    resp.close();
                    return ExecutionFlow.just(new ElementResponse<>(resp, empty.get()));
                }
                try {
                    HttpResponse<T> withElements = elements.apply(req, resp);
                    created.set(withElements);
                    return ExecutionFlow.just(withElements);
                } catch (RuntimeException e) {
                    resp.close();
                    return ExecutionFlow.error(e);
                }
            }
        )).flatMap(response -> {
            if (!bodyType.isInstance(response.getBody().orElse(null))) {
                HttpResponse<T> replaced = created.getAndSet(null);
                if (replaced != null) {
                    // nobody reads them: the connection is released
                    close.accept(replaced);
                }
                return ExecutionFlow.error(new IllegalStateException("Response has been replaced by a response without elements. Do not replace the response in client filters for streaming requests"));
            }
            @SuppressWarnings("unchecked")
            HttpResponse<T> result = (HttpResponse<T>) response;
            // Ownership has reached the caller; exchange failure cleanup must no longer own it.
            created.set(null);
            return ExecutionFlow.just(result);
        }).onErrorResume(error -> {
            HttpResponse<T> failed = created.getAndSet(null);
            if (failed != null) {
                // A throwing response filter leaves the created stream without a reader.
                try {
                    close.accept(failed);
                } catch (Throwable cleanupError) {
                    if (cleanupError != error) {
                        error.addSuppressed(cleanupError);
                    }
                }
            }
            return ExecutionFlow.error(error);
        });
    }

    // ---- server sent events

    @Override
    public <I, B> Publisher<HttpResponse<Event<B>>> exchangeEventStream(HttpRequest<I> request, Argument<B> eventType, Argument<?> errorType) {
        // the exchange of the async client: the events are decoded by its piece reader as they are
        // requested, and each one is wrapped in the response. The request is sent with the
        // context of the caller
        setupConversionService(request);
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        return Flux.defer(() -> toMono(exchangeEventStreamFlow(propagatedContext, toMutableRequest(request), eventType, errorType), propagatedContext)
            .flatMapMany(response -> {
                BodyElements<Event<B>> events = Objects.requireNonNull(response.body(), "The response has no events");
                return Flux.from(publisher(events))
                    .map(event -> (HttpResponse<Event<B>>) new ElementResponse<>(response, event))
                    // without an event, the status and the headers of the response are still of interest
                    .switchIfEmpty(Mono.fromSupplier(() -> new ElementResponse<>(response, null)));
            }));
    }

    @Override
    public <I> Publisher<Event<ByteBuffer<?>>> eventStream(HttpRequest<I> request) {
        return Flux.from(eventStreamOrError(request, Argument.of(byte[].class), null))
            .map(event -> Event.of(event, (ByteBuffer<?>) byteBufferFactory().wrap(event.getData())));
    }

    @Override
    public <I, B> Publisher<Event<B>> eventStream(HttpRequest<I> request, Argument<B> eventType) {
        return eventStream(request, eventType, DEFAULT_ERROR_TYPE);
    }

    @Override
    public <I, B> Publisher<Event<B>> eventStream(HttpRequest<I> request, Argument<B> eventType, Argument<?> errorType) {
        return eventStreamOrError(request, eventType, errorType);
    }

    /**
     * The events of {@link #eventStream}: the request accepts only an event stream, and the body
     * is read as one, whatever its content type, as it always was.
     *
     * @param request   The request
     * @param eventType The event data type
     * @param errorType The error type, or {@code null} if the error body is not read
     * @param <I>       The request body type
     * @param <B>       The event data type
     * @return The events
     */
    private <I, B> Publisher<Event<B>> eventStreamOrError(HttpRequest<I> request, Argument<B> eventType, @Nullable Argument<?> errorType) {
        setupConversionService(request);
        if (request instanceof MutableHttpRequest<?> httpRequest) {
            // replace, rather than add to, what the caller accepts: a server that may answer with another type, such
            // as JSON, would otherwise do so, and the body would yield no event
            httpRequest.getHeaders().set(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM);
        }
        // as it always did, the event stream sends the request with the context of the subscriber
        return Flux.defer(() -> {
            PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
            return toMono(exchangeElementsFlow(propagatedContext, toMutableRequest(request), errorType == null ? DEFAULT_ERROR_TYPE : errorType, shouldBufferErrorBody(errorType),
                (req, response) -> EventStreams.eventStreamResponse(response, handlerRegistry, eventType, sizeLimits().maxBufferSize(), this::decorate)), propagatedContext)
                .flatMapMany(AbstractHttpClient::elements);
        });
    }

    // ---- streams

    @Override
    public <I> Publisher<ByteBuffer<?>> dataStream(HttpRequest<I> request) {
        return dataStream(request, DEFAULT_ERROR_TYPE);
    }

    @Override
    public <I> Publisher<ByteBuffer<?>> dataStream(HttpRequest<I> request, @Nullable Argument<?> errorType) {
        // the request is sent with the context of the caller, as it always was
        setupConversionService(request);
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        return Flux.defer(() -> toMono(dataStreamFlow(propagatedContext, toMutableRequest(request), errorType == null ? DEFAULT_ERROR_TYPE : errorType), propagatedContext)
            .flatMapMany(response -> streamPiecesPublisher(Objects.requireNonNull(response.body(), "The response has no body"))));
    }

    @Override
    public <I> Publisher<HttpResponse<ByteBuffer<?>>> exchangeStream(HttpRequest<I> request) {
        return exchangeStream(request, DEFAULT_ERROR_TYPE);
    }

    @Override
    public <I> Publisher<HttpResponse<ByteBuffer<?>>> exchangeStream(HttpRequest<I> request, Argument<?> errorType) {
        setupConversionService(request);
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        return Flux.defer(() -> toMono(dataStreamFlow(propagatedContext, toMutableRequest(request), errorType), propagatedContext)
            .flatMapMany(response -> {
                BodyElements<ByteBuffer<?>> pieces = Objects.requireNonNull(response.body(), "The response has no body");
                return Flux.from(streamPiecesPublisher(pieces))
                    .map(piece -> (HttpResponse<ByteBuffer<?>>) new ElementResponse<>(response, piece));
            }));
    }

    @Override
    public <I, O> Publisher<O> jsonStream(HttpRequest<I> request, Argument<O> type) {
        return jsonStream(request, type, DEFAULT_ERROR_TYPE);
    }

    @Override
    public <I, O> Publisher<O> jsonStream(HttpRequest<I> request, Argument<O> type, Argument<?> errorType) {
        // the request is sent with the context of the caller, as it always was
        setupConversionService(request);
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        return Flux.defer(() -> toMono(this.<Publisher<? extends O>>exchangeStreamingFlow(propagatedContext, toMutableRequest(request), errorType,
            shouldBufferErrorBody(errorType), (req, response) -> {
                MediaType mediaType = response.getContentType().orElse(MediaType.APPLICATION_JSON_STREAM_TYPE);
                if (!(handlerRegistry.getReader(type, List.of(mediaType)) instanceof ChunkedMessageBodyReader<O> reader)) {
                    throw new CodecException("No reader of the elements of a [" + mediaType + "] body");
                }
                Publisher<? extends O> decoded = reader.readChunked(type, mediaType, response.getHeaders(),
                    response.byteBody().toByteBufferPublisher());
                return new ElementResponse<>(response, decoded);
            }, Publisher.class, Flux::empty, response -> Flux.from(Objects.requireNonNull(response.body())).subscribe(new reactor.core.publisher.BaseSubscriber<O>() {
                @Override
                protected void hookOnSubscribe(org.reactivestreams.Subscription subscription) {
                    cancel();
                }
            })), propagatedContext).flatMapMany(response -> Flux.from(Objects.requireNonNull(response.body()))));
    }

    /**
     * The elements of the body of a response, as a publisher.
     *
     * @param response The response
     * @param <T>      The type of an element
     * @return The elements
     */
    private static <T> Publisher<T> elements(HttpResponse<BodyElements<T>> response) {
        return publisher(Objects.requireNonNull(response.body(), "The response has no elements"));
    }

    /**
     * @param elements The elements of a response body
     * @param <T>      The element type
     * @return The elements as a publisher: pushed by the piece reader where the elements are read
     * from the body, else pulled one at a time
     */
    private static <T> Publisher<T> publisher(BodyElements<T> elements) {
        return elements instanceof ByteBodyElements<T> byteBody
            ? byteBody.toPublisher()
            : new BodyElementsPublisher<>(elements);
    }

    // ---- flows

    /**
     * @param flow    The flow
     * @param context The propagated context
     * @param <T>     The value type
     * @return The flow as a Mono, for the reactive API
     */
    protected static <T> Mono<T> toMono(ExecutionFlow<T> flow, PropagatedContext context) {
        return Mono.from(ReactivePropagation.propagate(context, ReactiveExecutionFlow.toPublisher(flow)));
    }

    /**
     * Wait for the given flow to complete on the calling thread. Errors are reported the same way
     * as reactor's {@code Mono.block()}: checked exceptions are wrapped using
     * {@link Exceptions#propagate(Throwable)}, and a suppressed exception carrying the stack trace
     * of the calling thread is added. If the thread is interrupted, the flow is cancelled.
     *
     * @param flow The flow to wait for
     * @param <T>  The value type
     * @return The flow value
     */
    @Nullable
    public static <T> T awaitFlow(ExecutionFlow<T> flow) {
        T value;
        Throwable error;
        ImperativeExecutionFlow<T> complete = flow.tryComplete();
        if (complete != null) {
            value = complete.getValue();
            error = complete.getError();
        } else {
            BlockingFlowListener<T> listener = new BlockingFlowListener<>();
            flow.onComplete(listener);
            try {
                listener.await();
            } catch (InterruptedException e) {
                flow.cancel();
                Thread.currentThread().interrupt();
                throw Exceptions.propagate(e);
            }
            value = listener.value;
            error = listener.error;
        }
        if (error != null) {
            RuntimeException re = Exceptions.propagate(error);
            // the error usually comes from the event loop, keep the stack trace of the caller
            re.addSuppressed(new Exception("#block terminated with an error"));
            throw re;
        }
        return value;
    }

    /**
     * The absolute URI a request is sent to, and the service instance the load balancer selected
     * for it, if the request was load balanced.
     *
     * @param uri       The absolute request URI
     * @param selection The selection of the load balancer, or {@code null} if the request URI
     *                  was absolute
     */
    public record ResolvedTarget(URI uri, @Nullable LoadBalancerSelection selection) {

        /**
         * @return The selected instance, or {@code null} if the request URI was absolute
         */
        public @Nullable ServiceInstance instance() {
            return selection == null ? null : selection.instance();
        }

        /**
         * @return The URI, for a caller that sends no exchange: the selection is released
         */
        public URI releasedUri() {
            if (selection != null) {
                selection.release();
            }
            return uri;
        }
    }

    /**
     * Completion listener that a blocking caller waits on, see {@link #awaitFlow(ExecutionFlow)}.
     *
     * @param <T> The value type
     */
    private static final class BlockingFlowListener<T> extends CountDownLatch implements BiConsumer<T, @Nullable Throwable> {
        @Nullable
        T value;
        @Nullable
        Throwable error;

        BlockingFlowListener() {
            super(1);
        }

        @Override
        public void accept(T value, @Nullable Throwable error) {
            // the count down publishes these fields to the waiting thread
            this.value = value;
            this.error = error;
            countDown();
        }
    }
}
