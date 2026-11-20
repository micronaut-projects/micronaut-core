package io.micronaut.http.server.netty.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OwnedBufferTest {

    @Test
    void closeReleasesAnUntakenBuffer() {
        ByteBuf buf = Unpooled.buffer(4);
        try (OwnedBuffer ignored = new OwnedBuffer(buf)) {
            assertEquals(1, buf.refCnt());
        }
        assertEquals(0, buf.refCnt());
    }

    @Test
    void closeDoesNotReleaseATakenBuffer() {
        ByteBuf buf = Unpooled.buffer(4);
        ByteBuf taken;
        try (OwnedBuffer owned = new OwnedBuffer(buf)) {
            taken = owned.take();
        }
        assertSame(buf, taken);
        assertEquals(1, buf.refCnt());
        buf.release();
    }

    @Test
    void takeTwiceFails() {
        ByteBuf buf = Unpooled.buffer(4);
        OwnedBuffer owned = new OwnedBuffer(buf);
        owned.take();
        assertThrows(IllegalStateException.class, owned::take);
        owned.close();
        assertEquals(1, buf.refCnt());
        buf.release();
    }

    @Test
    void takeAfterCloseFails() {
        ByteBuf buf = Unpooled.buffer(4);
        OwnedBuffer owned = new OwnedBuffer(buf);
        owned.close();
        assertThrows(IllegalStateException.class, owned::take);
        assertEquals(0, buf.refCnt());
    }

    @Test
    void closeTwiceReleasesOnce() {
        ByteBuf buf = Unpooled.buffer(4);
        buf.retain();
        OwnedBuffer owned = new OwnedBuffer(buf);
        owned.close();
        owned.close();
        assertEquals(1, buf.refCnt());
        buf.release();
    }

    @Test
    void anExceptionReleasesTheBufferOnce() {
        ByteBuf buf = Unpooled.buffer(4);
        buf.retain();
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> {
            try (OwnedBuffer ignored = new OwnedBuffer(buf)) {
                throw new IllegalStateException("boom");
            }
        });
        assertEquals("boom", failure.getMessage());
        assertEquals(1, buf.refCnt());
        buf.release();
    }

    @Test
    void anExceptionAfterTheHandOffDoesNotRelease() {
        ByteBuf buf = Unpooled.buffer(4);
        ByteBuf[] receiver = new ByteBuf[1];
        assertThrows(IllegalStateException.class, () -> {
            try (OwnedBuffer owned = new OwnedBuffer(buf)) {
                receiver[0] = owned.take();
                throw new IllegalStateException("boom");
            }
        });
        assertSame(buf, receiver[0]);
        assertEquals(1, buf.refCnt());
        buf.release();
    }
}
