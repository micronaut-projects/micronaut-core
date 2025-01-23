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
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
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
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.InternalByteBody;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.Cookies;
import io.micronaut.http.netty.AbstractNettyHttpRequest;
import io.micronaut.http.netty.NettyHttpHeaders;
import io.micronaut.http.netty.NettyHttpParameters;
import io.micronaut.http.netty.NettyHttpRequestBuilder;
import io.micronaut.http.netty.body.AvailableNettyByteBody;
import io.micronaut.http.netty.body.NettyByteBody;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.http.netty.cookies.NettyCookies;
import io.micronaut.http.netty.stream.DefaultStreamedHttpRequest;
import io.micronaut.http.netty.stream.DelegateStreamedHttpRequest;
import io.micronaut.http.netty.stream.StreamedHttpRequest;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.netty.handler.Http2ServerHandler;
import io.micronaut.http.server.netty.multipart.NettyCompletedFileUpload;
import io.micronaut.web.router.DefaultUriRouteMatch;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.RouteMatch;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpContent;
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
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSession;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
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
public final class NettyHttpRequest<T> extends AbstractNettyHttpRequest<T> implements HttpRequest<T>, PushCapableHttpRequest<T>, io.micronaut.http.FullHttpRequest<T>, ServerHttpRequest<T> {
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

    /**
     * ONLY for NettyBodyAnnotationBinder use.
     */
    @Internal
    @SuppressWarnings("VisibilityModifier")
    public ArgumentBinder.BindingResult<ConvertibleValues<?>> convertibleBody;

    private final NettyHttpHeaders headers;
    private final ChannelHandlerContext channelHandlerContext;
    private final HttpServerConfiguration serverConfiguration;
    private MutableConvertibleValues<Object> attributes;
    private NettyCookies nettyCookies;
    private final CloseableByteBody body;
    @Nullable
    private FormRouteCompleter formRouteCompleter;
    private ExecutionFlow<?> routeWaitsFor = ExecutionFlow.just(null);
    private Object legacyBody;

    private final BodyConvertor bodyConvertor = newBodyConvertor();

    /**
     * @param nettyRequest        The {@link io.netty.handler.codec.http.HttpRequest}
     * @param body                The request body
     * @param ctx                 The {@link ChannelHandlerContext}
     * @param environment         The Environment
     * @param serverConfiguration The {@link HttpServerConfiguration}
     * @throws IllegalArgumentException When the request URI is invalid
     */
    @SuppressWarnings("MagicNumber")
    public NettyHttpRequest(io.netty.handler.codec.http.HttpRequest nettyRequest,
                            CloseableByteBody body,
                            ChannelHandlerContext ctx,
                            ConversionService environment,
                            HttpServerConfiguration serverConfiguration) throws IllegalArgumentException {
        super(nettyRequest, environment);
        Objects.requireNonNull(nettyRequest, "Netty request cannot be null");
        Objects.requireNonNull(ctx, "ChannelHandlerContext cannot be null");
        Objects.requireNonNull(environment, "Environment cannot be null");
        this.serverConfiguration = serverConfiguration;
        this.channelHandlerContext = ctx;
        this.headers = new NettyHttpHeaders(nettyRequest.headers(), conversionService);
        this.body = body;
    }

    @Override
    public ByteBody byteBody() {
        return body;
    }

    public void setLegacyBody(Object legacyBody) {
        this.legacyBody = legacyBody;
    }

    public void addRouteWaitsFor(ExecutionFlow<?> executionFlow) {
        routeWaitsFor = routeWaitsFor.then(() -> executionFlow);
    }

    public ExecutionFlow<?> getRouteWaitsFor() {
        return routeWaitsFor;
    }

    public FormRouteCompleter formRouteCompleter() {
        assert isFormOrMultipartData();
        if (formRouteCompleter == null) {
            formRouteCompleter = new FormRouteCompleter(RouteAttributes.getRouteMatch(this).get(), getChannelHandlerContext().channel().eventLoop());
        }
        return formRouteCompleter;
    }

