/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.server.netty;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.DelegateByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpParameters;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.PushCapableHttpRequest;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.Cookies;
import io.micronaut.http.netty.AbstractNettyHttpRequest;
import io.micronaut.http.netty.NettyHttpHeaders;
import io.micronaut.http.netty.NettyHttpParameters;
import io.micronaut.http.netty.NettyHttpRequestBuilder;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.http.netty.cookies.NettyCookies;
import io.micronaut.http.netty.stream.DefaultStreamedHttpRequest;
import io.micronaut.http.netty.stream.StreamedHttpRequest;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.netty.body.ByteBody;
import io.micronaut.http.server.netty.body.HttpBody;
import io.micronaut.http.server.netty.body.ImmediateByteBody;
import io.micronaut.http.server.netty.body.ImmediateMultiObjectBody;
import io.micronaut.http.server.netty.body.ImmediateSingleObjectBody;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.netty.multipart.NettyCompletedFileUpload;
import io.micronaut.web.router.RouteMatch;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.cookie.ClientCookieEncoder;
import io.netty.handler.codec.http2.DefaultHttp2PushPromiseFrame;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.security.cert.Certificate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Delegates to the Netty {@link io.netty.handler.codec.http.HttpRequest} instance.
 *
 * @param <T> The type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class NettyHttpRequest<T> extends AbstractNettyHttpRequest<T> implements HttpRequest<T>, PushCapableHttpRequest<T>, io.micronaut.http.FullHttpRequest<T> {
    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpRequest.class);

    /**
     * Headers to exclude from the push promise sent to the client. We use
     * {@link io.netty.handler.codec.http.HttpHeaders} to store this set, so that the semantics (e.g. case
     * insensitivity) are the same as for the actual header storage, so that we don't accidentally copy headers we're
     * not supposed to.
     */
    private static final io.netty.handler.codec.http.HttpHeaders SERVER_PUSH_EXCLUDE_HEADERS;

    static {
        SERVER_PUSH_EXCLUDE_HEADERS = new DefaultHttpHeaders();
        // from JavaEE PushBuilder javadoc: "The existing request headers of the current HttpServletRequest are added to the builder, except for: "
        // "Conditional headers (defined in RFC 7232)"
        SERVER_PUSH_EXCLUDE_HEADERS.add(HttpHeaderNames.ETAG, "");
        SERVER_PUSH_EXCLUDE_HEADERS.add(HttpHeaderNames.IF_MATCH, "");
        SERVER_PUSH_EXCLUDE_HEADERS.add(HttpHeaderNames.IF_MODIFIED_SINCE, "");
        SERVER_PUSH_EXCLUDE_HEADERS.add(HttpHeaderNames.IF_NONE_MATCH, "");
        SERVER_PUSH_EXCLUDE_HEADERS.add(HttpHeaderNames.IF_UNMODIFIED_SINCE, "");
        SERVER_PUSH_EXCLUDE_HEADERS.add(HttpHeaderNames.LAST_MODIFIED, "");
        // "Range headers" (RFC 7233)
        SERVER_PUSH_EXCLUDE_HEADERS.add(HttpHeaderNames.ACCEPT_RANGES, "");
        SERVER_PUSH_EXCLUDE_HEADERS.add(HttpHeaderNames.CONTENT_RANGE, "");
        SERVER_PUSH_EXCLUDE_HEADERS.add(HttpHeaderNames.IF_RANGE, "");
        SERVER_PUSH_EXCLUDE_HEADERS.add(HttpHeaderNames.RANGE, "");
        // "Expect headers"
        SERVER_PUSH_EXCLUDE_HEADERS.add(HttpHeaderNames.EXPECT, "");
        // "Referrer headers"
        SERVER_PUSH_EXCLUDE_HEADERS.add(HttpHeaderNames.REFERER, "");
        // "Authorization headers"
        SERVER_PUSH_EXCLUDE_HEADERS.add(HttpHeaderNames.PROXY_AUTHENTICATE, "");
        SERVER_PUSH_EXCLUDE_HEADERS.add(HttpHeaderNames.PROXY_AUTHORIZATION, "");
        // we do copy other authorization headers and cookies. This is a potential security risk, e.g. if there's an
        // intermediate HTTP proxy that adds auth headers that the client isn't supposed to see – the client will
        // receive a copy of those headers in the PUSH_PROMISE. However, I'm not sure if the client will utilize the
        // pushed response properly if we don't send the authorization headers. It might also depend on the Vary
        // header. This behavior is documented in PushCapableHttpRequest.

        // some netty headers we won't copy
        SERVER_PUSH_EXCLUDE_HEADERS.add(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), "");
        SERVER_PUSH_EXCLUDE_HEADERS.add(HttpConversionUtil.ExtensionHeaderNames.PATH.text(), "");
        SERVER_PUSH_EXCLUDE_HEADERS.add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), "");
        SERVER_PUSH_EXCLUDE_HEADERS.add(HttpConversionUtil.ExtensionHeaderNames.STREAM_PROMISE_ID.text(), "");
        // we do copy the weight and dependency id
    }

    private final NettyHttpHeaders headers;
    private final ChannelHandlerContext channelHandlerContext;
    private final HttpServerConfiguration serverConfiguration;
    private MutableConvertibleValues<Object> attributes;
    private NettyCookies nettyCookies;
    private final ByteBody body;
    @Nullable
    private FormRouteCompleter formRouteCompleter;
    private ExecutionFlow<?> routeWaitsFor = ExecutionFlow.just(null);

    /**
     * Set to {@code true} when the {@link #headers} may have been mutated. If this is not the case,
     * we can cache some values.
     */
    private boolean headersMutated = false;
    private final long contentLength;
    @Nullable
    private final MediaType contentType;
    @Nullable
    private final String origin;

    private final BodyConvertor bodyConvertor = newBodyConvertor();

    /**
     * @param nettyRequest        The {@link io.netty.handler.codec.http.HttpRequest}
     * @param ctx                 The {@link ChannelHandlerContext}
     * @param environment         The Environment
     * @param serverConfiguration The {@link HttpServerConfiguration}
     * @throws IllegalArgumentException When the request URI is invalid
     */
    @SuppressWarnings("MagicNumber")
    public NettyHttpRequest(io.netty.handler.codec.http.HttpRequest nettyRequest,
                            ChannelHandlerContext ctx,
                            ConversionService environment,
                            HttpServerConfiguration serverConfiguration) throws IllegalArgumentException {
        super(nettyRequest, environment);
        Objects.requireNonNull(nettyRequest, "Netty request cannot be null");
        Objects.requireNonNull(ctx, "ChannelHandlerContext cannot be null");
        Objects.requireNonNull(environment, "Environment cannot be null");
        Channel channel = ctx.channel();
        if (channel != null) {
            channel.attr(ServerAttributeKeys.REQUEST_KEY).set(this);
        }
        this.serverConfiguration = serverConfiguration;
        this.channelHandlerContext = ctx;
        this.headers = new NettyHttpHeaders(nettyRequest.headers(), conversionService);
        this.body = ByteBody.of(nettyRequest);
        this.contentLength = headers.contentLength().orElse(-1);
        this.contentType = headers.contentType().orElse(null);
        this.origin = headers.getOrigin().orElse(null);
    }

    public static NettyHttpRequest<?> createSafe(io.netty.handler.codec.http.HttpRequest request, ChannelHandlerContext ctx, ConversionService conversionService, NettyHttpServerConfiguration serverConfiguration) {
        try {
            return new NettyHttpRequest<>(
                request,
                ctx,
                conversionService,
                serverConfiguration
            );
        } catch (IllegalArgumentException iae) {
            // invalid URI
            if (request instanceof StreamedHttpRequest streamed) {
                streamed.closeIfNoSubscriber();
            } else {
                ((FullHttpRequest) request).release();
            }

            return new NettyHttpRequest<>(
                new DefaultFullHttpRequest(request.protocolVersion(), request.method(), "/", Unpooled.EMPTY_BUFFER),
                ctx,
                conversionService,
                serverConfiguration
            );
        }
    }

    /**
     * Get the initial body of this request. This is always a {@link ByteBody}. In most cases you
     * should use {@link #byteBody()} instead.
     *
     * @return The root body
     */
    public final ByteBody rootBody() {
        return body;
    }

    /**
     * Get the <i>last</i> byte body of this request, be it claimed or unclaimed. Basically, there
     * are two options: For buffered requests (rootBody is immediate), this is just the root body.
     * For streaming requests (rootBody is streaming), this can be that root body, or if someone
     * called {@link ByteBody#buffer} (and the buffering has completed), it can be the buffered
     * immediate body.<br>
     * The returned byte body may have been claimed already.
     *
     * @return The byte body of this request
     */
    public final ByteBody byteBody() {
        ByteBody byteBody = rootBody();
        HttpBody httpBody = byteBody;
        while (true) {
            HttpBody next = httpBody.next();
            if (next == null) {
                break;
            }
            httpBody = next;
            if (httpBody instanceof ByteBody bb) {
                byteBody = bb;
            }
        }
        return byteBody;
    }

    /**
     * Get the <i>last</i> body of this request, of any type. This is a weird method to use, avoid
     * it. It's sometimes necessary to "piggy-back" off other code that parses the body. For
     * example in {@link #getBody()}, we want to return whatever we can, even if the body has
     * already been claimed for a {@code @Body} parameter or form parsing or something. So we take
     * the last step in the parse chain and do our best with it.
     *
     * @return The last body of this request
     */
    public final HttpBody lastBody() {
        HttpBody body = rootBody();
        while (true) {
            HttpBody next = body.next();
            if (next == null) {
                break;
            }
            body = next;
        }
        return body;
    }

    public final void addRouteWaitsFor(ExecutionFlow<?> executionFlow) {
        routeWaitsFor = routeWaitsFor.then(() -> executionFlow);
    }

    public final ExecutionFlow<?> getRouteWaitsFor() {
        return routeWaitsFor;
    }

    public final FormRouteCompleter formRouteCompleter() {
        assert isFormOrMultipartData();
        if (formRouteCompleter == null) {
            formRouteCompleter = new FormRouteCompleter((RouteMatch<?>) getAttribute(HttpAttributes.ROUTE_MATCH).get(), getChannelHandlerContext().channel().eventLoop());
        }
        return formRouteCompleter;
    }

    public final boolean hasFormRouteCompleter() {
        return formRouteCompleter != null;
    }

    @Override
    public MutableHttpRequest<T> mutate() {
        return new NettyMutableHttpRequest();
    }

    @NonNull
    @Override
    public Optional<Object> getAttribute(CharSequence name) {
        return Optional.ofNullable(getAttributes().getValue(Objects.requireNonNull(name, "Name cannot be null").toString()));
    }

    @Override
    public HttpVersion getHttpVersion() {
        HttpPipelineBuilder.StreamPipeline pipeline = channelHandlerContext.channel().attr(HttpPipelineBuilder.STREAM_PIPELINE_ATTRIBUTE.get()).get();
        if (pipeline != null) {
            return pipeline.httpVersion;
        }
        return HttpVersion.HTTP_1_1;
    }

    @Override
    public String toString() {
        return getMethodName() + " " + getUri();
    }

    /**
     * @return Obtain a reference to the native Netty HTTP request
     */
    public io.netty.handler.codec.http.HttpRequest getNativeRequest() {
        return nettyRequest;
    }

    /**
     * @return The {@link ChannelHandlerContext}
     */
    public ChannelHandlerContext getChannelHandlerContext() {
        return channelHandlerContext;
    }

    @Override
    public Cookies getCookies() {
        NettyCookies cookies = this.nettyCookies;
        if (cookies == null) {
            synchronized (this) { // double check
                cookies = this.nettyCookies;
                if (cookies == null) {
                    cookies = new NettyCookies(getPath(), headers.getNettyHeaders(), conversionService);
                    this.nettyCookies = cookies;
                }
            }
        }
        return cookies;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return (InetSocketAddress) getChannelHandlerContext()
            .channel()
            .remoteAddress();
    }

    @Override
    public InetSocketAddress getServerAddress() {
        return (InetSocketAddress) getChannelHandlerContext()
            .channel()
            .localAddress();
    }

    @Override
    public String getServerName() {
        return getServerAddress().getHostName();
    }

    @Override
    public boolean isSecure() {
        ChannelHandlerContext channelHandlerContext = getChannelHandlerContext();
        return channelHandlerContext.pipeline().get(SslHandler.class) != null;
    }

    @Override
    public Optional<String> getOrigin() {
        if (headersMutated) {
            return getHeaders().getOrigin();
        } else {
            return Optional.ofNullable(origin);
        }
    }

    @Override
    public HttpHeaders getHeaders() {
        return headers;
    }

    @Override
    public MutableConvertibleValues<Object> getAttributes() {
        MutableConvertibleValues<Object> attributes = this.attributes;
        if (attributes == null) {
            synchronized (this) { // double check
                attributes = this.attributes;
                if (attributes == null) {
                    attributes = new MutableConvertibleValuesMap<>(new HashMap<>(8));
                    this.attributes = attributes;
                }
            }
        }
        return attributes;
    }

    @Override
    public HttpRequest<T> setAttribute(CharSequence name, Object value) {
        // This is the copy from the super method to avoid the type pollution
        if (StringUtils.isNotEmpty(name)) {
            if (value == null) {
                getAttributes().remove(name.toString());
            } else {
                getAttributes().put(name.toString(), value);
            }
        }
        return this;
    }

    @Override
    public Optional<Certificate> getCertificate() {
        Supplier<Certificate> sup = channelHandlerContext.channel().attr(HttpPipelineBuilder.CERTIFICATE_SUPPLIER_ATTRIBUTE.get()).get();
        return sup == null ? Optional.empty() : Optional.ofNullable(sup.get());
    }

    @Override
    public Optional<T> getBody() {
        HttpBody lastBody = lastBody();
        if (lastBody instanceof ImmediateMultiObjectBody multi) {
            lastBody = multi.single(serverConfiguration.getDefaultCharset(), channelHandlerContext.alloc());
        }
        if (lastBody instanceof ImmediateSingleObjectBody single) {
            //noinspection unchecked
            return (Optional<T>) Optional.ofNullable(single.valueUnclaimed());
        } else if (lastBody instanceof FormRouteCompleter frc) {
            //noinspection unchecked
            return (Optional<T>) Optional.of(frc.asMap(serverConfiguration.getDefaultCharset()));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public <T1> Optional<T1> getBody(Class<T1> type) {
        return getBody(Argument.of(type));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T1> Optional<T1> getBody(ArgumentConversionContext<T1> conversionContext) {
        return getBody().flatMap(t -> bodyConvertor.convert(conversionContext, t));
    }

    /**
     * Release and cleanup resources.
     */
    @Internal
    public void release() {
        RouteMatch<?> routeMatch = (RouteMatch<?>) getAttribute(HttpAttributes.ROUTE_MATCH).orElse(null);
        if (routeMatch != null) {
            // discard parameters that have already been bound
            for (Object toDiscard : routeMatch.getVariableValues().values()) {
                if (toDiscard instanceof io.micronaut.core.io.buffer.ReferenceCounted rc) {
                    rc.release();
                }
                if (toDiscard instanceof io.netty.util.ReferenceCounted rc) {
                    rc.release();
                }
                if (toDiscard instanceof NettyCompletedFileUpload fu) {
                    fu.discard();
                }
            }
        }
        body.release();
        if (attributes != null) {
            attributes.values().forEach(this::releaseIfNecessary);
        }
    }

    /**
     * @param value An object with a value
     */
    protected void releaseIfNecessary(Object value) {
        if (value instanceof ReferenceCounted referenceCounted) {
            int i = referenceCounted.refCnt();
            if (i != 0) {
                referenceCounted.release();
            }
        }
    }

    @Nullable
    private ChannelHandlerContext findConnectionHandler() {
        ChannelHandlerContext current = channelHandlerContext.pipeline().context(Http2ConnectionHandler.class);
        if (current != null) {
            return current;
        }
        Channel parentChannel = channelHandlerContext.channel().parent();
        if (parentChannel != null) {
            return parentChannel.pipeline().context(Http2FrameCodec.class);
        }
        return null;
    }

    @Override
    public boolean isServerPushSupported() {
        ChannelHandlerContext http2ConnectionHandlerContext = findConnectionHandler();
        return http2ConnectionHandlerContext != null && ((Http2ConnectionHandler) http2ConnectionHandlerContext.handler()).connection().remote().allowPushTo();
    }

    @Override
    public PushCapableHttpRequest<T> serverPush(@NonNull HttpRequest<?> request) {
        ChannelHandlerContext connectionHandlerContext = findConnectionHandler();
        if (connectionHandlerContext != null) {
            Http2ConnectionHandler connectionHandler = (Http2ConnectionHandler) connectionHandlerContext.handler();

            if (!connectionHandler.connection().remote().allowPushTo()) {
                throw new UnsupportedOperationException("Server push not supported by this client: Client is HTTP2 but does not report support for this feature");
            }

            URI configuredUri = request.getUri();
            String scheme = configuredUri.getScheme();
            if (scheme == null) {
                scheme = channelHandlerContext.channel().parent().pipeline().get(SslHandler.class) == null ? SCHEME_HTTP : SCHEME_HTTPS;
            }
            String authority = configuredUri.getAuthority();
            if (authority == null) {
                // this is potentially user-controlled.
                authority = this.getHeaders().get("Host");
            }
            String path = configuredUri.getPath();
            if (path == null || !path.startsWith("/")) {
                throw new IllegalArgumentException("Request must have an absolute path");
            }
            String query = configuredUri.getQuery();
            String fragment = configuredUri.getFragment();

            URI fixedUri;
            try {
                fixedUri = new URI(scheme, authority, path, query, fragment);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Illegal URI", e);
            }

            // request used to trigger our handlers
            io.netty.handler.codec.http.HttpRequest inboundRequest = NettyHttpRequestBuilder.asBuilder(request).toHttpRequestWithoutBody();

            // copy headers from our request
            for (Iterator<Map.Entry<CharSequence, CharSequence>> itr = headers.getNettyHeaders().iteratorCharSequence(); itr.hasNext(); ) {
                Map.Entry<CharSequence, CharSequence> entry = itr.next();
                if (!inboundRequest.headers().contains(entry.getKey()) && !SERVER_PUSH_EXCLUDE_HEADERS.contains(entry.getKey())) {
                    inboundRequest.headers().add(entry.getKey(), entry.getValue());
                }
            }
            if (!inboundRequest.headers().contains(HttpHeaderNames.REFERER)) {
                inboundRequest.headers().add(HttpHeaderNames.REFERER, getUri().toString());
            }

            // request used to compute the headers for the PUSH_PROMISE frame
            io.netty.handler.codec.http.HttpRequest outboundRequest = new DefaultHttpRequest(
                inboundRequest.protocolVersion(),
                inboundRequest.method(),
                fixedUri.toString(),
                inboundRequest.headers()
            );

            int ourStream = ((Http2StreamChannel) channelHandlerContext.channel()).stream().id();
            HttpPipelineBuilder.StreamPipeline originalStreamPipeline = channelHandlerContext.channel().attr(HttpPipelineBuilder.STREAM_PIPELINE_ATTRIBUTE.get()).get();

            new Http2StreamChannelBootstrap(channelHandlerContext.channel().parent())
                .handler(new ChannelInitializer<Http2StreamChannel>() {
                    @Override
                    protected void initChannel(@NonNull Http2StreamChannel ch) throws Exception {
                        int newStream = ch.stream().id();

                        channelHandlerContext.write(new DefaultHttp2PushPromiseFrame(HttpConversionUtil.toHttp2Headers(outboundRequest, false))
                            .stream(((Http2StreamChannel) channelHandlerContext.channel()).stream())
                            .pushStream(ch.stream()));

                        originalStreamPipeline.initializeChildPipelineForPushPromise(ch);

                        inboundRequest.headers().setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), newStream);
                        inboundRequest.headers().setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_PROMISE_ID.text(), ourStream);

                        // delay until our handling is complete
                        connectionHandlerContext.executor().execute(() -> {
                            try {
                                ch.pipeline().context(ChannelPipelineCustomizer.HANDLER_HTTP_DECODER).fireChannelRead(inboundRequest);
                            } catch (Exception e) {
                                LOG.warn("Failed to complete push promise", e);
                            }
                        });
                    }
                })
                .open()
                .addListener((GenericFutureListener<Future<Http2StreamChannel>>) future -> {
                    try {
                        future.sync();
                    } catch (Exception e) {
                        if (e instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        }
                        LOG.warn("Failed to complete push promise", e);
                    }
                });
            return this;
        } else {
            throw new UnsupportedOperationException("Server push not supported by this client: Not a HTTP2 client");
        }
    }

    @Override
    protected Charset initCharset(Charset characterEncoding) {
        return characterEncoding == null ? serverConfiguration.getDefaultCharset() : characterEncoding;
    }

    /**
     * @return Return true if the request is form data.
     */
    @Internal
    public final boolean isFormOrMultipartData() {
        MediaType ct = getContentType().orElse(null);
        return ct != null && (ct.equals(MediaType.APPLICATION_FORM_URLENCODED_TYPE) || ct.equals(MediaType.MULTIPART_FORM_DATA_TYPE));
    }

    @Override
    @Deprecated
    public io.netty.handler.codec.http.HttpRequest toHttpRequest() {
        return toHttpRequestWithoutBody();
    }

    @Override
    public Optional<io.netty.handler.codec.http.HttpRequest> toHttpRequestDirect() {
        return Optional.of(byteBody().claimForReuse(nettyRequest));
    }

    @Override
    public io.netty.handler.codec.http.HttpRequest toHttpRequestWithoutBody() {
        if (nettyRequest instanceof FullHttpRequest) {
            // do not include body, the body is owned by us
            DefaultHttpRequest copy = new DefaultHttpRequest(
                nettyRequest.protocolVersion(),
                nettyRequest.method(),
                nettyRequest.uri(),
                nettyRequest.headers()
            );
            copy.setDecoderResult(nettyRequest.decoderResult());
            return copy;
        }
        return nettyRequest;
    }

    @Override
    public Optional<MediaType> getContentType() {
        // this is better than the caching we can do in AbstractNettyHttpRequest
        if (headersMutated) {
            return headers.contentType();
        } else {
            return Optional.ofNullable(contentType);
        }
    }

    /**
     * Remove the current request from the context.
     *
     * @param ctx The context
     * @return The request or null if it is not present
     */
    static NettyHttpRequest remove(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();

        io.netty.util.Attribute<NettyHttpRequest> attr = channel.attr(ServerAttributeKeys.REQUEST_KEY);
        return attr.getAndSet(null);
    }

    private BodyConvertor newBodyConvertor() {
        return new BodyConvertor() {

            @Override
            public Optional convert(ArgumentConversionContext conversionContext, Object value) {
                if (value == null) {
                    return Optional.empty();
                }
                if (Argument.OBJECT_ARGUMENT.equalsType(conversionContext.getArgument())) {
                    return Optional.of(value);
                }
                return convertFromNext(conversionService, conversionContext, value);
            }

        };
    }

    @Override
    public long getContentLength() {
        if (headersMutated) {
            return super.getContentLength();
        } else {
            return contentLength;
        }
    }

    @Override
    public boolean isFull() {
        return byteBody() instanceof ImmediateByteBody;
    }

    @Override
    public ByteBuffer<?> contents() {
        if (byteBody() instanceof ImmediateByteBody immediateByteBody) {
            return toByteBuffer(immediateByteBody);
        }
        return null;
    }

    @Override
    public ExecutionFlow<ByteBuffer<?>> bufferContents() {
        return byteBody().buffer(getChannelHandlerContext().alloc()).map(NettyHttpRequest::toByteBuffer);
    }

    private static ByteBuffer<ByteBuf> toByteBuffer(ImmediateByteBody immediateByteBody) {
        // use delegate because we don't want to implement ReferenceCounted
        return new DelegateByteBuffer<>(NettyByteBufferFactory.DEFAULT.wrap(immediateByteBody.contentUnclaimed()));
    }

    /**
     * Mutable version of the request.
     */
    private final class NettyMutableHttpRequest implements MutableHttpRequest<T>, NettyHttpRequestBuilder {

        private URI uri = NettyHttpRequest.this.uri;
        @Nullable
        private MutableHttpParameters httpParameters;
        @Nullable
        private Object body;

        @Override
        public void setConversionService(ConversionService conversionService) {
            if (httpParameters != null) {
                httpParameters.setConversionService(conversionService);
            }
        }

        @Override
        public MutableHttpRequest<T> cookie(Cookie cookie) {
            if (cookie instanceof NettyCookie) {
                NettyCookie nettyCookie = (NettyCookie) cookie;
                String value = ClientCookieEncoder.LAX.encode(nettyCookie.getNettyCookie());
                headers.add(HttpHeaderNames.COOKIE, value);
            }
            return this;
        }

        @Override
        public MutableHttpRequest<T> uri(URI uri) {
            this.uri = uri;
            if (uri.getQuery() != null) {
                // have to re-initialize parameters
                this.httpParameters = null;
            }
            return this;
        }

        @Override
        public <T1> MutableHttpRequest<T1> body(T1 body) {
            this.body = body;
            return (MutableHttpRequest<T1>) this;
        }

        @Override
        public MutableHttpHeaders getHeaders() {
            headersMutated = true;
            return headers;
        }

        @NonNull
        @Override
        public MutableConvertibleValues<Object> getAttributes() {
            return NettyHttpRequest.this.getAttributes();
        }

        @NonNull
        @Override
        public Optional<T> getBody() {
            if (body != null) {
                return Optional.of((T) body);
            }
            return NettyHttpRequest.this.getBody();
        }

        @NonNull
        @Override
        public Cookies getCookies() {
            return NettyHttpRequest.this.getCookies();
        }

        @Override
        public MutableHttpParameters getParameters() {
            MutableHttpParameters httpParameters = this.httpParameters;
            if (httpParameters == null) {
                synchronized (this) { // double check
                    httpParameters = this.httpParameters;
                    if (httpParameters == null) {
                        QueryStringDecoder queryStringDecoder = createDecoder(uri);
                        httpParameters = new NettyHttpParameters(queryStringDecoder.parameters(), conversionService, null);
                        this.httpParameters = httpParameters;
                    }
                }
            }
            return httpParameters;
        }

        @NonNull
        @Override
        public HttpMethod getMethod() {
            return NettyHttpRequest.this.getMethod();
        }

        @NonNull
        @Override
        public URI getUri() {
            if (uri != null) {
                return uri;
            }
            return NettyHttpRequest.this.getUri();
        }

        @NonNull
        @Override
        @Deprecated
        public io.netty.handler.codec.http.FullHttpRequest toFullHttpRequest() {
            io.netty.handler.codec.http.HttpRequest nr = NettyHttpRequest.this.nettyRequest;
            if (nr instanceof io.netty.handler.codec.http.FullHttpRequest) {
                return (io.netty.handler.codec.http.FullHttpRequest) NettyHttpRequest.this.nettyRequest;
            } else {
                return new DefaultFullHttpRequest(
                    nr.protocolVersion(),
                    nr.method(),
                    nr.uri(),
                    Unpooled.EMPTY_BUFFER,
                    nr.headers(),
                    EmptyHttpHeaders.INSTANCE
                );
            }
        }

        @NonNull
        @Override
        @Deprecated
        public StreamedHttpRequest toStreamHttpRequest() {
            if (isStream()) {
                return (StreamedHttpRequest) NettyHttpRequest.this.nettyRequest;
            } else {
                io.netty.handler.codec.http.FullHttpRequest fullHttpRequest = toFullHttpRequest();
                DefaultStreamedHttpRequest request = new DefaultStreamedHttpRequest(
                    fullHttpRequest.protocolVersion(),
                    fullHttpRequest.method(),
                    fullHttpRequest.uri(),
                    true,
                    Publishers.just(new DefaultLastHttpContent(fullHttpRequest.content()))
                );
                request.headers().setAll(fullHttpRequest.headers());
                return request;
            }
        }

        @NonNull
        @Override
        @Deprecated
        public io.netty.handler.codec.http.HttpRequest toHttpRequest() {
            if (isStream()) {
                return toStreamHttpRequest();
            }
            return toFullHttpRequest();
        }

        @Override
        @Deprecated
        public boolean isStream() {
            return NettyHttpRequest.this.nettyRequest instanceof StreamedHttpRequest;
        }

        @Override
        public MutableHttpRequest<T> mutate() {
            return new NettyMutableHttpRequest();
        }

        @Override
        public io.netty.handler.codec.http.HttpRequest toHttpRequestWithoutBody() {
            return NettyHttpRequest.this.toHttpRequestWithoutBody();
        }

        @Override
        public Optional<io.netty.handler.codec.http.HttpRequest> toHttpRequestDirect() {
            return body != null ? Optional.empty() : NettyHttpRequest.this.toHttpRequestDirect();
        }
    }

    private abstract static class BodyConvertor<T> {

        private BodyConvertor<T> nextConvertor;

        public abstract Optional<T> convert(ArgumentConversionContext<T> conversionContext, T value);

        protected synchronized Optional<T> convertFromNext(ConversionService conversionService, ArgumentConversionContext<T> conversionContext, T value) {
            if (nextConvertor == null) {
                Optional<T> conversion = conversionService.convert(value, conversionContext);
                nextConvertor = new BodyConvertor<T>() {

                    @Override
                    public Optional<T> convert(ArgumentConversionContext<T> currentConversionContext, T value) {
                        if (currentConversionContext == conversionContext) {
                            return conversion;
                        }
                        if (currentConversionContext.getArgument().equalsType(conversionContext.getArgument())) {
                            conversionContext.getLastError().ifPresent(error -> {
                                error.getOriginalValue().ifPresentOrElse(
                                    originalValue -> currentConversionContext.reject(originalValue, error.getCause()),
                                    () -> currentConversionContext.reject(error.getCause())
                                );
                            });
                            return conversion;
                        }
                        return convertFromNext(conversionService, currentConversionContext, value);
                    }

                };
                return conversion;
            }
            return nextConvertor.convert(conversionContext, value);
        }

        public void cleanup() {
            nextConvertor = null;
        }

    }

}
