package io.micronaut.http.server.netty

import io.netty.buffer.ByteBufHolder
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultHttpContent
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class HttpContentProcessorSpec extends Specification {
    def 'simple forward'() {
        given:
        def out = []
        def processor = new HttpContentProcessorAsReactiveProcessor(new HttpContentProcessor() {
            @Override
            void add(ByteBufHolder data, Collection<Object> o) {
                o.add(data.content().toString(StandardCharsets.UTF_8))
            }

            @Override
            void complete(Collection<Object> o) {
                o.add("complete HCP")
            }
        })

        when:
        Flux.fromIterable(["1", "2", "3"])
                .map {new DefaultHttpContent(Unpooled.wrappedBuffer(it.getBytes(StandardCharsets.UTF_8)))  }
                .subscribe(processor)
        Flux.from(processor).subscribe(
                d -> out.add(d),
                e -> out.add(e),
                () -> out.add("complete PROC"))

        then:
        out == ["1", "2", "3", "complete HCP", "complete PROC"]
    }
}
