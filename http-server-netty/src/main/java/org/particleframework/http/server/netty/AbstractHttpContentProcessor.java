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

import com.typesafe.netty.http.StreamedHttpMessage;
import io.netty.buffer.ByteBufHolder;
import org.particleframework.http.exceptions.ContentLengthExceededException;
import org.particleframework.http.server.HttpServerConfiguration;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Abtract implementation of the {@link HttpContentProcessor} interface that deals with limiting file upload sizes
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class AbstractHttpContentProcessor<T> implements HttpContentProcessor<T> {

    protected static final Subscription EMPTY_SUBSCRIPTION = new Subscription() {
        @Override
        public void request(long n) {
        }

        @Override
        public void cancel() {

        }
    };

    protected final AtomicReference<Subscriber<? super T>> subscriber = new AtomicReference<>();
    protected final NettyHttpRequest nettyHttpRequest;
    protected final long advertisedLength;
    protected final long requestMaxSize;
    protected final AtomicLong receivedLength = new AtomicLong();
    protected final HttpServerConfiguration configuration;
    protected Subscription parentSubscription;

    public AbstractHttpContentProcessor(NettyHttpRequest<?> nettyHttpRequest, HttpServerConfiguration configuration) {
        this.nettyHttpRequest = nettyHttpRequest;
        this.advertisedLength = nettyHttpRequest.getContentLength();
        this.requestMaxSize = configuration.getMaxRequestSize();
        this.configuration = configuration;
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        if(!this.subscriber.compareAndSet(null, subscriber)) {
            subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
            subscriber.onError(new IllegalStateException("Only one subscriber allowed"));
        }
        else {
            StreamedHttpMessage message = (StreamedHttpMessage) nettyHttpRequest.getNativeRequest();
            message.subscribe(this);
        }
    }

    @Override
    public final void onNext(ByteBufHolder message) {
        long receivedLength = this.receivedLength.addAndGet(message.content().readableBytes());

        if((advertisedLength != -1 && receivedLength > advertisedLength) || (receivedLength > requestMaxSize)) {
            fireExceedsLength(receivedLength, advertisedLength == -1 ? requestMaxSize : advertisedLength);
        }
        else {
            long serverMax = configuration.getMultipart().getMaxFileSize();
            if( receivedLength > serverMax ) {
                fireExceedsLength(receivedLength, serverMax);
            }
            else {
                publishMessage(message);
            }
        }
    }

    protected void fireExceedsLength(long receivedLength, long expected) {
        parentSubscription.cancel();
        onError(new ContentLengthExceededException(expected, receivedLength));
    }

    protected boolean verifyState(Subscriber<? super T> subscriber) {
        if(subscriber == null) {
            throw new IllegalStateException("No subscriber present!");
        }

        boolean hasParent = parentSubscription != null;
        if(!hasParent) {
            subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
            subscriber.onError(new IllegalStateException("Upstream publisher must be subscribed to first"));
        }
        return hasParent;
    }

    protected abstract void publishMessage(ByteBufHolder message);
}
