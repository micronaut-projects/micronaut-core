/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.particleframework.http.server.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.HttpData;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AttributeKey;
import io.netty.util.DefaultAttributeMap;
import io.netty.util.ReferenceCounted;
import org.particleframework.core.annotation.Internal;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.value.MutableConvertibleValues;
import org.particleframework.core.convert.value.MutableConvertibleValuesMap;
import org.particleframework.http.*;
import org.particleframework.http.cookie.Cookies;
import org.particleframework.http.server.HttpServerConfiguration;
import org.particleframework.http.server.netty.cookies.NettyCookies;
import org.particleframework.web.router.RouteMatch;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Delegates to the Netty {@link io.netty.handler.codec.http.HttpRequest} instance
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class NettyHttpRequest<T> extends DefaultAttributeMap implements HttpRequest<T> {

    public static final AttributeKey<NettyHttpRequest> KEY = AttributeKey.valueOf(NettyHttpRequest.class.getSimpleName());

    private final io.netty.handler.codec.http.HttpRequest nettyRequest;
    private final HttpMethod httpMethod;
    private final URI uri;
    private final NettyHttpRequestHeaders headers;
    private final ChannelHandlerContext channelHandlerContext;
    private final HttpServerConfiguration serverConfiguration;
    private final ConversionService<?> conversionService;
    private final Map<Class, Optional> convertedBodies = new LinkedHashMap<>(1);
    private final MutableConvertibleValues<Object> attributes;
    private NettyHttpParameters httpParameters;
    private NettyCookies nettyCookies;
    private Locale locale;
    private URI path;
    private List<ByteBufHolder> receivedContent = new ArrayList<>();

    private Object body;
    private MediaType mediaType;
    private Charset charset;
    private RouteMatch<Object> matchedRoute;
    private boolean bodyRequired;


    public NettyHttpRequest(io.netty.handler.codec.http.HttpRequest nettyRequest,
                            ChannelHandlerContext ctx,
                            ConversionService environment,
                            HttpServerConfiguration serverConfiguration) {
        Objects.requireNonNull(nettyRequest, "Netty request cannot be null");
        Objects.requireNonNull(ctx, "ChannelHandlerContext cannot be null");
        Objects.requireNonNull(environment, "Environment cannot be null");
        Channel channel = ctx.channel();
        if(channel != null) {
            channel.attr(KEY).set(this);
        }
        this.serverConfiguration = serverConfiguration;
        this.conversionService = environment;
        this.attributes = new MutableConvertibleValuesMap<>(new ConcurrentHashMap<>(4), conversionService);
        this.channelHandlerContext = ctx;
        this.nettyRequest = nettyRequest;
        this.httpMethod = HttpMethod.valueOf(nettyRequest.method().name());
        String fullUri = nettyRequest.uri();
        this.uri = URI.create(fullUri);
        this.headers = new NettyHttpRequestHeaders(nettyRequest.headers(), conversionService);
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
    public Charset getCharacterEncoding() {
        Charset charset = this.charset;
        if (charset == null) {
            synchronized (this) { // double check
                charset = this.charset;
                if (charset == null) {
                    this.charset = charset = initCharset();
                }
            }
        }
        return charset;
    }

    @Override
    public MediaType getContentType() {
        MediaType contentType = this.mediaType;
        if (contentType == null) {
            synchronized (this) { // double check
                contentType = this.mediaType;
                if (contentType == null) {
                    this.mediaType = contentType = HttpRequest.super.getContentType();
                }
            }
        }
        return contentType;
    }

    @Override
    public Optional<Locale> getLocale() {
        Locale locale = this.locale;
        if (locale == null) {
            synchronized (this) { // double check
                locale = this.locale;
                if (locale == null) {
                    this.locale = locale = HttpRequest.super.getLocale().orElse(null);
                }
            }
        }
        return Optional.ofNullable(locale);
    }

    @Override
    public Cookies getCookies() {
        NettyCookies cookies = this.nettyCookies;
        if (cookies == null) {
            synchronized (this) { // double check
                cookies = this.nettyCookies;
                if (cookies == null) {
                    this.nettyCookies = cookies = new NettyCookies(getPath(), headers.nettyHeaders, headers.conversionService);
                }
            }
        }
        return cookies;
    }

    @Override
    public HttpParameters getParameters() {
        NettyHttpParameters httpParameters = this.httpParameters;
        if (httpParameters == null) {
            synchronized (this) { // double check
                httpParameters = this.httpParameters;
                if (httpParameters == null) {
                    this.httpParameters = httpParameters = decodeParameters(nettyRequest.uri());
                }
            }
        }
        return httpParameters;
    }

    @Override
    public HttpMethod getMethod() {
        return httpMethod;
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
    public URI getUri() {
        return this.uri;
    }

    @Override
    public URI getPath() {
        URI path = this.path;
        if (path == null) {
            synchronized (this) { // double check
                path = this.path;
                if (path == null) {
                    this.path = path = decodePath(nettyRequest.uri());
                }
            }
        }
        return path;
    }

    @Override
    public HttpHeaders getHeaders() {
        return headers;
    }

    @Override
    public MutableConvertibleValues<Object> getAttributes() {
        return this.attributes;
    }

    @Override
    public T getBody() {
        Object body = this.body;
        if (body == null && !receivedContent.isEmpty()) {
            this.body = body = buildBody();
        }
        return (T) body;
    }

    protected CompositeByteBuf buildBody() {
        int size = receivedContent.size();
        CompositeByteBuf byteBufs = channelHandlerContext.alloc().compositeBuffer(size);
        for (ByteBufHolder holder : receivedContent) {
            ByteBuf content = holder.content();
            byteBufs.addComponent(true, content);
        }
        return byteBufs;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T1> Optional<T1> getBody(Class<T1> type) {
        T body = getBody();
        if (body == null) {
            return Optional.empty();
        }
        return convertedBodies.computeIfAbsent(type, aClass -> conversionService.convert(body, aClass));
    }

    /**
     * Release and cleanup resources
     */
    @Internal
    public void release() {
        for (ByteBufHolder byteBuf : receivedContent) {
            releaseIfNecessary(byteBuf);
        }
        if (this.body != null && body instanceof ReferenceCounted) {
            ReferenceCounted body = (ReferenceCounted) this.body;
            releaseIfNecessary(body);
        }
        for (Map.Entry<String, Object> attribute : attributes) {
            Object value = attribute.getValue();
            releaseIfNecessary(value);
        }
    }

    protected void releaseIfNecessary(Object value) {
        if (value instanceof ReferenceCounted) {
            ReferenceCounted referenceCounted = (ReferenceCounted) value;
            if((!(value instanceof CompositeByteBuf))) {
                int i = referenceCounted.refCnt();
                if (i != 0) {
                    referenceCounted.release();
                }
            }
        }
    }

    /**
     * Sets the body
     *
     * @param body The body to set
     */
    @Internal
    public void setBody(T body) {
        this.body = body;
        this.convertedBodies.clear();
    }

    /**
     * @return Obtains the matched route
     */
    @Internal
    public RouteMatch<Object> getMatchedRoute() {
        return matchedRoute;
    }


    @Internal
    void addContent(ByteBufHolder httpContent) {
        if(httpContent instanceof HttpData) {
            // TODO: handle binding attributes
        }
        else {
            receivedContent.add(httpContent);
        }
    }

    @Internal
    void setMatchedRoute(RouteMatch<Object> matchedRoute) {
        this.matchedRoute = matchedRoute;
    }

    @Internal
    void setBodyRequired(boolean bodyRequired) {
        this.bodyRequired = bodyRequired;
    }

    @Internal
    boolean isBodyRequired() {
        return bodyRequired || HttpMethod.requiresRequestBody(getMethod());
    }

    private URI decodePath(String uri) {
        QueryStringDecoder queryStringDecoder = createDecoder(uri);
        return URI.create(queryStringDecoder.path());
    }

    private NettyHttpParameters decodeParameters(String uri) {
        QueryStringDecoder queryStringDecoder = createDecoder(uri);
        return new NettyHttpParameters(queryStringDecoder.parameters(), headers.conversionService);
    }

    private QueryStringDecoder createDecoder(String uri) {
        Charset charset = getCharacterEncoding();
        return charset != null ? new QueryStringDecoder(uri, charset) : new QueryStringDecoder(uri);
    }

    private Charset initCharset() {
        Charset characterEncoding = HttpRequest.super.getCharacterEncoding();
        return characterEncoding == null ? serverConfiguration.getDefaultCharset() : characterEncoding;
    }

    /**
     * Lookup the current request from the context
     * @param ctx The context
     * @return The request or null if it is not present
     */
    public static NettyHttpRequest get(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        io.netty.util.Attribute<NettyHttpRequest> attr = channel.attr(KEY);
        return attr.get();
    }

    /**
     * Lookup the current request from the context
     * @param ctx The context
     * @return The request or null if it is not present
     */
    public static NettyHttpRequest current(ChannelHandlerContext ctx) {
        NettyHttpRequest current = get(ctx);
        if(current == null) throw new IllegalStateException("Current request not present");
        return current;
    }
}
