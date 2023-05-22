package io.micronaut.http.netty.body

import io.micronaut.buffer.netty.NettyByteBufferFactory
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.http.body.ChunkedMessageBodyReader
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class NettyJsonStreamHandlerSpec extends Specification {
    def parse(Class<? extends ChunkedMessageBodyReader<?>> readerType, Argument<?> type, String input, Object expected) {
        given:
        def ctx = ApplicationContext.run()
        def reader = ctx.getBean(readerType)

        when:
        def buf = NettyByteBufferFactory.DEFAULT.wrap(input.getBytes(StandardCharsets.UTF_8))
        def actual = Flux.from(reader.readChunked(type, null, null, Flux.just(buf)))
                .collectList()
                .block()
        then:
        actual == expected

        cleanup:
        ctx.close()

        where:
        readerType             | type                    | input              | expected
        NettyJsonHandler       | Argument.STRING         | '["foo","bar"]'    | ["foo", "bar"]
        NettyJsonHandler       | Argument.listOf(String) | '["foo","bar"]'    | [["foo", "bar"]]
        NettyJsonStreamHandler | Argument.STRING         | '"foo"\n"bar"'     | ["foo", "bar"]
        NettyJsonStreamHandler | Argument.listOf(String) | '["foo"]\n["bar"]' | [["foo"], ["bar"]]
    }
}
