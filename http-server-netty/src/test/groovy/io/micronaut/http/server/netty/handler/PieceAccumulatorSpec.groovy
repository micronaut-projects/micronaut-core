package io.micronaut.http.server.netty.handler

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.Unpooled
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.Executor

class PieceAccumulatorSpec extends Specification {
    List<Runnable> tasks = []
    Executor loop = { Runnable r -> tasks.add(r) } as Executor
    RecordingOutput output = new RecordingOutput()
    PieceAccumulator accumulator = new PieceAccumulator(loop, ByteBufAllocator.DEFAULT, output)

    private void endTurn() {
        def pending = new ArrayList<>(tasks)
        tasks.clear()
        pending.each { it.run() }
    }

    def 'the acknowledged bytes leave together with the held buffer when it is drained at the end of the turn'() {
        when:
        accumulator.add(buf("ab"))
        accumulator.add(buf("cd"))

        then: 'one drain is scheduled for the turn, nothing is written yet'
        tasks.size() == 1
        output.writes.empty
        accumulator.unacknowledged() == 4

        when:
        int first = accumulator.acknowledge()
        accumulator.add(buf("ef"))
        int second = accumulator.acknowledge()
        int third = accumulator.acknowledge()
        accumulator.add(buf("g"))

        then: 'each byte is acknowledged once'
        first == 4
        second == 2
        third == 0
        accumulator.unacknowledged() == 1

        when:
        endTurn()

        then: 'the drained buffer carries the six acknowledged bytes'
        output.writes == [["abcdefg", false, 6]]
        output.turns == 1
        accumulator.unacknowledged() == 0
        accumulator.acknowledge() == 0
    }

    def 'a lone piece is handed over as it arrived, and a combined buffer is the accumulator own'() {
        given:
        def lone = buf("lone")
        def a = buf("a")
        def b = buf("b")

        when:
        accumulator.add(lone)
        endTurn()
        accumulator.add(a)
        accumulator.add(b)
        endTurn()

        then:
        output.buffers[0].is(lone)
        !output.buffers[1].is(a)
        output.buffers[1].capacity() == PieceAccumulator.AGGREGATION_LIMIT
        a.refCnt() == 0
        b.refCnt() == 0
    }

    def 'a large piece drains the held buffer with its acknowledgement and passes through unacknowledged'() {
        given:
        def large = Unpooled.buffer(2048).writeZero(2048)

        when:
        accumulator.add(buf("ab"))
        accumulator.acknowledge()
        accumulator.add(large)

        then:
        output.writes == [["ab", false, 2], ["\u0000" * 2048, false, 0]]
        output.buffers[1].is(large)
    }

    def 'a full buffer is drained at once, a piece that does not fit drains what is held first'() {
        when:
        8.times { accumulator.add(buf("x" * 1024)) }

        then:
        output.writes.size() == 1
        output.writes[0][0].length() == 8192

        when:
        7.times { accumulator.add(buf("y" * 1024)) }
        accumulator.acknowledge()
        accumulator.add(buf("z" * 1000))
        accumulator.add(buf("w" * 200))

        then: 'the 7168 acknowledged bytes go out with their count, the overflowing piece starts a new buffer'
        output.writes.size() == 2
        output.writes[1][0] == "y" * 7168 + "z" * 1000
        output.writes[1][2] == 7168
        accumulator.unacknowledged() == 200
    }

    def 'the final piece is merged with the held buffer and carries its acknowledgement'() {
        when:
        accumulator.add(buf("ab"))
        accumulator.acknowledge()
        accumulator.addLast(buf(last), merge)

        then:
        output.writes == expected

        where:
        last       | merge || expected
        "c"        | true  || [["abc", true, 2]]
        ""         | true  || [["ab", true, 2]]
        "c"        | false || [["ab", false, 2], ["c", true, 0]]
        "c" * 2048 | true  || [["ab", false, 2], ["c" * 2048, true, 0]]
    }

    def 'the accumulator closes after the final piece: later pieces and the scheduled drain do nothing'() {
        given:
        def late = buf("late")

        when:
        accumulator.add(buf("a"))
        accumulator.addLast(Unpooled.EMPTY_BUFFER, true)
        accumulator.add(late)
        endTurn()

        then:
        output.writes == [["a", true, 0]]
        output.turns == 0
        late.refCnt() == 0
    }

    def 'release frees the held buffer and the piece that a reentrant close leaves behind'() {
        given:
        def held = buf("a")
        def next = buf("n" * size)
        output.onWrite = { accumulator.release() }

        when:
        accumulator.add(held)
        accumulator.release()

        then:
        held.refCnt() == 0
        output.writes.empty

        when: 'the output closes the accumulator while it writes a drained buffer'
        def reentrant = new PieceAccumulator(loop, ByteBufAllocator.DEFAULT, output)
        accumulator = reentrant
        8.times { reentrant.add(buf("p" * 1000)) }
        reentrant.add(next)
        endTurn()

        then:
        output.writes.size() == 1
        next.refCnt() == 0
        reentrant.unacknowledged() == 0

        where:
        size << [1000, 2048]
    }

    private static ByteBuf buf(String s) {
        return Unpooled.copiedBuffer(s, StandardCharsets.UTF_8)
    }

    static class RecordingOutput implements PieceAccumulator.Output {
        List<List<Object>> writes = []
        List<ByteBuf> buffers = []
        int turns
        Runnable onWrite = {}

        @Override
        void writePiece(ByteBuf data, boolean last, int acknowledged) {
            buffers.add(data)
            writes.add([data.toString(StandardCharsets.UTF_8), last, acknowledged])
            data.release()
            onWrite.run()
        }

        @Override
        void turnEnded() {
            turns++
        }
    }
}
