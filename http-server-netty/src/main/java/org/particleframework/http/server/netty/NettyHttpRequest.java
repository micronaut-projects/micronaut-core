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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.util.AttributeKey;
import org.particleframework.core.annotation.Internal;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.http.*;
import org.particleframework.http.HttpHeaders;
import org.particleframework.http.HttpMethod;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.cookie.Cookies;
import org.particleframework.http.server.HttpServerConfiguration;
import org.particleframework.http.server.netty.cookies.NettyCookies;
import org.particleframework.web.router.RouteMatch;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.*;
import java.util.function.Function;

/**
 * Delegates to the Netty {@link io.netty.handler.codec.http.HttpRequest} instance
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class NettyHttpRequest<T> implements HttpRequest<T> {

    public static final AttributeKey<NettyHttpRequest> KEY = AttributeKey.valueOf(NettyHttpRequest.class.getSimpleName());

    private final io.netty.handler.codec.http.HttpRequest nettyRequest;
    private final HttpMethod httpMethod;
    private final URI uri;
    private final NettyHttpRequestHeaders headers;
    private final ChannelHandlerContext channelHandlerContext;
    private final HttpServerConfiguration serverConfiguration;
    private final ConversionService<?> conversionService;
    private final Map<Class, Optional> convertedBodies = new LinkedHashMap<>(1);
    private NettyHttpParameters httpParameters;
    private NettyCookies nettyCookies;
    private Locale locale;
    private URI path;
    private List<ByteBuf> receivedContent  = new ArrayList<>();
    private Object body;

    private MediaType mediaType;
    private Charset charset;
    private RouteMatch<Object> matchedRoute;
    private boolean bodyRequired;
    private boolean nonBlockingBinderRegistered;


    public NettyHttpRequest(io.netty.handler.codec.http.HttpRequest nettyRequest,
                            ChannelHandlerContext ctx,
                            ConversionService conversionService,
                            HttpServerConfiguration serverConfiguration) {
        Objects.requireNonNull(nettyRequest, "Netty request cannot be null");
        Objects.requireNonNull(conversionService, "ConversionService cannot be null");
        this.serverConfiguration = serverConfiguration;
        this.conversionService = conversionService;
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

    /**
     * @return The received content as an array of {@link ByteBuf}
     */
    public ByteBuf[] getReceivedContent() {
        return receivedContent.toArray(new ByteBuf[receivedContent.size()]);
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
    public Locale getLocale() {
        Locale locale = this.locale;
        if (locale == null) {
            synchronized (this) { // double check
                locale = this.locale;
                if (locale == null) {
                    this.locale = locale = HttpRequest.super.getLocale();
                }
            }
        }
        return locale;
    }

    @Override
    public Cookies getCookies() {
        NettyCookies cookies = this.nettyCookies;
        if (cookies == null) {
            synchronized (this) { // double check
                cookies = this.nettyCookies;
                if (cookies == null) {
                    this.nettyCookies = cookies = new NettyCookies(headers.nettyHeaders, headers.conversionService);
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
    public T getBody() {
        Object body = this.body;
        if(body == null && !receivedContent.isEmpty()) {
            this.body = body = Unpooled.unmodifiableBuffer(getReceivedContent());
        }
        return (T)body;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T1> Optional<T1> getBody(Class<T1> type) {
        T body = getBody();
        if(body == null) {
            return Optional.empty();
        }
        return convertedBodies.computeIfAbsent(type, aClass -> conversionService.convert(body, aClass));
    }

    /**
     * Release and cleanup resources
     */
    @Internal
    public void release() {
        for (ByteBuf byteBuf : receivedContent) {
            if(byteBuf.refCnt() != 0) {
                byteBuf.release();
            }
        }
        if(this.body != null && body instanceof ByteBuf) {
            ByteBuf body = (ByteBuf) this.body;
            if(body.refCnt() != 0) {
                body.release();
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
    void addContent(HttpContent httpContent) {
        ByteBuf byteBuf = httpContent
                .content()
                .touch();
        int contentBytes = byteBuf.readableBytes();
        if (contentBytes == 0) {
            byteBuf.release();
        } else {
            receivedContent.add(byteBuf);
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

    @Internal
    void setPostRequestDecoder(HttpPostRequestDecoder postRequestDecoder) {
        NettyHttpParameters parameters = (NettyHttpParameters) getParameters();
        parameters.setPostRequestDecoder(postRequestDecoder);
    }

    @Internal
    void setNonBlockingBinderRegistered(boolean nonBlockingBinderRegistered) {
        this.nonBlockingBinderRegistered = nonBlockingBinderRegistered;
    }
    @Internal
    boolean isNonBlockingBinderRegistered() {
        return nonBlockingBinderRegistered;
    }

    public static NettyHttpRequest lookup(ChannelHandlerContext ctx) {
        return ctx.channel().attr(NettyHttpRequest.KEY).get();
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


}
