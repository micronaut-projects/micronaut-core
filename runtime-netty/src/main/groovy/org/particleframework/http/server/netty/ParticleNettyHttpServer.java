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

import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.netty.http.HttpStreamsServerHandler;
import com.typesafe.netty.http.StreamedHttpRequest;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.AttributeKey;
import org.particleframework.bind.ArgumentBinder;
import org.particleframework.configuration.jackson.parser.JacksonProcessor;
import org.particleframework.context.ApplicationContext;
import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.TypeConverter;
import org.particleframework.http.MediaType;
import org.particleframework.http.binding.RequestBinderRegistry;
import org.particleframework.http.binding.binders.request.BodyArgumentBinder;
import org.particleframework.http.server.HttpServerConfiguration;
import org.particleframework.inject.Argument;
import org.particleframework.inject.ReturnType;
import org.particleframework.runtime.server.EmbeddedServer;
import org.particleframework.web.router.RouteMatch;
import org.particleframework.web.router.Router;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class ParticleNettyHttpServer implements EmbeddedServer {
    static final AttributeKey<NettyHttpRequestContext> REQUEST_CONTEXT_KEY = AttributeKey.newInstance("REQUEST_CONTEXT_KEY");

    private volatile Channel serverChannel;
    private final HttpServerConfiguration serverConfiguration;
    private final ApplicationContext applicationContext;

    @Inject
    public ParticleNettyHttpServer(
            HttpServerConfiguration serverConfiguration,
            ApplicationContext applicationContext
    ) {
        this.serverConfiguration = serverConfiguration;
        this.applicationContext = applicationContext;
        applicationContext.getConversionService().addConverter(
                ByteBuf.class,
                String.class,
                new TypeConverter<ByteBuf, String>() {
                    @Override
                    public Optional<String> convert(ByteBuf object, Class<String> targetType, ConversionContext context) {
                        return Optional.of(object.toString(context.getCharset()));
                    }
                }
        );
    }

    @Override
    public EmbeddedServer start() {
        // TODO: allow configuration of threads and number of groups
        NioEventLoopGroup workerGroup = new NioEventLoopGroup();
        NioEventLoopGroup parentGroup = new NioEventLoopGroup();
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        // TODO: allow configuration of channel options
        if (applicationContext == null) {
            throw new IllegalStateException("Netty HTTP Server implementation requires a reference to the ApplicationContext");
        }
        ChannelFuture future = serverBootstrap.group(parentGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();

                        Optional<Router> routerBean = applicationContext.findBean(Router.class);
                        Optional<RequestBinderRegistry> binderRegistry = applicationContext.findBean(RequestBinderRegistry.class);

                        pipeline.addLast(new HttpServerCodec());
                        pipeline.addLast(new HttpStreamsServerHandler());
                        pipeline.addLast(new SimpleChannelInboundHandler<HttpRequest>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, HttpRequest msg) throws Exception {
                                Channel channel = ctx.channel();

                                NettyHttpRequest nettyHttpRequest = new NettyHttpRequest(msg, applicationContext.getEnvironment());
                                NettyHttpRequestContext requestContext = new NettyHttpRequestContext(nettyHttpRequest);

                                // set the request on the channel
                                channel.attr(REQUEST_CONTEXT_KEY).set(requestContext);

                                Optional<RouteMatch> routeMatch = routerBean.flatMap((router) ->
                                        router.find(nettyHttpRequest.getMethod(), nettyHttpRequest.getPath())
                                                .filter((match) -> match.test(nettyHttpRequest))
                                                .findFirst()
                                );

                                routeMatch.ifPresent((route -> {

                                    // TODO: check the media type vs the route and return 415 if invalid

                                    // TODO: here we need to analyze the binding requirements and if
                                    // the body is required then add an additional handler to the pipeline
                                    // right now only URL parameters are supported

                                    requestContext.setMatchedRoute(route);

                                    ReturnType returnType = route.getReturnType();
                                    boolean requiresBody = false;
                                    Collection<Argument> requiredArguments = route.getRequiredArguments();
                                    Map<String, Object> argumentValues;

                                    if (requiredArguments.isEmpty()) {
                                        // no required arguments so just execute
                                        argumentValues = Collections.emptyMap();
                                    } else {
                                        argumentValues = new LinkedHashMap<>();
                                        // Begin try fulfilling the argument requirements
                                        for (Argument argument : requiredArguments) {
                                            if (binderRegistry.isPresent()) {
                                                Optional<ArgumentBinder> registeredBinder = binderRegistry.get()
                                                        .findArgumentBinder(argument, nettyHttpRequest);
                                                if (registeredBinder.isPresent()) {
                                                    ArgumentBinder argumentBinder = registeredBinder.get();
                                                    if (argumentBinder instanceof BodyArgumentBinder) {
                                                        requiresBody = true;
                                                        BodyArgumentBinder bodyArgumentBinder = (BodyArgumentBinder) argumentBinder;
                                                        requestContext.addBodyArgument(argument, bodyArgumentBinder);
                                                    } else {

                                                        Optional bindingResult = argumentBinder
                                                                .bind(argument, nettyHttpRequest);
                                                        if (argument.getType() == Optional.class) {
                                                            argumentValues.put(argument.getName(), bindingResult);
                                                            continue;
                                                        } else if (bindingResult.isPresent()) {
                                                            argumentValues.put(argument.getName(), bindingResult.get());
                                                            continue;
                                                        }
                                                    }
                                                }
                                            }

                                            if (!requiresBody) {
                                                // if we arrived here the request is not processable
                                                // TODO: Change to 400: Bad Request
                                                requestContext
                                                        .getResponseTransmitter()
                                                        .sendNotFound(channel);
                                                return;
                                            }
                                        }

                                    }

                                    requestContext.setRouteArguments(argumentValues);

                                    if (!requiresBody) {

                                        // TODO: here we need a way to make the encoding of the result flexible
                                        // also support for GSON views etc.

                                        // TODO: Also need to handle exceptions that emerge from invoke()
                                        channel.eventLoop().execute(() -> {
                                                    Object result = route.execute(argumentValues);
                                                    Charset charset = serverConfiguration.getDefaultCharset();
                                                    requestContext.getResponseTransmitter()
                                                            .sendText(channel, result, charset);
                                                }
                                        );
                                    } else if (msg instanceof StreamedHttpRequest) {
                                        MediaType contentType = nettyHttpRequest.getContentType();
                                        StreamedHttpRequest streamedHttpRequest = (StreamedHttpRequest) msg;
                                        if("json".equals(contentType.getExtension())) {
                                            JacksonProcessor jacksonProcessor = new JacksonProcessor();
                                            streamedHttpRequest.subscribe(new Subscriber<HttpContent>() {
                                                @Override
                                                public void onSubscribe(Subscription s) {
                                                    s.request(Long.MAX_VALUE);
                                                    AtomicReference<JsonNode> nodeRef = new AtomicReference<>();
                                                    jacksonProcessor.subscribe(new Subscriber<JsonNode>() {
                                                        @Override
                                                        public void onSubscribe(Subscription s) {
                                                            s.request(Long.MAX_VALUE);
                                                        }

                                                        @Override
                                                        public void onNext(JsonNode jsonNode) {
                                                            nodeRef.set(jsonNode);
                                                        }

                                                        @Override
                                                        public void onError(Throwable t) {
                                                            // TODO: error handling / error handlers
                                                            requestContext.getResponseTransmitter().sendServerError(ctx);
                                                        }

                                                        @Override
                                                        public void onComplete() {
                                                            JsonNode jsonNode = nodeRef.get();
                                                            if(jsonNode != null) {
                                                                nettyHttpRequest.setBody(jsonNode);
                                                                processRequestBody(channel, requestContext);
                                                            }
                                                            else {
                                                                // TODO: should be  400: Bad Request
                                                                requestContext.getResponseTransmitter().sendNotFound(channel);
                                                            }
                                                        }
                                                    });
                                                }

                                                @Override
                                                public void onNext(HttpContent httpContent) {
                                                    ByteBuf content = httpContent.content();
                                                    int len = content.readableBytes();
                                                    if(len > 0) {
                                                        byte[] bytes;
                                                        if(content.hasArray()) {
                                                            bytes = content.array();
                                                        }
                                                        else {
                                                            bytes = new byte[len];
                                                            content.readBytes(bytes);
                                                        }

                                                        jacksonProcessor.onNext(bytes);
                                                    }
                                                }

                                                @Override
                                                public void onError(Throwable t) {
                                                    // TODO: error handling / error handlers
                                                    requestContext.getResponseTransmitter().sendServerError(ctx);
                                                }

                                                @Override
                                                public void onComplete() {
                                                    jacksonProcessor.onComplete();
                                                }
                                            });
                                        }
                                        else {

                                            Subscriber<HttpContent> contentSubscriber = new Subscriber<HttpContent>() {
                                                @Override
                                                public void onSubscribe(Subscription s) {
                                                    s.request(Long.MAX_VALUE - 1);
                                                }

                                                @Override
                                                public void onNext(HttpContent httpContent) {
                                                    requestContext.getRequest().addContent(httpContent);
                                                }

                                                @Override
                                                public void onError(Throwable t) {
                                                    // TODO: error handling / error handlers
                                                    requestContext.getResponseTransmitter().sendServerError(ctx);
                                                }

                                                @Override
                                                public void onComplete() {
                                                    processRequestBody(channel, requestContext);
                                                }
                                            };

                                            streamedHttpRequest.subscribe(contentSubscriber);
                                        }

                                    } else {
                                        // TODO: should be  400: Bad Request
                                        requestContext.getResponseTransmitter().sendNotFound(channel);
                                    }


                                }));

                                if (!routeMatch.isPresent()) {
                                    // TODO: check if any other routes exist for URI and return 405 if they do

                                    // TODO: Here we need to add routing for 404 handlers
                                    requestContext.getResponseTransmitter().sendNotFound(channel);
                                }
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {

                                // TODO: Here we need to add routing to error handlers
                                DefaultHttpResponse httpResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                                ctx.channel().writeAndFlush(httpResponse)
                                        .addListener(ChannelFutureListener.CLOSE);

                            }
                        });


                    }
                })
                // TODO: handle random port binding
                .bind(serverConfiguration.getHost(), serverConfiguration.getPort());
        try {
            future.sync();
        } catch (InterruptedException e) {
            // TODO: exception handling
            e.printStackTrace();
        }
        this.serverChannel = future.channel();
        return this;
    }

    protected void processRequestBody(Channel channel, NettyHttpRequestContext requestContext) {
        channel.eventLoop().execute(() -> {

            List<NettyHttpRequestContext.UnboundBodyArgument> unboundBodyArguments = requestContext.getUnboundBodyArguments();
            Map<String, Object> resolvedArguments = requestContext.getRouteArguments();

            for (NettyHttpRequestContext.UnboundBodyArgument unboundBodyArgument : unboundBodyArguments) {
                Argument argument = unboundBodyArgument.argument;
                BodyArgumentBinder argumentBinder = unboundBodyArgument.argumentBinder;
                Optional bound = argumentBinder.bind(argument, requestContext.getRequest());
                if (bound.isPresent()) {
                    resolvedArguments.put(argument.getName(), bound.get());
                } else {
                    // TODO: replace with 422
                    requestContext.getResponseTransmitter().sendNotFound(channel);
                    return;
                }
            }

            RouteMatch route = requestContext.getMatchedRoute();
            Object result = route.execute(resolvedArguments);
            Charset charset = serverConfiguration.getDefaultCharset();
            requestContext.getResponseTransmitter()
                    .sendText(channel, result, charset);
        });
    }

    @Override
    public EmbeddedServer stop() {
        if (this.serverChannel != null) {
            try {
                serverChannel.close().sync();
            } catch (Throwable e) {
                // TODO: exception handling
                e.printStackTrace();
            }
        }
        return this;
    }

    @Override
    public int getPort() {
        return serverConfiguration.getPort();
    }

}
