package io.micronaut.http.client

import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.stream.AvailableByteArrayBody
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class StreamedPiecesSpec extends Specification {

    private static CloseableByteBody body(String text) {
        return AvailableByteArrayBody.create(ByteArrayBufferFactory.INSTANCE, text.getBytes(StandardCharsets.UTF_8))
    }

    private static String text(ByteBuffer<?> buffer) {
        return buffer.toString(StandardCharsets.UTF_8)
    }

    def "the publisher of lines emits each line"() {
        given:
        def pieces = new StreamedPieces(body('a\nb\n'), true, 1024)

        expect:
        Flux.from(pieces.publisher()).map(StreamedPiecesSpec::text).collectList().block() == ['a', 'b']
    }

    def "the publisher of bytes emits the body"() {
        given:
        def pieces = new StreamedPieces(body('abc'), false, 1024)

        expect:
        Flux.from(pieces.publisher()).map(StreamedPiecesSpec::text).collectList().block().join('') == 'abc'
    }

    def "the publisher is claimed once"() {
        given:
        def pieces = new StreamedPieces(body('abc'), false, 1024)
        pieces.publisher()

        when:
        pieces.publisher()

        then:
        thrown(IllegalStateException)

        when:
        pieces.next()

        then:
        thrown(IllegalStateException)
    }

    def "the pulled lines are read with next"() {
        given:
        def pieces = new StreamedPieces(body('a\nb\n'), true, 1024)

        expect:
        text(pieces.next().toCompletableFuture().get(5, TimeUnit.SECONDS).get()) == 'a'
        text(pieces.next().toCompletableFuture().get(5, TimeUnit.SECONDS).get()) == 'b'
        !pieces.next().toCompletableFuture().get(5, TimeUnit.SECONDS).isPresent()

        when:
        pieces.publisher()

        then:
        thrown(IllegalStateException)

        cleanup:
        pieces.closeAsync().toCompletableFuture().get(5, TimeUnit.SECONDS)
    }

    def "the pulled bytes are read with forEach"() {
        given:
        def pieces = new StreamedPieces(body('abc'), false, 1024)
        StringBuilder read = new StringBuilder()

        when:
        pieces.forEach({ ByteBuffer<?> b ->
            read.append(text(b))
            return java.util.concurrent.CompletableFuture.completedFuture(null)
        }).toCompletableFuture().get(5, TimeUnit.SECONDS)

        then:
        read.toString() == 'abc'

        cleanup:
        pieces.close()
    }

    def "closing unread pieces closes the body"() {
        given:
        CloseableByteBody body = Mock(CloseableByteBody)
        def pieces = new StreamedPieces(body, false, 1024)

        when:
        pieces.close()
        pieces.close()

        then:
        1 * body.close()

        when:
        pieces.publisher()

        then:
        thrown(IllegalStateException)
    }
}
