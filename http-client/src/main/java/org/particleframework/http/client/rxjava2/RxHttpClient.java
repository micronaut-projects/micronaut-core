/*
 * Copyright 2018 original authors
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
package org.particleframework.http.client.rxjava2;

import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.netty.http.StreamedHttpResponse;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.*;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder;
import io.netty.handler.ssl.SslContext;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.disposables.Disposable;
import org.particleframework.context.annotation.Argument;
import org.particleframework.context.annotation.Prototype;
import org.particleframework.context.annotation.Replaces;
import org.particleframework.context.annotation.Requires;
import org.particleframework.core.async.subscriber.CompletionAwareSubscriber;
import org.particleframework.core.io.buffer.ByteBuffer;
import org.particleframework.http.HttpMethod;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.MediaType;
import org.particleframework.http.client.*;
import org.particleframework.http.client.exceptions.HttpClientException;
import org.particleframework.http.codec.MediaTypeCodecRegistry;
import org.particleframework.http.filter.HttpClientFilter;
import org.particleframework.jackson.codec.JsonMediaTypeCodec;
import org.particleframework.jackson.parser.JacksonProcessor;
import org.reactivestreams.Subscription;

import javax.inject.Inject;
import java.net.URI;
import java.net.URL;
import java.util.Map;

/**
 * Subclass of {@link DefaultHttpClient} that exposes a RxJava 2.x API
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Prototype
@Replaces(DefaultHttpClient.class)
@Requires(classes = Flowable.class)
public class RxHttpClient extends DefaultHttpClient implements StreamingHttpClient {

    public RxHttpClient( URL url, HttpClientConfiguration configuration, MediaTypeCodecRegistry codecRegistry, HttpClientFilter... filters) {
        super(url, configuration, codecRegistry, filters);
    }

    @Inject
    public RxHttpClient( @Argument ServerSelector serverSelector, @Argument HttpClientConfiguration configuration, MediaTypeCodecRegistry codecRegistry, HttpClientFilter... filters) {
        super(serverSelector, configuration, codecRegistry, filters);
    }

    public RxHttpClient(ServerSelector serverSelector) {
        super(serverSelector);
    }

    public RxHttpClient(URL url) {
        super(url);
    }

    @Override
    public <I> Flowable<HttpResponse<ByteBuffer>> exchange(HttpRequest<I> request) {
        return Flowable.fromPublisher(super.exchange(request));
    }

    @Override
    public <I> Flowable<ByteBuffer<?>> dataStream(HttpRequest<I> request) {
        URI requestURI = resolveRequestURI(request);
        SslContext sslContext = buildSslContext(requestURI);

        return Flowable.create(emitter -> {
            ChannelFuture channelFuture = doConnect(requestURI, sslContext);
            Disposable disposable = buildDisposableChannel(channelFuture);
            emitter.setDisposable(disposable);
            emitter.setCancellable(disposable::dispose);


            channelFuture
                    .addListener((ChannelFutureListener) f -> {
                        if (f.isSuccess()) {
                            Channel channel = f.channel();
                            io.netty.handler.codec.http.HttpRequest nettyRequest = prepareRequest(request, requestURI);

                            ChannelPipeline pipeline = channel.pipeline();
                            pipeline.remove(HANDLER_AGGREGATOR);
                            pipeline.addLast(new SimpleChannelInboundHandler<StreamedHttpResponse>() {

                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, StreamedHttpResponse msg) throws Exception {
                                    msg.subscribe(new CompletionAwareSubscriber<HttpContent>() {
                                        @Override
                                        protected void doOnSubscribe(Subscription subscription) {
                                            subscription.request(emitter.requested());
                                        }

                                        @Override
                                        protected void doOnNext(HttpContent message) {
                                            ByteBuf byteBuf = message.content();
                                            emitter.onNext(byteBufferFactory.wrap(byteBuf));
                                        }

                                        @Override
                                        protected void doOnError(Throwable t) {
                                            emitter.onError(t);
                                        }

                                        @Override
                                        protected void doOnComplete() {
                                            emitter.onComplete();
                                        }
                                    });
                                }
                            });
                            channel.writeAndFlush(nettyRequest);
                        } else {
                            Throwable cause = f.cause();
                            emitter.onError(
                                    new HttpClientException("Connect error:" + cause.getMessage(), cause)
                            );
                        }
                    });
        }, BackpressureStrategy.BUFFER);
    }


    @Override
    public <I> Flowable<HttpResponse<ByteBuffer<?>>> exchangeStream(HttpRequest<I> request) {
        URI requestURI = resolveRequestURI(request);
        SslContext sslContext = buildSslContext(requestURI);

        return Flowable.create(emitter -> {
            ChannelFuture channelFuture = doConnect(requestURI, sslContext);
            Disposable disposable = buildDisposableChannel(channelFuture);
            emitter.setDisposable(disposable);
            emitter.setCancellable(disposable::dispose);


            channelFuture
                    .addListener((ChannelFutureListener) f -> {
                        if (f.isSuccess()) {
                            Channel channel = f.channel();
                            io.netty.handler.codec.http.HttpRequest nettyRequest = prepareRequest(request, requestURI);

                            ChannelPipeline pipeline = channel.pipeline();
                            pipeline.remove(HANDLER_AGGREGATOR);
                            pipeline.addLast(new SimpleChannelInboundHandler<StreamedHttpResponse>() {

                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, StreamedHttpResponse msg) throws Exception {

                                    NettyStreamedHttpResponse<ByteBuffer<?>> response = new NettyStreamedHttpResponse<>(msg);
                                    msg.subscribe(new CompletionAwareSubscriber<HttpContent>() {
                                        @Override
                                        protected void doOnSubscribe(Subscription subscription) {
                                            subscription.request(emitter.requested());
                                        }

                                        @Override
                                        protected void doOnNext(HttpContent message) {
                                            ByteBuf byteBuf = message.content();
                                            ByteBuffer<?> byteBuffer = byteBufferFactory.wrap(byteBuf);
                                            response.setBody(byteBuffer);
                                            emitter.onNext(response);
                                        }

                                        @Override
                                        protected void doOnError(Throwable t) {
                                            emitter.onError(t);
                                        }

                                        @Override
                                        protected void doOnComplete() {
                                            emitter.onComplete();
                                        }
                                    });
                                }
                            });
                            channel.writeAndFlush(nettyRequest);
                        } else {
                            Throwable cause = f.cause();
                            emitter.onError(
                                    new HttpClientException("Connect error:" + cause.getMessage(), cause)
                            );
                        }
                    });
        }, BackpressureStrategy.BUFFER);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <I> Flowable<Map<String, Object>> jsonStream(HttpRequest<I> request) {
        Flowable flowable = jsonStream(request, Map.class);
        return flowable;
    }

    @Override
    public <I, O> Flowable<O> jsonStream(HttpRequest<I> request, Class<O> type) {
        return jsonStream(request, org.particleframework.core.type.Argument.of(type));
    }

    @Override
    public <I, O> Flowable<O> jsonStream(HttpRequest<I> request, org.particleframework.core.type.Argument<O> type) {
        JsonMediaTypeCodec mediaTypeCodec = (JsonMediaTypeCodec) mediaTypeCodecRegistry.findCodec(MediaType.APPLICATION_JSON_TYPE)
                .orElseThrow(() -> new IllegalStateException("No JSON codec found"));
        URI requestURI = resolveRequestURI(request);
        SslContext sslContext = buildSslContext(requestURI);

        return Flowable.create(emitter -> {
            ChannelFuture channelFuture = doConnect(requestURI, sslContext);
            Disposable disposable = buildDisposableChannel(channelFuture);
            emitter.setDisposable(disposable);
            emitter.setCancellable(disposable::dispose);

            channelFuture
                    .addListener((ChannelFutureListener) f -> {
                        if (f.isSuccess()) {
                            Channel channel = f.channel();
                            io.netty.handler.codec.http.HttpRequest nettyRequest = prepareRequest(request, requestURI);
                            ChannelPipeline pipeline = channel.pipeline();
                            pipeline.remove(HANDLER_AGGREGATOR);
                            pipeline.addLast(newJsonStreamDecoder(type, mediaTypeCodec, emitter));
                            channel.writeAndFlush(nettyRequest);
                        } else {
                            Throwable cause = f.cause();
                            emitter.onError(
                                    new HttpClientException("Connect error:" + cause.getMessage(), cause)
                            );
                        }
                    });
        }, BackpressureStrategy.BUFFER);

    }

    @Override
    public <I, O> Flowable<HttpResponse<O>> exchange(HttpRequest<I> request, org.particleframework.core.type.Argument<O> bodyType) {
        return Flowable.fromPublisher(super.exchange(request, bodyType));
    }

    @Override
    public <I, O> Flowable<HttpResponse<O>> exchange(HttpRequest<I> request, Class<O> bodyType) {
        return Flowable.fromPublisher(super.exchange(request, bodyType));
    }

    @Override
    public <I, O> Flowable<O> retrieve(HttpRequest<I> request, org.particleframework.core.type.Argument<O> bodyType) {
        return Flowable.fromPublisher(super.retrieve(request, bodyType));
    }

    @Override
    public <I, O> Flowable<O> retrieve(HttpRequest<I> request, Class<O> bodyType) {
        return Flowable.fromPublisher(super.retrieve(request, bodyType));
    }

    @Override
    public Flowable<HttpResponse<ByteBuffer>> exchange(String uri) {
        return Flowable.fromPublisher(super.exchange(uri));
    }

    @Override
    public <O> Flowable<HttpResponse<O>> exchange(String uri, Class<O> bodyType) {
        return Flowable.fromPublisher(super.exchange(uri, bodyType));
    }

    @Override
    public <I> Flowable<String> retrieve(HttpRequest<I> request) {
        return Flowable.fromPublisher(super.retrieve(request));
    }

    @Override
    public Flowable<String> retrieve(String uri) {
        return Flowable.fromPublisher(super.retrieve(uri));
    }

    /**
     * Create a new {@link HttpClient}. Note that this method should only be used outside of the context of a Particle application. Within particle use
     * {@link javax.inject.Inject} to inject a client instead
     *
     *
     * @param url The base URL
     * @return The client
     */
    public static RxHttpClient create(URL url) {
        return new RxHttpClient(url);
    }


    private <I> io.netty.handler.codec.http.HttpRequest prepareRequest(HttpRequest<I> request, URI requestURI) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        MediaType requestContentType = request
                .getContentType()
                .orElse(MediaType.APPLICATION_JSON_TYPE);

        boolean permitsBody = HttpMethod.permitsRequestBody(request.getMethod());
        io.netty.handler.codec.http.HttpRequest nettyRequest =
                buildNettyRequest(
                        request,
                        requestContentType,
                        permitsBody);


        prepareHttpHeaders(requestURI, request, nettyRequest, permitsBody);
        return nettyRequest;
    }




    private <O> SimpleChannelInboundHandler<StreamedHttpResponse> newJsonStreamDecoder(org.particleframework.core.type.Argument<O> type, JsonMediaTypeCodec mediaTypeCodec, FlowableEmitter<O> emitter) {
        return new SimpleChannelInboundHandler<StreamedHttpResponse>() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                emitter.onError(
                        new HttpClientException("Client error:" + cause.getMessage(), cause)
                );
            }

            @Override
            protected void channelRead0(ChannelHandlerContext ctx, StreamedHttpResponse msg) throws Exception {
                JacksonProcessor jacksonProcessor = new JacksonProcessor();
                jacksonProcessor.subscribe(new CompletionAwareSubscriber<JsonNode>() {
                    @Override
                    protected void doOnSubscribe(Subscription subscription) {
                        long demand = emitter.requested();
                        subscription.request(demand);
                    }

                    @Override
                    protected void doOnNext(JsonNode message) {
                        O json = mediaTypeCodec.decode(type, message);
                        emitter.onNext(json);
                    }

                    @Override
                    protected void doOnError(Throwable t) {
                        emitter.onError(t);
                    }

                    @Override
                    protected void doOnComplete() {
                        emitter.onComplete();
                    }
                });
                msg.subscribe(new CompletionAwareSubscriber<HttpContent>() {
                    @Override
                    protected void doOnSubscribe(Subscription subscription) {
                        long demand = emitter.requested();
                        jacksonProcessor.onSubscribe(subscription);
                        subscription.request(demand);
                    }

                    @Override
                    protected void doOnNext(HttpContent message) {
                        try {
                            jacksonProcessor.onNext(
                                    ByteBufUtil.getBytes(message.content())
                            );
                        } catch (Exception e) {
                            jacksonProcessor.onError(e);
                        }
                    }

                    @Override
                    protected void doOnError(Throwable t) {
                        jacksonProcessor.onError(t);
                    }

                    @Override
                    protected void doOnComplete() {
                        jacksonProcessor.onComplete();
                    }
                });
            }

        };
    }

    private Disposable buildDisposableChannel(ChannelFuture channelFuture) {
        return new Disposable() {
            boolean disposed = false;

            @Override
            public void dispose() {
                if (!disposed) {
                    Channel channel = channelFuture.channel();
                    if (channel.isOpen()) {
                        closeChannelAsync(channel);
                    }
                    disposed = true;
                }
            }

            @Override
            public boolean isDisposed() {
                return disposed;
            }
        };
    }
}
