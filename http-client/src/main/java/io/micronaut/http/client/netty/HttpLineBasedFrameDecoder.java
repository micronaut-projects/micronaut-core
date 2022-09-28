package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;

/**
 * Variant of {@link LineBasedFrameDecoder} that accepts
 * {@link io.netty.handler.codec.http.HttpContent} data. Note: this handler removes itself when the
 * response has been consumed.
 */
@Internal
final class HttpLineBasedFrameDecoder extends LineBasedFrameDecoder {
    static final String NAME = ChannelPipelineCustomizer.HANDLER_MICRONAUT_SSE_EVENT_STREAM;

    private boolean ignoreOneLast = false;

    public HttpLineBasedFrameDecoder(int maxLength, boolean stripDelimiter, boolean failFast) {
        super(maxLength, stripDelimiter, failFast);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpResponse &&
            ((HttpResponse) msg).status().equals(HttpResponseStatus.CONTINUE)) {
            ignoreOneLast = true;
        }

        if (msg instanceof HttpContent) {
            super.channelRead(ctx, ((HttpContent) msg).content());
        } else {
            ctx.fireChannelRead(msg);
        }

        if (msg instanceof LastHttpContent) {
            if (ignoreOneLast) {
                ignoreOneLast = false;
            } else {
                // first, remove the handler so that LineBasedFrameDecoder flushes any further
                // data. Then forward the LastHttpContent.
                ctx.pipeline().remove(NAME);
                ctx.fireChannelRead(LastHttpContent.EMPTY_LAST_CONTENT);
            }
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        ctx.pipeline().addAfter(NAME, Wrap.NAME, Wrap.INSTANCE);
    }

    @Override
    protected void handlerRemoved0(ChannelHandlerContext ctx) {
        ctx.pipeline().remove(Wrap.NAME);
    }

    @Sharable
    private static class Wrap extends ChannelInboundHandlerAdapter {
        static final ChannelHandler INSTANCE = new Wrap();
        static final String NAME = ChannelPipelineCustomizer.HANDLER_MICRONAUT_SSE_CONTENT;

        @Override
        public void channelRead(@NonNull ChannelHandlerContext ctx, @NonNull Object msg) throws Exception {
            if (msg instanceof ByteBuf) {
                ByteBuf buffer = (ByteBuf) msg;
                // todo: this is necessary because downstream handlers sometimes do the
                //  `if (refcnt > 0) release` pattern. We should eventually fix that.
                ByteBuf copy = buffer.copy();
                buffer.release();
                ctx.fireChannelRead(new DefaultHttpContent(copy));
            } else {
                ctx.fireChannelRead(msg);
            }
        }
    }
}
