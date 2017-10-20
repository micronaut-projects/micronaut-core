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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCounted;
import org.particleframework.core.annotation.Internal;
import org.particleframework.core.convert.*;
import org.particleframework.core.convert.value.MutableConvertibleValues;
import org.particleframework.core.convert.value.MutableConvertibleValuesMap;
import org.particleframework.core.util.CollectionUtils;
import org.particleframework.http.*;
import org.particleframework.http.HttpHeaders;
import org.particleframework.http.HttpMethod;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.cookie.Cookies;
import org.particleframework.http.exceptions.ConnectionClosedException;
import org.particleframework.http.server.HttpServerConfiguration;
import org.particleframework.http.server.netty.cookies.NettyCookies;
import org.particleframework.web.router.RouteMatch;

import java.io.IOException;
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
    private final MutableConvertibleValues<Object> attributes;
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
    private Set<String> routeArgumentNames = Collections.emptySet();


    public NettyHttpRequest(io.netty.handler.codec.http.HttpRequest nettyRequest,
                            ChannelHandlerContext ctx,
                            ConversionService environment,
                            HttpServerConfiguration serverConfiguration) {
        Objects.requireNonNull(nettyRequest, "Netty request cannot be null");
        Objects.requireNonNull(ctx, "ChannelHandlerContext cannot be null");
        Objects.requireNonNull(environment, "Environment cannot be null");

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
    public MutableConvertibleValues<Object> getAttributes() {
        return this.attributes;
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
            releaseIfNecessary(byteBuf);
        }
        if(this.body != null && body instanceof ReferenceCounted) {
            ReferenceCounted body = (ReferenceCounted) this.body;
            releaseIfNecessary(body);
        }
        for (Map.Entry<String, Object> attribute : attributes) {
            Object value = attribute.getValue();
            releaseIfNecessary(value);
        }
    }

    protected void releaseIfNecessary(Object value) {
        if(value instanceof ReferenceCounted) {
            ReferenceCounted referenceCounted = (ReferenceCounted) value;
            if(referenceCounted.refCnt() != 0) {
                referenceCounted.release();
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
        this.routeArgumentNames = CollectionUtils.setOf(this.matchedRoute.getArgumentNames());
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
    void offer(HttpPostRequestDecoder postRequestDecoder) {
        if(postRequestDecoder != null && !routeArgumentNames.isEmpty()) {
            Object body = this.body;
            if (body == null) {
                synchronized (this) { // double check
                    body = this.body;
                    if (body == null) {
                        this.body = body = new MutableConvertibleValuesMap<Object>(new ConcurrentHashMap<>(routeArgumentNames.size())) {};
                    }
                }
            }

            if(body instanceof MutableConvertibleValues) {
                try {
                    while (postRequestDecoder.hasNext()) {
                        InterfaceHttpData interfaceHttpData = postRequestDecoder.next();
                        String name = interfaceHttpData.getName();
                        if(!routeArgumentNames.contains(name)) {
                            // discard non-required arguments
                            interfaceHttpData.release();
                            continue;
                        }

                        switch (interfaceHttpData.getHttpDataType()) {
                            case Attribute:
                                Attribute attribute = (Attribute) interfaceHttpData;

                                ((MutableConvertibleValues<Object>)body).put(attribute.getName(), attribute.getValue());
                                interfaceHttpData.release();
                                break;
                            case FileUpload:
                                FileUpload fileUpload = (FileUpload) interfaceHttpData;
                                if(fileUpload.isCompleted() && fileUpload.isInMemory()) {
                                    ((MutableConvertibleValues<Object>)body).put(fileUpload.getName(), fileUpload);
                                }
                                break;
                        }

                    }
                    InterfaceHttpData partialHttpData = postRequestDecoder.currentPartialHttpData();
                    // TODO: handle partial data and chunked data

                } catch (HttpPostRequestDecoder.EndOfDataDecoderException e) {
                    // ok, ignore
                } catch (IOException e) {
                    throw new ConnectionClosedException("Error reading request data: " + e.getMessage(), e);
                }
            }
            else {
                postRequestDecoder.destroy();
            }
        }

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
