package io.micronaut.json.body

import io.micronaut.core.io.buffer.ReadBuffer
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.Unpooled
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * A composite input chunk is split like the same bytes arriving as separate chunks.
 */
class JsonChunkedProcessorCompositeSpec extends Specification {
    static final String TEXT = '[{"a":"x,y}"},{"b":[1,2,{"c":"\\"q\\""}]},"s",42 ,{"d":{}}]'

    private static ByteBuf direct(String s) {
        return Unpooled.directBuffer().writeBytes(s.getBytes(StandardCharsets.UTF_8))
    }

    private static List<String> split(List<ByteBuf> chunks) {
        def processor = new JsonChunkedProcessor()
        processor.counter.unwrapTopLevelArray()
        List<String> values = []
        JsonChunkedFlux.process(processor, Flux.fromIterable(chunks)).doOnNext {
            // consumes the value, which releases it
            values << ((ReadBuffer) it).toString(StandardCharsets.UTF_8)
        }.blockLast()
        return values
    }

    def 'values that span the components of a composite chunk are split correctly'() {
        given:
        def text = TEXT
        def bounds = [0] + cuts + [text.length()]
        def components = (0..<bounds.size() - 1).collect { direct(text.substring(bounds[it], bounds[it + 1])) }
        def composite = ByteBufAllocator.DEFAULT.compositeDirectBuffer()
        components.each { composite.addComponent(true, it) }

        when:
        def values = split([composite])

        then:
        values == ['{"a":"x,y}"}', '{"b":[1,2,{"c":"\\"q\\""}]}', '"s"', '42', '{"d":{}}']
        composite.refCnt() == 0
        components.every { it.refCnt() == 0 }

        where:
        cuts << [
            [5, 20],
            // every byte its own component
            (1..<TEXT.length()).toList(),
            // the cuts fall inside strings and escapes
            [8, 30, 31],
        ]
    }

    def 'a composite chunk followed by plain chunks continues the value in progress'() {
        given:
        def composite = ByteBufAllocator.DEFAULT.compositeDirectBuffer()
        composite.addComponent(true, direct('[{"n":1},'))
        composite.addComponent(true, direct('{"n":'))
        def tail = direct('2}]')

        when:
        def values = split([composite, tail])

        then:
        values == ['{"n":1}', '{"n":2}']
        composite.refCnt() == 0
        tail.refCnt() == 0
    }
}
