package org.particleframework.http.server.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import org.particleframework.web.router.RouteMatch;
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
    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpServer.class);
    private final NettyHttpRequest nettyHttpRequest;
    private final RouteMatch<Object> route;
    private final ChannelHandlerContext ctx;

    public HttpContentSubscriber(NettyHttpRequest<?> nettyHttpRequest) {
        this.nettyHttpRequest = nettyHttpRequest;
        this.route = nettyHttpRequest.getMatchedRoute();
        this.ctx = nettyHttpRequest.getChannelHandlerContext();
    }


    @Override
    public void onSubscribe(Subscription s) {
        s.request(Long.MAX_VALUE - 1);
    }

    @Override
    public void onNext(HttpContent httpContent) {
        nettyHttpRequest.addContent(httpContent);
    }

    @Override
    public void onError(Throwable t) {
        if (LOG.isErrorEnabled()) {
            LOG.error("Error processing Request body: " + t.getMessage(), t);
        }
        ctx.pipeline().fireExceptionCaught(t);
    }

    @Override
    public void onComplete() {
        route.execute();
    }
}