    public boolean hasFormRouteCompleter() {
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
        // Http2ServerHandler case
        return findConnectionHandler() == null ? HttpVersion.HTTP_1_1 : HttpVersion.HTTP_2_0;
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
        return headers.getOrigin();
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
    public Optional<SSLSession> getSslSession() {
        Supplier<SSLSession> sup = channelHandlerContext.channel().attr(HttpPipelineBuilder.SSL_SESSION_ATTRIBUTE.get()).get();
        return sup == null ? Optional.empty() : Optional.ofNullable(sup.get());
    }

    @SuppressWarnings("unchecked")
    @Override
    public Optional<T> getBody() {
        if (hasFormRouteCompleter()) {
            return Optional.of((T) formRouteCompleter().asMap(serverConfiguration.getDefaultCharset()));
        } else {
            return Optional.ofNullable((T) legacyBody);
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
        body.close();
        if (formRouteCompleter != null) {
            formRouteCompleter.release();
        }
        if (attributes != null) {
            attributes.forEach(NettyHttpRequest::cleanup);
        }
    }

    private static void cleanup(String k, Object v) {
        //noinspection StringEquality
        if (k == HttpAttributes.ROUTE_MATCH.toString()) {
            // usually this is a DefaultUriRouteMatch, avoid scalability issues here
            RouteMatch<?> routeMatch = v instanceof DefaultUriRouteMatch<?, ?> urm ? urm : (RouteMatch<?>) v;
            if (routeMatch != null) {
                // discard parameters that have already been bound
                for (Object toDiscard : routeMatch.getVariableValues().values()) {
                    if (toDiscard instanceof io.micronaut.core.io.buffer.ReferenceCounted rc) {
                        rc.release();
                    }
                    if (toDiscard instanceof ReferenceCounted rc) {
                        rc.release();
                    }
                    if (toDiscard instanceof NettyCompletedFileUpload fu) {
                        fu.discard();
                    }
                }
            }
            // perf: avoid an instanceof in releaseIfNecessary
            return;
        }
        //noinspection StringEquality
        if (k == HttpAttributes.ROUTE_INFO.toString() || v instanceof String) {
            // perf: avoid an instanceof in releaseIfNecessary
            return;
        }
        releaseIfNecessary(v);
    }

    private static void releaseIfNecessary(Object value) {
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
        return http2ConnectionHandlerContext != null &&
            ((Http2ConnectionHandler) http2ConnectionHandlerContext.handler()).connection().remote().allowPushTo();
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
                scheme = connectionHandlerContext.channel().pipeline().get(SslHandler.class) == null ? SCHEME_HTTP : SCHEME_HTTPS;
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
            io.netty.handler.codec.http.HttpRequest inboundRequestNoBody = NettyHttpRequestBuilder.asBuilder(request).toHttpRequestWithoutBody();
            FullHttpRequest inboundRequest = new DefaultFullHttpRequest(
                inboundRequestNoBody.protocolVersion(),
                inboundRequestNoBody.method(),
                inboundRequestNoBody.uri(),
                Unpooled.EMPTY_BUFFER,
                inboundRequestNoBody.headers(),
                EmptyHttpHeaders.INSTANCE
            );

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
            Http2Headers outboundHeaders = HttpConversionUtil.toHttp2Headers(outboundRequest, false);

            if (channelHandlerContext.channel() instanceof Http2StreamChannel streamChannel) {
                int ourStream = streamChannel.stream().id();
                HttpPipelineBuilder.StreamPipeline originalStreamPipeline = channelHandlerContext.channel().attr(HttpPipelineBuilder.STREAM_PIPELINE_ATTRIBUTE.get()).get();

                new Http2StreamChannelBootstrap(channelHandlerContext.channel().parent())
                    .handler(new ChannelInitializer<Http2StreamChannel>() {
                        @Override
                        protected void initChannel(@NonNull Http2StreamChannel ch) throws Exception {
                            int newStream = ch.stream().id();

                            channelHandlerContext.write(new DefaultHttp2PushPromiseFrame(outboundHeaders)
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
            } else {
                int ourStreamId = headers.getNettyHeaders().getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text());
                if (channelHandlerContext.executor().inEventLoop()) {
                    serverPush0(connectionHandler, ourStreamId, inboundRequest, connectionHandlerContext, outboundHeaders);
                } else {
                    channelHandlerContext.executor().execute(() -> serverPush0(connectionHandler, ourStreamId, inboundRequest, connectionHandlerContext, outboundHeaders));
                }
            }
            return this;
        } else {
            throw new UnsupportedOperationException("Server push not supported by this client: Not a HTTP2 client");
        }
    }

    private static void serverPush0(Http2ConnectionHandler connectionHandler, int ourStreamId, FullHttpRequest inboundRequest, ChannelHandlerContext connectionHandlerContext, Http2Headers outboundHeaders) {
        try {
            Http2Stream ourStream = connectionHandler.connection().stream(ourStreamId);
            if (ourStream == null) {
                // this is a bit ugly. Ideally serverPush would return a Publisher that completes when the promise is sent
                throw new IllegalStateException("Push promise origin stream is already gone. " +
                    "This can happen if serverPush() is called outside the event loop, and the primary response is sent before the push promise. " +
                    "Please either call serverPush() on the event loop, or delay the primary response.");
            }
            int newStreamId = connectionHandler.connection().local().incrementAndGetNextStreamId();

            inboundRequest.headers().setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), newStreamId);
            inboundRequest.headers().setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_PROMISE_ID.text(), ourStreamId);

            connectionHandler.encoder().writePushPromise(connectionHandlerContext, ourStreamId, newStreamId, outboundHeaders, 0, connectionHandlerContext.newPromise())
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        try {
                            ((Http2ServerHandler.ConnectionHandler) connectionHandler).handleFakeRequest(connectionHandler.connection().stream(newStreamId), inboundRequest);
                        } catch (Exception e) {
                            LOG.warn("Failed to send push promise", e);
                        }
                    } else {
                        LOG.warn("Failed to send push promise", future.cause());
                    }
                });
        } catch (Exception e) {
            LOG.warn("Failed to send push promise", e);
        }
    }

    @Override
    protected Charset initCharset(Charset characterEncoding) {
        return characterEncoding == null ? serverConfiguration.getDefaultCharset() : characterEncoding;
    }

    @Override
    protected int getMaxParams() {
        return serverConfiguration.getMaxParams();
    }

    @Override
    protected boolean isSemicolonIsNormalChar() {
        return serverConfiguration.isSemicolonIsNormalChar();
    }

    /**
     * @return Return true if the request is form data.
     */
    @Internal
    public boolean isFormOrMultipartData() {
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
        return Optional.of(new DelegateStreamedHttpRequest(nettyRequest, NettyByteBody.toByteBufs(byteBody()).map(DefaultHttpContent::new)));
    }

    @Override
    public ByteBody byteBodyDirect() {
        return byteBody();
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
        return headers.contentType();
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
        return headers.contentLength().orElse(-1);
    }

    @Override
    public boolean isFull() {
        return byteBody() instanceof AvailableNettyByteBody;
    }

    @Override
    public ByteBuffer<?> contents() {
        if (byteBody() instanceof AvailableNettyByteBody immediate) {
            return toByteBuffer(immediate);
        }
        return null;
    }

    @Override
    public ExecutionFlow<ByteBuffer<?>> bufferContents() {
        return InternalByteBody.bufferFlow(byteBody()).map(c -> toByteBuffer((AvailableNettyByteBody) c));
    }

    private static ByteBuffer<ByteBuf> toByteBuffer(AvailableNettyByteBody immediateByteBody) {
        // use delegate because we don't want to implement ReferenceCounted
        return new DelegateByteBuffer<>(NettyByteBufferFactory.DEFAULT.wrap(immediateByteBody.peek()));
    }

    /**
     * Mutable version of the request.
     */
    private final class NettyMutableHttpRequest implements MutableHttpRequest<T>, NettyHttpRequestBuilder {

        private URI uri;
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
            if (cookie instanceof NettyCookie nettyCookie) {
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
                        QueryStringDecoder queryStringDecoder = createDecoder(getUri());
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

        @Override
        public ByteBody byteBodyDirect() {
            // if the body has been changed we can't return the byteBody directly
            return body != null ? null : NettyHttpRequest.this.byteBodyDirect();
        }
    }

    private abstract static class BodyConvertor<T> {

        private BodyConvertor<T> nextConvertor;

        public abstract Optional<T> convert(ArgumentConversionContext<T> conversionContext, T value);

        protected synchronized Optional<T> convertFromNext(ConversionService conversionService, ArgumentConversionContext<T> conversionContext, T value) {
            if (nextConvertor == null) {
                Optional<T> conversion = conversionService.convert(value, conversionContext);
                nextConvertor = new BodyConvertor<>() {

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

    }

}
