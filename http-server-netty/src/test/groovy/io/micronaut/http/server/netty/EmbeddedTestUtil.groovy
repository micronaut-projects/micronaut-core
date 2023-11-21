package io.micronaut.http.server.netty

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel

class EmbeddedTestUtil {
    static void advance(EmbeddedChannel... channels) {
        boolean advanced
        do {
            advanced = false
            for (EmbeddedChannel channel : channels) {
                if (channel.hasPendingTasks()) {
                    advanced = true
                    channel.runPendingTasks()
                }
                channel.checkException()
            }
        } while (advanced);
    }

    static void connect(EmbeddedChannel server, EmbeddedChannel client) {
        new ConnectionDirection(server, client).register()
        def csDir = new ConnectionDirection(client, server)
        csDir.register()
        // PipeliningServerHandler fires a read() before this method is called, so we don't see it.
        csDir.readPending = true
    }

    private static class ConnectionDirection {
        private static final Object FLUSH = new Object()

        final EmbeddedChannel source
        final EmbeddedChannel dest
        final Queue<Object> queue = new ArrayDeque<>()
        boolean readPending

        ConnectionDirection(EmbeddedChannel source, EmbeddedChannel dest) {
            this.source = source
            this.dest = dest
        }

        private void forwardLater(Object msg) {
            if (readPending || dest.config().isAutoRead()) {
                dest.eventLoop().execute(() -> forwardNow(msg))
                readPending = false
            } else {
                queue.add(msg)
            }
        }

        private void forwardNow(Object msg) {
            if (msg == FLUSH) {
                dest.flushInbound()
            } else {
                dest.writeOneInbound(msg)
            }
        }

        void register() {
            source.pipeline().addFirst(new ChannelOutboundHandlerAdapter() {
                @Override
                void write(ChannelHandlerContext ctx_, Object msg, ChannelPromise promise) throws Exception {
                    if (!(msg instanceof ByteBuf)) {
                        throw new IllegalArgumentException("Can only forward bytes!")
                    }
                    forwardLater(msg)
                    promise.setSuccess()
                }

                @Override
                void flush(ChannelHandlerContext ctx_) throws Exception {
                    forwardLater(FLUSH)
                }
            })
            dest.pipeline().addFirst(new ChannelOutboundHandlerAdapter() {
                @Override
                void read(ChannelHandlerContext ctx) throws Exception {
                    if (queue.isEmpty()) {
                        readPending = true
                    } else {
                        ctx.fireChannelRead(queue.poll())
                    }
                }
            })
        }
    }
}
