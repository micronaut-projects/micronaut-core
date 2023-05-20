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

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.MediaType;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.netty.buffer.ByteBufHolder;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpData;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.HttpPostStandardRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.InterfaceHttpPostRequestDecoder;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <p>Decodes {@link MediaType#MULTIPART_FORM_DATA} in a non-blocking manner.</p>
 *
 * <p>Designed to be used by a single thread</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class FormDataHttpContentProcessor extends AbstractHttpContentProcessor<HttpData> {

    private final InterfaceHttpPostRequestDecoder decoder;
    private final boolean enabled;
    private final AtomicLong extraMessages = new AtomicLong(0);
    private final long partMaxSize;

    /**
     * Set to true to request a destroy by any thread.
     */
    private volatile boolean pleaseDestroy = false;
    /**
     * {@code true} during {@link #doOnNext}, can't destroy while that's running.
     */
    private volatile boolean inFlight = false;
    /**
     * {@code true} if the decoder has been destroyed or will be destroyed in the near future.
     */
    private boolean destroyed = false;

    /**
     * @param nettyHttpRequest The {@link NettyHttpRequest}
     * @param configuration    The {@link NettyHttpServerConfiguration}
     */
    FormDataHttpContentProcessor(NettyHttpRequest<?> nettyHttpRequest, NettyHttpServerConfiguration configuration) {
        super(nettyHttpRequest, configuration);
        Charset characterEncoding = nettyHttpRequest.getCharacterEncoding();
        HttpServerConfiguration.MultipartConfiguration multipart = configuration.getMultipart();
        HttpDataFactory factory;
        if (multipart.isDisk()) {
            factory = new DefaultHttpDataFactory(true, characterEncoding);
        } else if (multipart.isMixed()) {
            factory = new DefaultHttpDataFactory(multipart.getThreshold(), characterEncoding);
        } else {
            factory = new DefaultHttpDataFactory(false, characterEncoding);
        }
        factory.setMaxLimit(multipart.getMaxFileSize());
        final HttpRequest nativeRequest = nettyHttpRequest.getNativeRequest();
        if (HttpPostRequestDecoder.isMultipart(nativeRequest)) {
            this.decoder = new MicronautHttpPostMultipartRequestDecoder(factory, nativeRequest, characterEncoding);
        } else {
            this.decoder = new HttpPostStandardRequestDecoder(factory, nativeRequest, characterEncoding);
        }
        this.enabled = nettyHttpRequest.getContentType().map(type -> type.equals(MediaType.APPLICATION_FORM_URLENCODED_TYPE)).orElse(false) ||
            multipart.isEnabled();
        this.partMaxSize = multipart.getMaxFileSize();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    protected void doOnSubscribe(Subscription subscription, Subscriber<? super HttpData> subscriber) {
        subscriber.onSubscribe(new Subscription() {

            @Override
            public void request(long n) {
                extraMessages.updateAndGet(p -> {
                    long newVal = p - n;
                    if (newVal < 0) {
                        subscription.request(n - p);
                        return 0;
                    } else {
                        return newVal;
                    }
                });
            }

            @Override
            public void cancel() {
                subscription.cancel();
                pleaseDestroy = true;
                destroyIfRequested();
            }
        });
    }

    @Override
    protected void onData(ByteBufHolder message) {
        boolean skip;
        synchronized (this) {
            if (destroyed) {
                skip = true;
            } else {
                skip = false;
                inFlight = true;
            }
        }
        if (skip) {
            message.release();
            return;
        }
        try {
            Subscriber<? super HttpData> subscriber = getSubscriber();

            if (message instanceof HttpContent) {
                HttpContent httpContent = (HttpContent) message;
                List<InterfaceHttpData> messages = new ArrayList<>(1);

                try {
                    InterfaceHttpPostRequestDecoder postRequestDecoder = this.decoder;
                    postRequestDecoder.offer(httpContent);

                    while (postRequestDecoder.hasNext()) {
                        InterfaceHttpData data = postRequestDecoder.next();
                        data.touch();
                        switch (data.getHttpDataType()) {
                            case Attribute:
                                Attribute attribute = (Attribute) data;
                                // bodyListHttpData keeps a copy and releases it later
                                messages.add(attribute.retain());
                                postRequestDecoder.removeHttpDataFromClean(attribute);
                                break;
                            case FileUpload:
                                FileUpload fileUpload = (FileUpload) data;
                                if (fileUpload.isCompleted()) {
                                    // bodyListHttpData keeps a copy and releases it later
                                    messages.add(fileUpload.retain());
                                    postRequestDecoder.removeHttpDataFromClean(fileUpload);
                                }
                                break;
                            default:
                                // no-op
                        }
                    }

                    InterfaceHttpData currentPartialHttpData = postRequestDecoder.currentPartialHttpData();
                    if (currentPartialHttpData instanceof HttpData) {
                        // can't give away ownership of this data yet, so retain it
                        messages.add(currentPartialHttpData.retain());
                    }

                } catch (HttpPostRequestDecoder.EndOfDataDecoderException e) {
                    // ok, ignore
                } catch (HttpPostRequestDecoder.ErrorDataDecoderException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof IOException && cause.getMessage().equals("Size exceed allowed maximum capacity")) {
                        String partName = decoder.currentPartialHttpData().getName();
                        try {
                            onError(new ContentLengthExceededException("The part named [" + partName + "] exceeds the maximum allowed content length [" + partMaxSize + "]"));
                        } finally {
                            parentSubscription.cancel();
                        }
                    } else {
                        onError(e);
                    }
                } catch (Throwable e) {
                    onError(e);
                } finally {
                    if (messages.isEmpty()) {
                        subscription.request(1);
                    } else {
                        extraMessages.updateAndGet(p -> p + messages.size() - 1);
                        messages.stream().map(HttpData.class::cast).forEach(data -> {
                            try {
                                subscriber.onNext(data);
                            } catch (Throwable e) {
                                subscriber.onError(Operators.onOperatorError(subscription, e, data, Context.empty()));
                            }
                        });
                    }

                    httpContent.release();
                }
            } else {
                message.release();
            }
        } finally {
            inFlight = false;
            destroyIfRequested();
        }
    }

    @Override
    protected void doAfterOnError(Throwable throwable) {
        pleaseDestroy = true;
        destroyIfRequested();
    }

    @Override
    protected void doAfterComplete() {
        pleaseDestroy = true;
        destroyIfRequested();
    }

    private void destroyIfRequested() {
        boolean destroy;
        synchronized (this) {
            if (pleaseDestroy && !destroyed && !inFlight) {
                destroy = true;
                destroyed = true;
            } else {
                destroy = false;
            }
        }
        if (destroy) {
            decoder.destroy();
        }
    }

}
