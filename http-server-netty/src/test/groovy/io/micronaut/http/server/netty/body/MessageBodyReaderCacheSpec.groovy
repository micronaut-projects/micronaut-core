package io.micronaut.http.server.netty.body

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.type.Argument
import io.micronaut.http.MediaType
import io.micronaut.http.body.AbstractMessageBodyHandlerRegistry
import io.micronaut.http.body.MessageBodyHandlerRegistry
import spock.lang.Specification

class MessageBodyReaderCacheSpec extends Specification {

    def 'distinct content types do not grow the reader and writer caches without bound'() {
        given:
        def ctx = ApplicationContext.run()
        def registry = ctx.getBean(MessageBodyHandlerRegistry)
        def readers = AbstractMessageBodyHandlerRegistry.getDeclaredField('readers')
        readers.accessible = true
        def writers = AbstractMessageBodyHandlerRegistry.getDeclaredField('writers')
        writers.accessible = true

        when:
        for (int i = 0; i < 3000; i++) {
            registry.findReader(Argument.of(Pojo), [MediaType.of("application/x-test-${i}")])
            registry.findWriter(Argument.of(Pojo), [MediaType.of("application/x-test-${i}")])
        }

        then:
        ((Map) readers.get(registry)).size() <= 1000
        ((Map) writers.get(registry)).size() <= 1000
        registry.findReader(Argument.of(Pojo), [MediaType.APPLICATION_JSON_TYPE]).isPresent()
        registry.findWriter(Argument.of(Pojo), [MediaType.APPLICATION_JSON_TYPE]).isPresent()

        cleanup:
        ctx.close()
    }

    @Introspected
    static class Pojo {
        String a
    }
}
