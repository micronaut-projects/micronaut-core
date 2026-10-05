package io.micronaut.http.server.netty.handler

import io.micronaut.buffer.netty.NettyReadBufferFactory
import io.micronaut.core.io.buffer.ReadBuffer
import io.micronaut.http.body.stream.BufferConsumer
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.Unpooled
import io.netty.buffer.UnpooledByteBufAllocator
import io.netty.channel.DefaultEventLoop
import io.netty.channel.EventLoop
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class StreamingResponseWriterSpec extends Specification {
    EventLoop loop = new DefaultEventLoop()

    def cleanup() {
        loop.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync()
    }

    private <T> T onLoop(Closure<T> c) {
        T result = loop.submit(c).get(10, TimeUnit.SECONDS)
        loop.submit({} as Runnable).get(10, TimeUnit.SECONDS)
        return result
    }

    def 'signals from the event loop are written in order and terminate the response once'() {
        given:
        def sink = new RecordingSink()
        def upstream = new PipeliningServerHandlerSpec.RecordingUpstream()
        def writer = new StreamingResponseWriter(loop, sink)
        writer.attach(upstream)

        when:
        onLoop {
            writer.open()
            writer.add(piece("a"))
            writer.add(piece("b"))
            writer.addAndComplete(piece("c"))
            writer.complete()
            writer.error(new RuntimeException("late"))
        }

        then:
        sink.events == ["open", "last(abc)", "responseWritten"]
        sink.events.every { it != "fail" }
        sink.responseWritten == 1
        upstream.starts == 1
        writer.done
    }

    def 'signals from a foreign thread are serialized onto the event loop in order'() {
        given:
        def sink = new RecordingSink()
        def upstream = new PipeliningServerHandlerSpec.RecordingUpstream()
        def writer = new StreamingResponseWriter(loop, sink)
        writer.attach(upstream)
        onLoop { writer.open() }

        when:
        writer.add(piece("a"))
        writer.add(piece("b"))
        writer.complete()
        sink.done.await(10, TimeUnit.SECONDS)

        then:
        sink.events.first() == "open"
        sink.events.last() == "responseWritten"
        sink.events.count { it.startsWith("last(") } == 1
        sink.events[1..-2].collect { it.substring(it.indexOf('(') + 1, it.length() - 1) }.join('') == "ab"
        sink.threads.every { it == sink.threads[0] }
        onLoop { loop.inEventLoop(sink.threads[0]) }
    }

    def 'written bytes are reported as consumed while the sink is writable'() {
        given:
        def sink = new RecordingSink(writable: true)
        def upstream = new PipeliningServerHandlerSpec.RecordingUpstream()
        def writer = new StreamingResponseWriter(loop, sink)
        writer.attach(upstream)
        onLoop { writer.open() }

        when:
        onLoop {
            writer.add(piece("abc"))
            writer.add(piece("de"))
        }
        then: 'writable sinks can accept pieces into the bounded aggregate'
        upstream.consumed == 5
        upstream.consumptions == 2

        when: 'the sink cannot take more, so the bytes are only reported when it can again'
        sink.writable = false
        onLoop {
            writer.add(piece("fg"))
            writer.add(piece("h"))
        }
        then:
        upstream.consumed == 5

        when:
        onLoop { writer.onWritable() }
        then:
        upstream.consumed == 8
        upstream.consumptions == 3

        when: 'a sink that confirms its writes itself takes the unreported bytes'
        onLoop { writer.add(piece("ijk")) }
        long taken = onLoop { writer.takeUnconsumedBytes() }
        then:
        taken == 3
        upstream.consumed == 8

        when:
        onLoop { writer.bytesConsumed(taken) }
        then:
        upstream.consumed == 11
        onLoop { writer.takeUnconsumedBytes() } == 0

        when: 'a completed response reports nothing more'
        onLoop {
            writer.complete()
            writer.onWritable()
        }
        then:
        upstream.consumed == 11
        upstream.consumptions == 4
    }

    def 'markResponseWritten is idempotent'() {
        given:
        def sink = new RecordingSink()
        def writer = new StreamingResponseWriter(loop, sink)
        writer.attach(new PipeliningServerHandlerSpec.RecordingUpstream())

        when:
        onLoop {
            writer.open()
            writer.markResponseWritten()
            writer.markResponseWritten()
            writer.complete()
            writer.dispose()
            writer.markResponseWritten()
        }

        then:
        sink.responseWritten == 1
        sink.events == ["open", "responseWritten", "last()"]
    }

    def 'data and completion that arrive early are held back until the response is opened'() {
        given:
        def sink = new RecordingSink(writable: true)
        def upstream = new PipeliningServerHandlerSpec.RecordingUpstream()
        def writer = new StreamingResponseWriter(loop, sink)
        writer.attach(upstream)
        def a = Unpooled.copiedBuffer("a", StandardCharsets.UTF_8)

        when:
        onLoop {
            writer.add(piece(a))
            writer.add(piece("b"))
        }
        then: 'nothing is written and the upstream is not started'
        sink.events.empty
        upstream.starts == 0
        upstream.consumed == 0
        !writer.done

        when:
        onLoop { writer.open() }
        then: 'the head goes first, then the early pieces, and their bytes are consumed after the start'
        sink.events == ["open", "write(ab)"]
        upstream.starts == 1
        upstream.consumed == 2
        a.refCnt() == 0

        when: 'a completion that arrives early is applied at open'
        def sink2 = new RecordingSink()
        def upstream2 = new PipeliningServerHandlerSpec.RecordingUpstream()
        def writer2 = new StreamingResponseWriter(loop, sink2)
        writer2.attach(upstream2)
        onLoop {
            writer2.add(piece("c"))
            writer2.complete()
        }
        then:
        sink2.events.empty
        !writer2.done

        when:
        onLoop { writer2.open() }
        then:
        sink2.events == ["open", "last(c)", "responseWritten"]
        upstream2.starts == 1
        writer2.done
    }

    def 'an error that arrives early is handled when the response is opened, without writing anything'() {
        given:
        def sink = new RecordingSink()
        def upstream = new PipeliningServerHandlerSpec.RecordingUpstream()
        def writer = new StreamingResponseWriter(loop, sink)
        writer.attach(upstream)
        def a = Unpooled.copiedBuffer("a", StandardCharsets.UTF_8)
        def failure = new RuntimeException("failed")

        when:
        onLoop {
            writer.add(piece(a))
            writer.error(failure)
        }
        then:
        sink.events.empty
        a.refCnt() == 1

        when:
        onLoop { writer.open() }
        then:
        sink.events == ["fail", "responseWritten"]
        sink.failure.is(failure)
        a.refCnt() == 0
        upstream.starts == 0
        writer.done
    }

    def 'an early error without data fails the response without opening it or starting the upstream'() {
        given:
        def sink = new RecordingSink()
        def upstream = new PipeliningServerHandlerSpec.RecordingUpstream()
        def writer = new StreamingResponseWriter(loop, sink)
        writer.attach(upstream)
        def failure = new RuntimeException("failed")

        when:
        onLoop { writer.error(failure) }
        then:
        sink.events.empty
        !writer.done

        when:
        onLoop {
            writer.open()
            writer.open()
        }
        then:
        sink.events == ["fail", "responseWritten"]
        sink.failure.is(failure)
        upstream.starts == 0
        writer.done
    }

    def 'an early completion without data opens and terminates the response'() {
        given:
        def sink = new RecordingSink()
        def upstream = new PipeliningServerHandlerSpec.RecordingUpstream()
        def writer = new StreamingResponseWriter(loop, sink)
        writer.attach(upstream)

        when:
        onLoop { writer.complete() }
        then:
        sink.events.empty
        !writer.done

        when:
        onLoop {
            writer.open()
            writer.open()
        }
        then:
        sink.events == ["open", "last()", "responseWritten"]
        upstream.starts == 1
        writer.done
    }

    def 'an early failure takes precedence over an early completion in either order'() {
        given:
        def sink = new RecordingSink()
        def upstream = new PipeliningServerHandlerSpec.RecordingUpstream()
        def writer = new StreamingResponseWriter(loop, sink)
        writer.attach(upstream)
        def data = Unpooled.copiedBuffer("a", StandardCharsets.UTF_8)
        def failure = new RuntimeException("failed")

        when:
        onLoop {
            writer.add(piece(data))
            if (errorFirst) {
                writer.error(failure)
                writer.complete()
            } else {
                writer.complete()
                writer.error(failure)
            }
            writer.open()
        }

        then:
        sink.events == ["fail", "responseWritten"]
        sink.failure.is(failure)
        data.refCnt() == 0
        upstream.starts == 0

        where:
        errorFirst << [true, false]
    }

    def 'a dispose from within the replay of early data skips the early completion and releases the rest'() {
        given:
        def sink = new RecordingSink()
        def upstream = new PipeliningServerHandlerSpec.RecordingUpstream()
        def writer = new StreamingResponseWriter(loop, sink)
        writer.attach(upstream)
        sink.afterWrite = { writer.dispose() }
        def rest = Unpooled.copiedBuffer("r", StandardCharsets.UTF_8)

        when:
        onLoop {
            // eight pieces fill the accumulator, so the replay writes them before the last one
            8.times { writer.add(piece("p" * 1024)) }
            writer.add(piece(rest))
            writer.complete()
            writer.open()
        }

        then:
        sink.events.size() == 3
        sink.events[0] == "open"
        sink.events[1].startsWith("write(")
        sink.events[2] == "responseWritten"
        sink.responseWritten == 1
        rest.refCnt() == 0
        writer.done
    }

    def 'an error after completion is ignored, and data after completion is released'() {
        given:
        def sink = new RecordingSink()
        def writer = new StreamingResponseWriter(loop, sink)
        writer.attach(new PipeliningServerHandlerSpec.RecordingUpstream())
        def late = Unpooled.copiedBuffer("late", StandardCharsets.UTF_8)

        when:
        onLoop {
            writer.open()
            writer.complete()
            writer.error(new RuntimeException("after complete"))
            writer.add(piece(late))
            writer.complete()
        }

        then:
        sink.events == ["open", "last()", "responseWritten"]
        sink.failure == null
        late.refCnt() == 0
    }

    def 'a failure while open is reported once, and completion after it is ignored'() {
        given:
        def sink = new RecordingSink()
        def writer = new StreamingResponseWriter(loop, sink)
        writer.attach(new PipeliningServerHandlerSpec.RecordingUpstream())

        when:
        onLoop {
            writer.open()
            writer.add(piece("a"))
            writer.error(new RuntimeException("failed"))
            writer.complete()
            writer.error(new RuntimeException("again"))
        }

        then:
        sink.events == ["open", "write(a)", "fail", "responseWritten"]
        writer.done
    }

    def 'dispose releases the held pieces and reports the response as written'() {
        given:
        def sink = new RecordingSink()
        def writer = new StreamingResponseWriter(loop, sink)
        writer.attach(new PipeliningServerHandlerSpec.RecordingUpstream())
        def early = Unpooled.copiedBuffer("early", StandardCharsets.UTF_8)
        def late = Unpooled.copiedBuffer("late", StandardCharsets.UTF_8)

        when:
        onLoop {
            writer.add(piece(early))
            writer.dispose()
            writer.add(piece(late))
            writer.complete()
            writer.error(new RuntimeException("after dispose"))
            writer.open()
        }

        then:
        sink.events == ["responseWritten"]
        early.refCnt() == 0
        late.refCnt() == 0
        writer.done
    }

    def 'aggregation is bounded and preserves order at exact and overflowing boundaries'() {
        given:
        def sink = new RecordingSink()
        def writer = new StreamingResponseWriter(loop, sink)
        writer.attach(new PipeliningServerHandlerSpec.RecordingUpstream())
        def buffers = (0..<20).collect { Unpooled.copiedBuffer("$it".take(1) * size, StandardCharsets.UTF_8) }
        def expected = buffers.collect { it.toString(StandardCharsets.UTF_8) }.join('')

        when:
        onLoop {
            writer.open()
            buffers.each { writer.add(piece(it)) }
            writer.addAndComplete(piece("end"))
        }

        then:
        sink.sizes.every { it <= 8192 }
        sink.sizes.sum() == expected.length() + 3
        sink.events[1..-2].collect { it.substring(it.indexOf('(') + 1, it.length() - 1) }.join('') == expected + "end"
        buffers.every { it.refCnt() == 0 }
        sink.responseWritten == 1

        where:
        size << [1, 1000, 1024]
    }

    def 'a large piece bypasses aggregation without copying and keeps adjacent pieces ordered'() {
        given:
        def sink = new RecordingSink()
        def writer = new StreamingResponseWriter(loop, sink)
        writer.attach(new PipeliningServerHandlerSpec.RecordingUpstream())
        def large = Unpooled.buffer(16384).writeZero(16384)

        when:
        onLoop {
            writer.open()
            writer.add(piece("a"))
            writer.add(piece(large))
            writer.addAndComplete(piece("b"))
        }

        then:
        sink.sizes == [1, 16384, 1]
        sink.buffers[1].is(large)
        large.refCnt() == 0
    }

    def 'a lone piece drains without completion or another piece and without copying'() {
        given:
        def sink = new RecordingSink(writable: true)
        def upstream = new PipeliningServerHandlerSpec.RecordingUpstream()
        def writer = new StreamingResponseWriter(loop, sink)
        writer.attach(upstream)
        def data = Unpooled.copiedBuffer("first", StandardCharsets.UTF_8)

        when:
        onLoop {
            writer.open()
            writer.add(piece(data))
            assert upstream.consumed == 5
            assert sink.events == ["open"]
        }

        then:
        sink.events == ["open", "write(first)"]
        sink.buffers[0].is(data)
        upstream.consumed == 5
        !writer.done
    }

    def 'a protocol acknowledgement excludes the aggregate still held for a later batch'() {
        given:
        def sink = new RecordingSink()
        def writer = new StreamingResponseWriter(loop, sink)
        writer.attach(new PipeliningServerHandlerSpec.RecordingUpstream())
        long taken

        when:
        onLoop {
            writer.open()
            writer.add(piece("x" * 2048))
            writer.add(piece("small"))
            taken = writer.takeUnconsumedBytes()
        }

        then:
        taken == 2048
        onLoop { writer.takeUnconsumedBytes() } == 5
    }

    def 'dispose releases an open aggregate and its scheduled drain becomes harmless'() {
        given:
        def sink = new RecordingSink()
        def writer = new StreamingResponseWriter(loop, sink)
        writer.attach(new PipeliningServerHandlerSpec.RecordingUpstream())
        def a = Unpooled.copiedBuffer("a", StandardCharsets.UTF_8)
        def b = Unpooled.copiedBuffer("b", StandardCharsets.UTF_8)
        ByteBuf aggregate

        when:
        onLoop {
            writer.open()
            writer.add(piece(a))
            writer.add(piece(b))
            aggregate = writer.accumulator.held
            writer.dispose()
        }

        then:
        sink.events == ["open", "responseWritten"]
        a.refCnt() == 0
        b.refCnt() == 0
        aggregate.refCnt() == 0
    }

    def 'cancellation from a sink write releases the following piece instead of retaining it'() {
        given:
        def sink = new RecordingSink()
        def writer = new StreamingResponseWriter(loop, sink)
        writer.attach(new PipeliningServerHandlerSpec.RecordingUpstream())
        sink.afterWrite = { writer.dispose() }
        def next = Unpooled.copiedBuffer("n" * size, StandardCharsets.UTF_8)

        when:
        onLoop {
            writer.open()
            8.times { writer.add(piece("p" * 1000)) }
            writer.add(piece(next))
        }

        then:
        writer.done
        sink.sizes == [8000]
        next.refCnt() == 0
        sink.responseWritten == 1

        where:
        size << [1000, 2048]
    }

    def 'a protocol can preserve a separate terminal message'() {
        given:
        def sink = new RecordingSink(mergeLast: false)
        def writer = new StreamingResponseWriter(loop, sink)
        writer.attach(new PipeliningServerHandlerSpec.RecordingUpstream())

        when:
        onLoop {
            writer.open()
            writer.add(piece("a"))
            writer.add(piece("b"))
            writer.complete()
        }

        then:
        sink.events == ["open", "write(ab)", "last()", "responseWritten"]
    }

    def 'a writable aggregate is acknowledged once even when it is drained or completed'() {
        given:
        def sink = new RecordingSink(writable: true)
        def upstream = new PipeliningServerHandlerSpec.RecordingUpstream()
        def writer = new StreamingResponseWriter(loop, sink)
        writer.attach(upstream)

        when:
        onLoop {
            writer.open()
            20.times { writer.add(piece("x" * 1024)) }
        }
        onLoop { writer.complete() }

        then:
        upstream.consumed == 20480
        onLoop { writer.takeUnconsumedBytes() } == 0
        sink.sizes.sum() == 20480
    }

    def 'pieces from another allocator are combined into a buffer of the channel allocator'() {
        given:
        def sink = new RecordingSink()
        def channelAllocator = new UnpooledByteBufAllocator(true)
        def writer = new StreamingResponseWriter(loop, sink, channelAllocator)
        writer.attach(new PipeliningServerHandlerSpec.RecordingUpstream())
        // heap pieces, e.g. byte arrays of a Flux<byte[]>, from the shared unpooled allocator
        def a = Unpooled.wrappedBuffer("ab".getBytes(StandardCharsets.UTF_8))
        def b = Unpooled.wrappedBuffer("xcdx".getBytes(StandardCharsets.UTF_8), 1, 2)

        when:
        onLoop {
            writer.open()
            writer.add(piece(a))
            writer.add(piece(b))
            writer.complete()
        }

        then:
        sink.events == ["open", "last(abcd)", "responseWritten"]
        sink.buffers[0].alloc().is(channelAllocator)
        a.refCnt() == 0
        b.refCnt() == 0
    }

    def 'the HTTP/2 held frame: an aggregate is reported once the batch is confirmed when the stream is not writable'() {
        given:
        def upstream = new PipeliningServerHandlerSpec.RecordingUpstream()
        def sink = new HoldingSink(loop)
        def writer = new StreamingResponseWriter(loop, sink)
        sink.writer = writer
        writer.attach(upstream)
        onLoop { writer.open() }

        when: 'small pieces arrive in one turn while the stream is not writable'
        onLoop {
            writer.add(piece("a"))
            writer.add(piece("b"))
            writer.add(piece("c"))
        }
        settle()

        then: 'the aggregate became the held frame, was written with the batch, and nothing is reported yet'
        sink.transport == ["abc"]
        sink.unconfirmed == 3
        upstream.consumed == 0

        when: 'the transport confirms the batch'
        onLoop { sink.confirm() }

        then:
        upstream.consumed == 3
        upstream.consumptions == 1
    }

    def 'the HTTP/2 held frame: bytes accepted while writable are not reported again by the batch'() {
        given:
        def upstream = new PipeliningServerHandlerSpec.RecordingUpstream()
        def sink = new HoldingSink(loop)
        def writer = new StreamingResponseWriter(loop, sink)
        sink.writer = writer
        writer.attach(upstream)
        onLoop { writer.open() }

        when: 'two pieces are accepted while the stream is writable, then the window fills and two more arrive in the same turn'
        long acceptedInTurn = -1
        onLoop {
            sink.writable = true
            writer.add(piece("ab"))
            writer.add(piece("cd"))
            sink.writable = false
            writer.add(piece("ef"))
            writer.add(piece("gh"))
            acceptedInTurn = upstream.consumed
        }
        settle()

        then: 'the accepted bytes are reported on arrival, and the aggregate drained into the held frame carries them'
        acceptedInTurn == 4
        sink.transport == ["abcdefgh"]
        sink.unconfirmed == 4
        upstream.consumed == 4

        when: 'the batch is confirmed'
        onLoop { sink.confirm() }

        then: 'only the bytes that were not accepted are reported, so every byte is reported once'
        upstream.consumed == 8

        when: 'the stream becomes writable again'
        onLoop {
            sink.writable = true
            writer.onWritable()
        }

        then:
        upstream.consumed == 8
    }

    def 'the HTTP/2 held frame: a large piece held behind an accepted aggregate keeps the credits apart'() {
        given:
        def upstream = new PipeliningServerHandlerSpec.RecordingUpstream()
        def sink = new HoldingSink(loop)
        def writer = new StreamingResponseWriter(loop, sink)
        sink.writer = writer
        writer.attach(upstream)
        onLoop { writer.open() }

        when:
        onLoop {
            sink.writable = true
            writer.add(piece("a" * 100))
            sink.writable = false
            // drains the accepted aggregate into the held frame, then is held itself
            writer.add(piece("L" * 2048))
            writer.add(piece("b" * 100))
        }
        settle()

        then: 'the aggregate goes out first, the large piece next, and the last aggregate is the held frame of the batch'
        sink.transport.collect { it.length() } == [100, 2048, 100]
        sink.transport*.charAt(0) == ['a' as char, 'L' as char, 'b' as char]
        upstream.consumed == 100
        sink.unconfirmed == 2148

        when:
        onLoop { sink.confirm() }

        then:
        upstream.consumed == 2248
    }

    def 'the HTTP/2 held frame: the completion merges the aggregate into the final frame after the held frame'() {
        given:
        def upstream = new PipeliningServerHandlerSpec.RecordingUpstream()
        def sink = new HoldingSink(loop)
        def writer = new StreamingResponseWriter(loop, sink)
        sink.writer = writer
        writer.attach(upstream)
        onLoop { writer.open() }

        when:
        onLoop {
            writer.add(piece("L" * 2048))
            writer.add(piece("x"))
            writer.add(piece("y"))
            writer.addAndComplete(piece("z"))
        }
        settle()

        then:
        sink.transport.collect { it.length() } == [2048, 3]
        sink.transport[1] == "xyz"
        sink.ended
        sink.responseWritten == 1
        writer.done
    }

    def 'every byte is reported exactly once across writability changes, drains and confirmed batches'() {
        given:
        def upstream = new PipeliningServerHandlerSpec.RecordingUpstream()
        def sink = new HoldingSink(loop)
        def writer = new StreamingResponseWriter(loop, sink)
        sink.writer = writer
        writer.attach(upstream)
        onLoop { writer.open() }
        def random = new Random(42)
        long added = 0

        when:
        100.times { turn ->
            onLoop {
                (1 + random.nextInt(12)).times {
                    if (random.nextInt(4) == 0) {
                        toggleWritable(sink, writer)
                    }
                    int size = random.nextInt(5) == 0 ? 1025 + random.nextInt(3000) : 1 + random.nextInt(1024)
                    added += size
                    writer.add(piece("p" * size))
                }
            }
            settle()
            if (random.nextBoolean()) {
                onLoop { sink.confirm() }
            }
            assert upstream.consumed <= added
        }
        settle()
        onLoop {
            sink.confirm()
            sink.writable = true
            writer.onWritable()
        }

        then:
        upstream.consumed == added
        sink.transport.sum { it.length() } == added
        sink.transport.every { it.length() <= 8192 }
    }

    /**
     * Flip the writability of the sink, resuming the writer when it becomes writable, as the
     * HTTP/2 flow controller listener does.
     */
    private static void toggleWritable(HoldingSink sink, StreamingResponseWriter writer) {
        sink.writable = !sink.writable
        if (sink.writable) {
            writer.onWritable()
        }
    }

    private static ReadBuffer piece(String s) {
        return piece(Unpooled.copiedBuffer(s, StandardCharsets.UTF_8))
    }

    private static ReadBuffer piece(ByteBuf buf) {
        return NettyReadBufferFactory.of(ByteBufAllocator.DEFAULT).adapt(buf)
    }

    /**
     * Runs the tasks the event loop has queued, including tasks those tasks queue.
     */
    private void settle() {
        3.times { loop.submit({} as Runnable).get(10, TimeUnit.SECONDS) }
    }

    /**
     * Mirrors the HTTP/2 ResponseStreamer: it holds the last piece written in a turn and writes
     * it at the end of the turn, together with the earlier pieces of the batch. While the stream
     * is not writable, the batch's written bytes are taken at the end of the turn and reported
     * only when the transport confirms the batch ({@link #confirm()}).
     */
    static class HoldingSink implements StreamingResponseWriter.Sink {
        final EventLoop loop
        StreamingResponseWriter writer
        boolean writable = false
        ByteBuf held
        boolean batchScheduled
        /** What reached the transport, in order. */
        List<String> transport = []
        /** Bytes taken by ended batches, reported on confirm. */
        long unconfirmed
        boolean ended
        int responseWritten

        HoldingSink(EventLoop loop) {
            this.loop = loop
        }

        @Override
        void open() {
            // the head of the response is not part of the credit being tested
        }

        @Override
        void write(ByteBuf data, boolean last) {
            ByteBuf previous = held
            held = null
            if (!last) {
                held = data
                if (previous != null) {
                    send(previous)
                }
                if (!batchScheduled) {
                    batchScheduled = true
                    loop.execute { endBatch() }
                }
            } else {
                if (previous != null) {
                    if (data.isReadable()) {
                        send(previous)
                    } else {
                        data.release()
                        data = previous
                    }
                }
                send(data)
                ended = true
            }
        }

        private void send(ByteBuf data) {
            transport.add(data.toString(StandardCharsets.UTF_8))
            data.release()
        }

        void endBatch() {
            batchScheduled = false
            unconfirmed += writer.takeUnconsumedBytes()
            if (held != null) {
                send(held)
                held = null
            }
        }

        void confirm() {
            long n = unconfirmed
            unconfirmed = 0
            writer.bytesConsumed(n)
        }

        @Override
        boolean isWritable() {
            return writable
        }

        @Override
        void fail(Throwable t) {
            if (held != null) {
                held.release()
                held = null
            }
        }

        @Override
        void responseWritten() {
            responseWritten++
        }
    }

    static class RecordingSink implements StreamingResponseWriter.Sink {
        boolean writable = false
        boolean mergeLast = true
        Runnable afterWrite = {}
        List<String> events = new ArrayList<>()
        List<Integer> sizes = []
        List<ByteBuf> buffers = []
        List<Thread> threads = new ArrayList<>()
        int responseWritten = 0
        Throwable failure
        CountDownLatch done = new CountDownLatch(1)

        private void record(String event) {
            events.add(event)
            threads.add(Thread.currentThread())
        }

        @Override
        void open() {
            record("open")
        }

        @Override
        void write(ByteBuf data, boolean last) {
            sizes.add(data.readableBytes())
            buffers.add(data)
            String s = data.toString(StandardCharsets.UTF_8)
            data.release()
            record((last ? "last(" : "write(") + s + ")")
            afterWrite.run()
        }

        @Override
        boolean canMergeLast() {
            return mergeLast
        }

        @Override
        boolean isWritable() {
            return writable
        }

        @Override
        void fail(Throwable t) {
            failure = t
            record("fail")
        }

        @Override
        void responseWritten() {
            responseWritten++
            record("responseWritten")
            done.countDown()
        }
    }
}
