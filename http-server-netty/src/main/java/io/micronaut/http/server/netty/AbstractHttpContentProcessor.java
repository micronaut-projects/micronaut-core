/*
 * Copyright 2017-2018 original authors
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

import com.typesafe.netty.http.StreamedHttpMessage;
import io.micronaut.core.async.processor.SingleSubscriberProcessor;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import io.micronaut.http.server.HttpServerConfiguration;
import io.netty.buffer.ByteBufHolder;
import org.reactivestreams.Subscriber;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Abtract implementation of the {@link HttpContentProcessor} interface that deals with limiting file upload sizes.
 *
 * @param <T> The type
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class AbstractHttpContentProcessor<T> extends SingleSubscriberProcessor<ByteBufHolder, T> implements HttpContentProcessor<T> {

    protected final NettyHttpRequest nettyHttpRequest;
    protected final long advertisedLength;
    protected final long requestMaxSize;
    protected final AtomicLong receivedLength = new AtomicLong();
    protected final HttpServerConfiguration configuration;

    /**
     * @param nettyHttpRequest The {@link NettyHttpRequest}
     * @param configuration    The {@link HttpServerConfiguration}
     */
    public AbstractHttpContentProcessor(NettyHttpRequest<?> nettyHttpRequest, HttpServerConfiguration configuration) {
        this.nettyHttpRequest = nettyHttpRequest;
        this.advertisedLength = nettyHttpRequest.getContentLength();
        this.requestMaxSize = configuration.getMaxRequestSize();
        this.configuration = configuration;
    }

    /**
     * Called after verifying the data of the message.
     *
     * @param message The message
     */
    protected abstract void onData(ByteBufHolder message);

    @Override
    protected final void doSubscribe(Subscriber<? super T> subscriber) {
        StreamedHttpMessage message = (StreamedHttpMessage) nettyHttpRequest.getNativeRequest();
        message.subscribe(this);
    }

    @Override
    protected final void doOnNext(ByteBufHolder message) {
        long receivedLength = this.receivedLength.addAndGet(message.content().readableBytes());

        if ((advertisedLength != -1 && receivedLength > advertisedLength) || (receivedLength > requestMaxSize)) {
            fireExceedsLength(receivedLength, advertisedLength == -1 ? requestMaxSize : advertisedLength);
        } else {
            long serverMax = configuration.getMultipart().getMaxFileSize();
            if (receivedLength > serverMax) {
                fireExceedsLength(receivedLength, serverMax);
            } else {
                onData(message);
            }
        }
    }

    private void fireExceedsLength(long receivedLength, long expected) {
        try {
            onError(new ContentLengthExceededException(expected, receivedLength));
        } finally {
            parentSubscription.cancel();
        }
    }
}
