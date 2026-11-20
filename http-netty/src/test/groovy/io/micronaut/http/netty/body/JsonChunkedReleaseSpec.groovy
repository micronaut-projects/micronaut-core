package io.micronaut.http.netty.body

import io.micronaut.buffer.netty.NettyByteBufferFactory
import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.core.type.Argument
import io.micronaut.http.MediaType
import io.micronaut.http.body.StringBodyReader
import io.micronaut.http.body.TextPlainObjectBodyReader
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class JsonChunkedReleaseSpec extends Specification {
    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run()

    private static ByteBuf buf(String s) {
        return Unpooled.copiedBuffer(s, StandardCharsets.UTF_8)
    }

    private static Flux<ByteBuffer<?>> wrap(List<ByteBuf> bufs) {
        // like ByteBody, the source releases the chunks it did not emit
        return Flux.fromIterable(bufs)
                .doOnDiscard(ByteBuf, ByteBuf::release)
                .map(b -> (ByteBuffer<?>) NettyByteBufferFactory.DEFAULT.wrap(b))
    }

    def 'an element that fails to decode is released'() {
        given:
        def reader = ctx.getBean(NettyJsonHandler)
        def bufs = [buf('[1,"a",3]')]

        when:
        Flux.from(reader.readChunked(Argument.INT, MediaType.APPLICATION_JSON_TYPE, null, wrap(bufs))).collectList().block()

        then:
        thrown(Exception)
        bufs.every { it.refCnt() == 0 }
    }

    def 'elements the downstream does not take are released'(List<String> input) {
        given:
        def reader = ctx.getBean(NettyJsonHandler)
        def bufs = input.collect { buf(it) }

        when:
        def result = Flux.from(reader.readChunked(Argument.INT, MediaType.APPLICATION_JSON_TYPE, null, wrap(bufs))).take(1).collectList().block()

        then:
        result == [1]
        bufs.every { it.refCnt() == 0 }

        where:
        input << [
                ['[1,2,3]'],
                ['[1,', '2,', '3]'],
                // the second element is buffered, incomplete, when the downstream cancels
                ['[1,{"a"', ':2}]'],
                ['[1,[2', ',3]', ']'],
        ]
    }

    def 'elements are released when the downstream cancels from another thread'() {
        given:
        def reader = ctx.getBean(NettyJsonHandler)
        def input = '[' + (1..200).join(',') + ']'
        def bufs = []

        when:
        for (int i = 0; i < 1000; i++) {
            def b = buf(input)
            bufs.add(b)
            def started = new CountDownLatch(1)
            def done = new CountDownLatch(1)
            def disposable = Flux.from(reader.readChunked(Argument.INT, MediaType.APPLICATION_JSON_TYPE, null, wrap([b])))
                    .subscribeOn(Schedulers.parallel())
                    .doOnNext { started.countDown() }
                    .doFinally { done.countDown() }
                    .subscribe()
            // cancel while the nodes are being emitted on the other thread
            started.await(10, TimeUnit.SECONDS)
            disposable.dispose()
            done.await(10, TimeUnit.SECONDS)
        }

        then:
        new PollingConditions(timeout: 10).eventually {
            assert bufs.every { it.refCnt() == 0 }
        }
    }

    def 'elements are released when the downstream fails'() {
        given:
        def reader = ctx.getBean(NettyJsonHandler)
        def bufs = [buf('[1,2,'), buf('3]')]

        when:
        Flux.from(reader.readChunked(Argument.INT, MediaType.APPLICATION_JSON_TYPE, null, wrap(bufs)))
                .map { throw new IllegalStateException('consumer failure') }
                .collectList().block()

        then:
        thrown(IllegalStateException)
        bufs.every { it.refCnt() == 0 }
    }

    def 'the buffered element is released when the upstream fails'() {
        given:
        def reader = ctx.getBean(NettyJsonHandler)
        def first = buf('[1,{"a"')

        when:
        Flux.from(reader.readChunked(Argument.INT, MediaType.APPLICATION_JSON_TYPE, null,
                Flux.concat(wrap([first]), Flux.error(new IOException('upstream failure'))))).collectList().block()

        then:
        def e = thrown(RuntimeException)
        e.cause instanceof IOException
        first.refCnt() == 0
    }

    def 'input with a syntax error is released'() {
        given:
        def reader = ctx.getBean(NettyJsonHandler)
        def bufs = [buf('[1,'), buf(',2]')]

        when:
        Flux.from(reader.readChunked(Argument.INT, MediaType.APPLICATION_JSON_TYPE, null, wrap(bufs))).collectList().block()

        then:
        thrown(Exception)
        bufs.every { it.refCnt() == 0 }
    }

    def 'a json-stream element that fails to decode is released'() {
        given:
        def reader = ctx.getBean(NettyJsonStreamHandler)
        def bufs = [buf('1\n"a"\n3')]

        when:
        Flux.from(reader.readChunked(Argument.INT, MediaType.APPLICATION_JSON_STREAM_TYPE, null, wrap(bufs))).collectList().block()

        then:
        thrown(Exception)
        bufs.every { it.refCnt() == 0 }
    }

    def 'json-stream list read releases the input on success only'() {
        given:
        def reader = ctx.getBean(NettyJsonStreamHandler)
        def ok = buf('1\n2\n3')
        def bad = buf(input)

        when:
        def result = reader.read(Argument.listOf(Integer), MediaType.APPLICATION_JSON_STREAM_TYPE, null, NettyByteBufferFactory.DEFAULT.wrap(ok))

        then:
        result == [1, 2, 3]
        ok.refCnt() == 0

        when:
        reader.read(Argument.listOf(Integer), MediaType.APPLICATION_JSON_STREAM_TYPE, null, NettyByteBufferFactory.DEFAULT.wrap(bad))

        then: 'the caller still owns the input it has to release on failure'
        thrown(Exception)
        bad.refCnt() == 1

        cleanup:
        bad.release()

        where:
        input << [
                '1\n"a"\n3',
                '1\n]',
        ]
    }

    def 'a text/plain chunk that fails to convert is released'() {
        given:
        def reader = ctx.getBean(TextPlainObjectBodyReader)
        def bufs = [buf('1'), buf('a'), buf('3')]

        when:
        Flux.from(reader.readChunked(Argument.INT, MediaType.TEXT_PLAIN_TYPE, null, wrap(bufs))).collectList().block()

        then:
        thrown(Exception)
        bufs[0].refCnt() == 0
        bufs[1].refCnt() == 0
    }

    def 'a string chunk with an unsupported charset is released'() {
        given:
        def reader = ctx.getBean(StringBodyReader)
        def bufs = [buf('a'), buf('b')]

        when:
        Flux.from(reader.readChunked(Argument.STRING, MediaType.of('text/plain;charset=bogus'), null, wrap(bufs))).collectList().block()

        then:
        thrown(Exception)
        bufs[0].refCnt() == 0
        bufs[1].refCnt() == 0
    }
}
