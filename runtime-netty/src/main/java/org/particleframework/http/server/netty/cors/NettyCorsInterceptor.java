package org.particleframework.http.server.netty.cors;

import io.netty.channel.*;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpUtil;
import org.particleframework.http.cors.CorsHandler;
import org.particleframework.http.cors.CorsRequest;
import org.particleframework.http.cors.CorsRequestHandler;
import org.particleframework.http.server.HttpServerConfiguration;

import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.util.ReferenceCountUtil.release;

/**
 * Default Netty CORS processor
 *
 * @author James Kleeh
 * @since 1.0
 */
public class NettyCorsInterceptor extends ChannelDuplexHandler {

    private CorsHandler corsHandler;
    private HttpRequest request;
    private CorsRequest requestContext;
    private boolean corsRequest = false;

    public NettyCorsInterceptor(HttpServerConfiguration.CorsConfiguration corsConfiguration) {
        this.corsHandler = new CorsHandler(corsConfiguration);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {

        if (msg instanceof HttpRequest) {
            request = (HttpRequest) msg;

            requestContext = new NettyCorsRequest(request);
            corsRequest = requestContext.isCorsRequest();

            if (corsRequest) {
                CorsRequestHandler requestHandler = new CorsRequestHandler() {
                    @Override
                    public void rejectRequest() {
                        release(request);
                        respond(ctx, request, new DefaultFullHttpResponse(request.protocolVersion(), FORBIDDEN));
                    }

                    @Override
                    public void preflightSuccess() {
                        final HttpResponse response = new DefaultFullHttpResponse(request.protocolVersion(), OK, true, false);
                        corsHandler.handleResponse(requestContext, new NettyCorsResponse(response));
                        release(request);
                        respond(ctx, request, response);
                    }

                    @Override
                    public void continueRequest() {
                        ctx.fireChannelRead(msg);
                    }
                };

                corsHandler.handleRequest(requestContext, requestHandler);
                return;
            }
        }

        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) throws Exception {
        if (msg instanceof HttpResponse && corsRequest) {
            final HttpResponse response = (HttpResponse) msg;
            NettyCorsResponse responseContext = new NettyCorsResponse(response);
            corsHandler.handleResponse(requestContext, responseContext);
        }

        ctx.writeAndFlush(msg, promise);
    }

    private static void respond(
            final ChannelHandlerContext ctx,
            final HttpRequest request,
            final HttpResponse response) {

        final boolean keepAlive = HttpUtil.isKeepAlive(request);

        HttpUtil.setKeepAlive(response, keepAlive);

        final ChannelFuture future = ctx.writeAndFlush(response);
        if (!keepAlive) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

}
