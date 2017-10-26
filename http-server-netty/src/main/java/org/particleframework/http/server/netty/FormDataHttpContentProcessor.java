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

import io.netty.buffer.ByteBufHolder;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.multipart.*;
import org.particleframework.http.MediaType;
import org.particleframework.http.server.netty.configuration.NettyHttpServerConfiguration;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.nio.charset.Charset;

/**
 * Decodes {@link org.particleframework.http.MediaType#MULTIPART_FORM_DATA} in a non-blocking manner
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class FormDataHttpContentProcessor extends AbstractHttpContentProcessor<HttpData> {

    private final HttpPostRequestDecoder decoder;
    private final boolean enabled;
    private InterfaceHttpData currentPartialHttpData;

    public FormDataHttpContentProcessor(NettyHttpRequest<?> nettyHttpRequest, NettyHttpServerConfiguration configuration) {
        super(nettyHttpRequest, configuration);
        Charset characterEncoding = nettyHttpRequest.getCharacterEncoding();
        DefaultHttpDataFactory factory = new DefaultHttpDataFactory(configuration.getMultipart().isDisk(), characterEncoding);
        factory.setMaxLimit(configuration.getMultipart().getMaxFileSize());
        this.decoder = new HttpPostRequestDecoder(factory, nettyHttpRequest.getNativeRequest(), characterEncoding);
        this.enabled = nettyHttpRequest.getContentType() == MediaType.APPLICATION_FORM_URLENCODED_TYPE ||
                                configuration.getMultipart().isEnabled();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }


    @Override
    public void onSubscribe(Subscription subscription) {
        this.parentSubscription = subscription;
        Subscriber<? super HttpData> subscriber = this.subscriber.get();

        if(!verifyState(subscriber)) {
            return;
        }

        subscriber.onSubscribe(subscription);
    }

    @Override
    protected void publishMessage(ByteBufHolder message) {
        Subscriber<? super HttpData> subscriber = this.subscriber.get();
        verifyState(subscriber);

        if(message instanceof HttpContent) {
            try {
                HttpContent httpContent = (HttpContent) message;
                HttpPostRequestDecoder postRequestDecoder = this.decoder;
                postRequestDecoder.offer(httpContent);
                while (postRequestDecoder.hasNext()) {
                    InterfaceHttpData data = postRequestDecoder.next();
                    try {
                        switch (data.getHttpDataType()) {
                            case Attribute:
                                Attribute attribute = (Attribute) data;
                                subscriber.onNext(attribute);
                                break;
                            case FileUpload:
                                FileUpload fileUpload = (FileUpload) data;
                                if (fileUpload.isCompleted()) {
                                    subscriber.onNext(fileUpload);
                                }
                                break;
                        }
                    } finally {
                        data.release();
                    }

                }
                this.currentPartialHttpData = postRequestDecoder.currentPartialHttpData();
                if(currentPartialHttpData instanceof HttpData) {
                    subscriber.onNext((HttpData)currentPartialHttpData);
                }
            } catch (HttpPostRequestDecoder.EndOfDataDecoderException e) {
                // ok, ignore
            } catch (Throwable e) {
                onError(e);
            }
        }
        else {
            message.release();
        }
    }

    @Override
    public void onError(Throwable t) {
        try {
            Subscriber<? super HttpData> subscriber = this.subscriber.get();
            verifyState(subscriber);
            subscriber.onError(t);
            parentSubscription.cancel();
        } finally {
            decoder.destroy();
        }
    }

    @Override
    public void onComplete() {
        try {
            Subscriber<? super HttpData> subscriber = this.subscriber.get();
            verifyState(subscriber);
            subscriber.onComplete();
        } finally {
            decoder.destroy();
        }
    }

}
