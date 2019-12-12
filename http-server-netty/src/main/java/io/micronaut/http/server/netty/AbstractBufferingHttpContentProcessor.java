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
import io.micronaut.core.async.processor.SingleThreadedBufferingProcessor;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import io.micronaut.http.netty.stream.StreamedHttpMessage;
import io.micronaut.http.server.HttpServerConfiguration;
import io.netty.buffer.ByteBufHolder;
import io.netty.handler.codec.http.multipart.HttpData;
import org.reactivestreams.Subscriber;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Abtract implementation of the {@link HttpContentProcessor} interface that deals with limiting file upload sizes.
 *
 * @param <T> The type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public abstract class AbstractBufferingHttpContentProcessor<T> extends SingleThreadedBufferingProcessor<ByteBufHolder, T> implements HttpContentProcessor<T> {

    protected final NettyHttpRequest nettyHttpRequest;
    protected final long advertisedLength;
    protected final long requestMaxSize;
    protected final AtomicLong receivedLength = new AtomicLong();
    protected final HttpServerConfiguration configuration;
    private final long partMaxSize;

    /**
     * @param nettyHttpRequest The {@link NettyHttpRequest}
     * @param configuration    The {@link HttpServerConfiguration}
     */
    public AbstractBufferingHttpContentProcessor(NettyHttpRequest<?> nettyHttpRequest, HttpServerConfiguration configuration) {
        this.nettyHttpRequest = nettyHttpRequest;
        this.advertisedLength = nettyHttpRequest.getContentLength();
        this.requestMaxSize = configuration.getMaxRequestSize();
        this.configuration = configuration;
        this.partMaxSize = configuration.getMultipart().getMaxFileSize();
    }

    @Override
    public void subscribe(Subscriber<? super T> downstreamSubscriber) {
        super.subscribe(downstreamSubscriber);
        subscribeUpstream();
    }

    @Override
    protected final void doOnNext(ByteBufHolder message) {
        long receivedLength = this.receivedLength.addAndGet(resolveLength(message));

        if ((advertisedLength != -1 && receivedLength > advertisedLength) || (receivedLength > requestMaxSize)) {
            fireExceedsLength(receivedLength, advertisedLength == -1 ? requestMaxSize : advertisedLength);
        } else {
            onUpstreamMessage(message);
        }
    }

    /**
     * @param message The message
     * @return Whether the message has verified part size
     */
    protected boolean verifyPartDefinedSize(ByteBufHolder message) {
        long partLength = message instanceof HttpData ? ((HttpData) message).definedLength() : -1;
        boolean validPart = partLength > partMaxSize;
        if (validPart) {
            fireExceedsLength(partLength, partMaxSize);
            return false;
        }
        return true;
    }

    /**
     * @param receivedLength The received length
     * @param expected       The expected length
     */
    protected void fireExceedsLength(long receivedLength, long expected) {
        try {
            onError(new ContentLengthExceededException(expected, receivedLength));
        } finally {
            upstreamSubscription.cancel();
        }
    }

    private long resolveLength(ByteBufHolder message) {
        if (message instanceof HttpData) {
            return ((HttpData) message).length();
        } else {
            return message.content().readableBytes();
        }
    }

    private void subscribeUpstream() {
        StreamedHttpMessage message = (StreamedHttpMessage) nettyHttpRequest.getNativeRequest();
        message.subscribe(this);
    }
}
