package org.particleframework.http.server.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.particleframework.http.server.HttpServerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * Responsible for relaying the response to the client
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class NettyHttpResponseTransmitter {
    private static final Logger LOG = LoggerFactory.getLogger(ParticleNettyHttpServer.class);

    private final Charset defaultCharset;

    public NettyHttpResponseTransmitter(HttpServerConfiguration serverConfiguration) {
        defaultCharset = serverConfiguration.getDefaultCharset();
    }

    /**
     * Sends an HTTP Not found response and closed the channel
     *
     * @param channel The channel
     */
    public void sendNotFound(Channel channel) {
        channel.attr(ParticleNettyHttpServer.REQUEST_CONTEXT_KEY).set(null);
        DefaultHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
        channel.writeAndFlush(httpResponse)
                .addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * Send the given object as text to the channel
     *
     * @param context The channel
     * @param object  The object
     */
    public void sendText(ChannelHandlerContext context, Object object) {
        sendText(context, object, defaultCharset);
    }
    /**
     * Send the given object as text to the channel
     *
     * @param context The channel context
     * @param object  The object
     * @param charset The charset to use
     */
    public void sendText(ChannelHandlerContext context, Object object, Charset charset) {
        DefaultFullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        if(object instanceof CompletableFuture) {
            CompletableFuture<?> future = (CompletableFuture) object;
            future.whenCompleteAsync((o, throwable) -> {
                if(throwable != null) {
                    if(LOG.isErrorEnabled()) {
                        LOG.error("Error executing future: " + throwable.getMessage(), throwable);
                    }
                    sendServerError(context);
                }
                else {
                    sendText(context, o, charset);
                }
            }, context.channel().eventLoop());
        }
        else {

            httpResponse.content()
                    .writeCharSequence(
                            object.toString(),
                            charset);
            context.writeAndFlush(httpResponse)
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * Sends an HTTP 500 SERVER ERROR
     *
     * @param ctx The channel context
     */
    public void sendServerError(ChannelHandlerContext ctx) {
        DefaultFullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        ctx.channel()
                .writeAndFlush(httpResponse)
                .addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * Sends an HTTP 400 BAD REQUEST
     *
     * @param ctx The channel context
     */
    public void sendBadRequest(ChannelHandlerContext ctx) {
        DefaultFullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST);
        ctx.channel()
                .writeAndFlush(httpResponse)
                .addListener(ChannelFutureListener.CLOSE);

    }
}
