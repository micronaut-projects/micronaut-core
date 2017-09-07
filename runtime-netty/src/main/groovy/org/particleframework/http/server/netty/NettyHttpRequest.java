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
import io.netty.util.AttributeKey;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.http.*;
import org.particleframework.http.HttpHeaders;
import org.particleframework.http.HttpMethod;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.cookie.Cookies;
import org.particleframework.http.server.HttpServerConfiguration;
import org.particleframework.http.server.netty.cookies.NettyCookies;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.*;

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
    private final NettyHttpRequestContext requestContext;
    private NettyHttpParameters httpParameters;
    private NettyCookies nettyCookies;
    private Locale locale;
    private URI path;
    private List<ByteBuf> receivedContent  = new ArrayList<>();
    private long receivedLength = 0;
    private Object body;
    private MediaType mediaType;

    public NettyHttpRequest(io.netty.handler.codec.http.HttpRequest nettyRequest,
                            ChannelHandlerContext ctx,
                            ConversionService conversionService,
                            HttpServerConfiguration serverConfiguration) {
        Objects.requireNonNull(nettyRequest, "Netty request cannot be null");
        Objects.requireNonNull(conversionService, "ConversionService cannot be null");
        this.requestContext = new NettyHttpRequestContext(ctx, this, serverConfiguration, conversionService);
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

    NettyHttpRequestContext getRequestContext() {
        return requestContext;
    }

    void addContent(HttpContent httpContent) {
        ByteBuf byteBuf = httpContent
                            .content()
                            .touch();
        int contentBytes = byteBuf.readableBytes();
        if (contentBytes == 0) {
            byteBuf.release();
        } else {
            receivedContent.add(byteBuf);
            receivedLength += contentBytes;
        }
    }

    public ByteBuf[] getReceivedContent() {
        return receivedContent.toArray(new ByteBuf[receivedContent.size()]);
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
        return (InetSocketAddress) getRequestContext()
                .getContext()
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
        if(body == null) {
            this.body = body = Unpooled.unmodifiableBuffer(getReceivedContent());
        }
        return (T)body;
    }

    /**
     * Release and cleanup resources
     */
    public void release() {
        receivedContent.forEach(ByteBuf::release);
        if(this.body != null && body instanceof ByteBuf) {
            ((ByteBuf)this.body).release();
        }
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

    void setBody(T body) {
        this.body = body;
    }
}
