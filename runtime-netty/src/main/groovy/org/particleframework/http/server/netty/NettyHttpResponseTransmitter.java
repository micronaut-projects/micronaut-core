package org.particleframework.http.server.netty;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.nio.charset.Charset;

/**
 * Responsible for relaying the response to the client
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class NettyHttpResponseTransmitter {

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
     * @param channel The channel
     * @param object The object
     * @param charset The charset to use
     */
    public void sendText(Channel channel, Object object, Charset charset) {
        DefaultFullHttpResponse httpResponse = new DefaultFullHttpResponse (HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        httpResponse.content().writeCharSequence(
                object.toString(),
                charset);
        channel.writeAndFlush(httpResponse)
                .addListener(ChannelFutureListener.CLOSE);
    }

    public void sendContinue(ChannelHandlerContext ctx, Channel channel) {
        // TODO: error handling
        DefaultFullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE, Unpooled.EMPTY_BUFFER);
        channel.writeAndFlush(httpResponse)
                .addListener((future)-> {
            if(future.isSuccess()) {
                ctx.read();
            }
        });
    }

    public void sendServerError(ChannelHandlerContext ctx) {
        DefaultHttpResponse httpResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        ctx.channel().writeAndFlush(httpResponse)
                .addListener(ChannelFutureListener.CLOSE);
    }
}
