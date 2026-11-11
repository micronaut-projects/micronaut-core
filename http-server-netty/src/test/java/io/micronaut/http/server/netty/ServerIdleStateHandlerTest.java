package io.micronaut.http.server.netty;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerIdleStateHandlerTest {
    @Test
    void voidPromiseStaysVoid() {
        PromiseRecorder recorder = new PromiseRecorder();
        EmbeddedChannel channel = new EmbeddedChannel(recorder, new ServerIdleStateHandler(1000, 1000, 1000, TimeUnit.MILLISECONDS));

        channel.writeAndFlush(Unpooled.copiedBuffer(new byte[]{1}), channel.voidPromise());
        channel.writeAndFlush(Unpooled.copiedBuffer(new byte[]{2}), channel.newPromise());

        assertEquals(List.of(true, false), recorder.voidPromises);
        channel.finishAndReleaseAll();
    }

    @Test
    void nettyHandlerUnvoidsThePromise() {
        // documents the allocation this handler avoids: the netty handler replaces the void promise
        PromiseRecorder recorder = new PromiseRecorder();
        EmbeddedChannel channel = new EmbeddedChannel(recorder, new IdleStateHandler(1000, 1000, 1000, TimeUnit.MILLISECONDS));

        channel.writeAndFlush(Unpooled.copiedBuffer(new byte[]{1}), channel.voidPromise());

        assertEquals(List.of(false), recorder.voidPromises);
        channel.finishAndReleaseAll();
    }

    @Test
    void writeIdleEventsFireAndWritesResetThem() {
        EventRecorder events = new EventRecorder();
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.freezeTime();
        channel.pipeline().addLast(new ServerIdleStateHandler(0, 1000, 0, TimeUnit.MILLISECONDS), events);

        channel.advanceTimeBy(600, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();
        assertTrue(events.states.isEmpty());

        channel.writeAndFlush(Unpooled.copiedBuffer(new byte[]{1}), channel.voidPromise());
        channel.advanceTimeBy(600, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();
        assertTrue(events.states.isEmpty(), "the write must count as activity");

        channel.advanceTimeBy(500, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();
        assertEquals(List.of(IdleState.WRITER_IDLE), events.states);
        assertTrue(events.first.get(0));

        channel.advanceTimeBy(1000, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();
        assertEquals(List.of(IdleState.WRITER_IDLE, IdleState.WRITER_IDLE), events.states);
        assertFalse(events.first.get(1));
        channel.finishAndReleaseAll();
    }

    @Test
    void allIdleEventsFireAndWritesResetThem() {
        EventRecorder events = new EventRecorder();
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.freezeTime();
        channel.pipeline().addLast(new ServerIdleStateHandler(0, 0, 1000, TimeUnit.MILLISECONDS), events);

        channel.advanceTimeBy(600, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();
        channel.writeAndFlush(Unpooled.copiedBuffer(new byte[]{1}), channel.voidPromise());
        channel.advanceTimeBy(600, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();
        assertTrue(events.states.isEmpty(), "the write must count as activity");

        channel.advanceTimeBy(500, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();
        assertEquals(List.of(IdleState.ALL_IDLE), events.states);
        channel.finishAndReleaseAll();
    }

    private static final class PromiseRecorder extends ChannelOutboundHandlerAdapter {
        final List<Boolean> voidPromises = new ArrayList<>();

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            voidPromises.add(promise.isVoid());
            ctx.write(msg, promise);
        }
    }

    private static final class EventRecorder extends ChannelInboundHandlerAdapter {
        final List<IdleState> states = new ArrayList<>();
        final List<Boolean> first = new ArrayList<>();

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof IdleStateEvent idle) {
                states.add(idle.state());
                first.add(idle.isFirst());
            }
        }
    }
}
