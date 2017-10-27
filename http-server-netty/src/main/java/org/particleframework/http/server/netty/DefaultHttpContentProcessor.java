package org.particleframework.http.server.netty;

import com.typesafe.netty.http.StreamedHttpMessage;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.multipart.HttpData;
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
    private final long partMaxSize;

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
        this.partMaxSize = configuration.getMultipart().getMaxFileSize();
    }

    @Override
    public final void subscribe(Subscriber<? super ByteBufHolder> downstreamSubscriber) {
        StreamedHttpMessage message = (StreamedHttpMessage) nettyHttpRequest.getNativeRequest();
        message.subscribe(this );
        super.subscribe(downstreamSubscriber);
    }

    @Override
    protected void onUpstreamMessage(ByteBufHolder message) {
        long receivedLength = this.receivedLength.addAndGet(resolveLength(message));

        if((advertisedLength != -1 && receivedLength > advertisedLength) || (receivedLength > requestMaxSize)) {
            fireExceedsLength(receivedLength, advertisedLength == -1 ? requestMaxSize : advertisedLength);
        }
        else {
            if(verifyPartDefinedSize(message)) {
                publishVerifiedContent(message);
            }
        }
    }

    private boolean verifyPartDefinedSize(ByteBufHolder message) {
        long partLength = message instanceof HttpData ? ((HttpData)message).definedLength() : -1;
        boolean validPart = partLength > partMaxSize;
        if(validPart) {
            fireExceedsLength(partLength, partMaxSize);
            return false;
        }
        return true;
    }

    private long resolveLength(ByteBufHolder message) {
        if(message instanceof HttpData) {
            return ((HttpData)message).length();
        }
        else {
            return message.content().readableBytes();
        }
    }

    private void fireExceedsLength(long receivedLength, long expected) {
        upstreamState = BackPressureState.DONE;
        upstreamSubscription.cancel();
        currentDownstreamSubscriber().ifPresent(subscriber -> subscriber.onError(new ContentLengthExceededException(expected, receivedLength)));
    }

    private void publishVerifiedContent(ByteBufHolder message) {
        currentDownstreamSubscriber().ifPresent(subscriber -> subscriber.onNext(message));
    }

}
