package org.particleframework.http.server.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class will handle subscribing to a stream of {@link HttpContent}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class HttpContentSubscriber implements Subscriber<HttpContent> {
    private static final Logger LOG = LoggerFactory.getLogger(ParticleNettyHttpServer.class);
    private final NettyHttpRequestContext requestContext;
    private final ChannelHandlerContext ctx;

    HttpContentSubscriber(NettyHttpRequestContext requestContext) {
        this.requestContext = requestContext;
        this.ctx = requestContext.getContext();
    }

    public HttpContentSubscriber(NettyHttpRequest request) {
        this(request.getRequestContext());
    }

    @Override
    public void onSubscribe(Subscription s) {
        s.request(Long.MAX_VALUE - 1);
    }

    @Override
    public void onNext(HttpContent httpContent) {
        requestContext
                .getRequest()
                .addContent(httpContent);
    }

    @Override
    public void onError(Throwable t) {
        if (LOG.isErrorEnabled()) {
            LOG.error("Error processing Request body: " + t.getMessage(), t);
        }
        requestContext
                .getResponseTransmitter()
                .sendBadRequest(ctx);
    }

    @Override
    public void onComplete() {
        requestContext.processRequestBody();
    }
}
