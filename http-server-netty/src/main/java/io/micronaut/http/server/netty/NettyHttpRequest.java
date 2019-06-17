/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.SupplierUtil;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.cookie.Cookies;
import io.micronaut.http.netty.AbstractNettyHttpRequest;
import io.micronaut.http.netty.NettyHttpHeaders;
import io.micronaut.http.netty.cookies.NettyCookies;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.exceptions.InternalServerException;
import io.micronaut.web.router.RouteMatch;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.multipart.AbstractHttpData;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCounted;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Delegates to the Netty {@link io.netty.handler.codec.http.HttpRequest} instance.
 *
 * @param <T> The type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class NettyHttpRequest<T> extends AbstractNettyHttpRequest<T> implements HttpRequest<T> {

    private static final AttributeKey<NettyHttpRequest> KEY = AttributeKey.valueOf(NettyHttpRequest.class.getSimpleName());

    private final NettyHttpHeaders headers;
    private final ChannelHandlerContext channelHandlerContext;
    private final HttpServerConfiguration serverConfiguration;
    private final Map<Class, Optional> convertedBodies = new LinkedHashMap<>(1);
    private final MutableConvertibleValues<Object> attributes;
    private NettyCookies nettyCookies;
    private List<ByteBufHolder> receivedContent = new ArrayList<>();
    private Map<Integer, AbstractHttpData> receivedData = new LinkedHashMap<>();

    private Supplier<Optional<T>> body;
    private RouteMatch<?> matchedRoute;
    private boolean bodyRequired;

    /**
     * @param nettyRequest        The {@link io.netty.handler.codec.http.HttpRequest}
     * @param ctx                 The {@link ChannelHandlerContext}
     * @param environment         The Environment
     * @param serverConfiguration The {@link HttpServerConfiguration}
     */
    @SuppressWarnings("MagicNumber")
    public NettyHttpRequest(io.netty.handler.codec.http.HttpRequest nettyRequest,
                            ChannelHandlerContext ctx,
                            ConversionService environment,
                            HttpServerConfiguration serverConfiguration) {
        super(nettyRequest, environment);
        Objects.requireNonNull(nettyRequest, "Netty request cannot be null");
        Objects.requireNonNull(ctx, "ChannelHandlerContext cannot be null");
        Objects.requireNonNull(environment, "Environment cannot be null");
        Channel channel = ctx.channel();
        if (channel != null) {
            channel.attr(KEY).set(this);
        }
        this.serverConfiguration = serverConfiguration;
        this.attributes = new MutableConvertibleValuesMap<>(new ConcurrentHashMap<>(4), conversionService);
        this.channelHandlerContext = ctx;
        this.headers = new NettyHttpHeaders(nettyRequest.headers(), conversionService);
        this.body = SupplierUtil.memoizedNonEmpty(() -> Optional.ofNullable((T) buildBody()));
    }

    @Override
    public String toString() {
        return getMethod() + " " + getUri();
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
    public HttpHeaders getHeaders() {
        return headers;
    }

    @Override
    public MutableConvertibleValues<Object> getAttributes() {
        return this.attributes;
    }

    @Override
    public Optional<T> getBody() {
        return this.body.get();
    }

    /**
     * @return A {@link CompositeByteBuf}
     */
    protected Object buildBody() {
        if (!receivedData.isEmpty()) {
            Map body = new LinkedHashMap(receivedData.size());

            for (AbstractHttpData data: receivedData.values()) {
                String newValue = getContent(data);
                //noinspection unchecked
                body.compute(data.getName(), (key, oldValue) -> {
                    if (oldValue == null) {
                        return newValue;
                    } else if (oldValue instanceof Collection) {
                        //noinspection unchecked
                        ((Collection) oldValue).add(newValue);
                        return oldValue;
                    } else {
                        ArrayList<Object> values = new ArrayList<>(2);
                        values.add(oldValue);
                        values.add(newValue);
                        return values;
                    }
                });
                data.release();
            }
            return body;
        } else if (!receivedContent.isEmpty()) {
            int size = receivedContent.size();
            CompositeByteBuf byteBufs = channelHandlerContext.alloc().compositeBuffer(size);
            for (ByteBufHolder holder : receivedContent) {
                ByteBuf content = holder.content();
                if (content != null) {
                    byteBufs.addComponent(true, content);
                }
            }
            return byteBufs;
        } else {
            return null;
        }
    }

    private String getContent(AbstractHttpData data) {
        String newValue;
        try {
            newValue = data.getString(serverConfiguration.getDefaultCharset());
        } catch (IOException e) {
            throw new InternalServerException("Error retrieving or decoding the value for: " + data.getName());
        }
        return newValue;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T1> Optional<T1> getBody(Class<T1> type) {
        Optional<T> body = getBody();
        return body.flatMap(t -> convertedBodies.computeIfAbsent(type, aClass -> conversionService.convert(t, aClass)));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T1> Optional<T1> getBody(Argument<T1> type) {
        Optional<T> body = getBody();
        return body.flatMap(t -> convertedBodies.computeIfAbsent(type.getType(), aClass -> conversionService.convert(t, ConversionContext.of(type))));
    }

    /**
     * Release and cleanup resources.
     */
    @Internal
    public void release() {
        Object body = getBody().orElse(null);
        releaseIfNecessary(body);
        for (ByteBufHolder byteBuf : receivedContent) {
            releaseIfNecessary(byteBuf);
        }
        for (ByteBufHolder byteBuf : receivedData.values()) {
            releaseIfNecessary(byteBuf);
        }
        if (this.body != null && this.body instanceof ReferenceCounted) {
            ReferenceCounted referenceCounted = (ReferenceCounted) this.body;
            releaseIfNecessary(referenceCounted);
        }
        for (Map.Entry<String, Object> attribute : attributes) {
            Object value = attribute.getValue();
            releaseIfNecessary(value);
        }
    }

    /**
     * @param value An object with a value
     */
    protected void releaseIfNecessary(Object value) {
        if (value instanceof ReferenceCounted) {
            ReferenceCounted referenceCounted = (ReferenceCounted) value;
            int i = referenceCounted.refCnt();
            if (i != 0) {
                referenceCounted.release();
            }
        }
    }

    /**
     * Sets the body.
     *
     * @param body The body to set
     */
    @Internal
    public void setBody(T body) {
        this.body = () -> Optional.ofNullable(body);
        this.convertedBodies.clear();
    }

    /**
     * @return Obtains the matched route
     */
    @Internal
    public RouteMatch<?> getMatchedRoute() {
        return matchedRoute;
    }

    /**
     * @param httpContent The HttpContent as {@link ByteBufHolder}
     */
    @Internal
    void addContent(ByteBufHolder httpContent) {
        if (httpContent instanceof AbstractHttpData) {
            receivedData.computeIfAbsent(System.identityHashCode(httpContent), (key) -> {
                httpContent.retain();
                return (AbstractHttpData) httpContent;
            });
        } else {
            receivedContent.add(httpContent);
        }
    }

    /**
     * @param matchedRoute The matched route
     */
    @Internal
    void setMatchedRoute(RouteMatch<?> matchedRoute) {
        this.matchedRoute = matchedRoute;
    }

    /**
     * @param bodyRequired Sets the body as required
     */
    @Internal
    void setBodyRequired(boolean bodyRequired) {
        this.bodyRequired = bodyRequired;
    }

    /**
     * @return Whether the body is required
     */
    @Internal
    boolean isBodyRequired() {
        return bodyRequired || HttpMethod.requiresRequestBody(getMethod());
    }

    @Override
    protected Charset initCharset(Charset characterEncoding) {
        return characterEncoding == null ? serverConfiguration.getDefaultCharset() : characterEncoding;
    }

    /**
     * Lookup the current request from the context.
     *
     * @param ctx The context
     * @return The request or null if it is not present
     */
    static NettyHttpRequest get(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        io.netty.util.Attribute<NettyHttpRequest> attr = channel.attr(KEY);
        return attr.get();
    }

    /**
     * Remove the current request from the context.
     *
     * @param ctx The context
     * @return The request or null if it is not present
     */
    static NettyHttpRequest remove(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();

        io.netty.util.Attribute<NettyHttpRequest> attr = channel.attr(KEY);
        return attr.getAndSet(null);
    }

}
