package org.particleframework.http.server.netty;

import com.typesafe.netty.http.StreamedHttpMessage;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import org.particleframework.http.exceptions.ContentLengthExceededException;
import org.particleframework.http.server.HttpServerConfiguration;
import org.particleframework.core.async.processor.SingleThreadedBufferingProcessor;
import org.reactivestreams.Subscriber;

import java.util.concurrent.atomic.AtomicLong;



/**
 * This class will handle subscribing to a stream of {@link HttpContent}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultHttpContentProcessor extends SingleThreadedBufferingProcessor<ByteBufHolder, ByteBufHolder> implements HttpContentProcessor<ByteBufHolder> {
    protected final NettyHttpRequest nettyHttpRequest;
    protected final ChannelHandlerContext ctx;
    protected final HttpServerConfiguration configuration;
    protected final long advertisedLength;
    protected final long requestMaxSize;
    protected final StreamedHttpMessage streamedHttpMessage;
    protected final AtomicLong receivedLength = new AtomicLong();

    public DefaultHttpContentProcessor(NettyHttpRequest<?> nettyHttpRequest, HttpServerConfiguration configuration) {
        this.nettyHttpRequest = nettyHttpRequest;
        HttpRequest nativeRequest = nettyHttpRequest.getNativeRequest();
        if(!(nativeRequest instanceof StreamedHttpMessage)) {
            throw new IllegalStateException("Streamed HTTP message expected");
        }
        this.streamedHttpMessage = (StreamedHttpMessage)nativeRequest;
        this.configuration = configuration;
        this.requestMaxSize = configuration.getMaxRequestSize();
        this.ctx = nettyHttpRequest.getChannelHandlerContext();
        this.advertisedLength = nettyHttpRequest.getContentLength();
    }

    protected void publishVerifiedContent(ByteBufHolder message) {
        currentSubscriber().ifPresent(subscriber -> subscriber.onNext(message));
    }

    @Override
    protected void onMessage(ByteBufHolder message) {
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
                publishVerifiedContent(message);
            }
        }
    }

    @Override
    public void subscribe(Subscriber<? super ByteBufHolder> subscriber) {
        StreamedHttpMessage message = (StreamedHttpMessage) nettyHttpRequest.getNativeRequest();
        message.subscribe(this);
        super.subscribe(subscriber);
    }

    protected void fireExceedsLength(long receivedLength, long expected) {
        state = BackPressureState.DONE;
        parentSubscription.cancel();
        currentSubscriber().ifPresent(subscriber -> subscriber.onError(new ContentLengthExceededException(expected, receivedLength)));
    }

}
