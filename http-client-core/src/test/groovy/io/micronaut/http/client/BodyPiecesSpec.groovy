package io.micronaut.http.client

import io.micronaut.core.io.buffer.ReadBufferFactory
import io.micronaut.http.client.exceptions.ContentLengthExceededException
import spock.lang.Specification
import spock.lang.Timeout

import java.nio.charset.StandardCharsets

class BodyPiecesSpec extends Specification {

    private static List<String> lines(List<String> pieces, long max = 1024) {
        def reader = BodyPieces.lineReader(max, { byte[] b -> new String(b, StandardCharsets.UTF_8) })
        List<String> lines = []
        for (String piece : pieces) {
            reader.read(ReadBufferFactory.getJdkFactory().adapt(piece.getBytes(StandardCharsets.UTF_8)))
            String line
            while ((line = reader.poll()) != null) {
                lines.add(line)
            }
        }
        reader.complete()
        String line
        while ((line = reader.poll()) != null) {
            lines.add(line)
        }
        reader.close()
        return lines
    }

    def "a line ends with a line feed, a carriage return, or both"() {
        expect:
        lines(pieces) == expected

        where:
        pieces                       | expected
        ['a\nb\n']                   | ['a', 'b']
        ['a\r\nb\r\n']               | ['a', 'b']
        ['a\rb\r']                   | ['a', 'b']
        ['a\r', '\nb\n']             | ['a', 'b']
        ['a\r', 'b\n']               | ['a', 'b']
        ['a\r\r\nb\n']               | ['a', '', 'b']
        ['a\n\nb\n']                 | ['a', '', 'b']
        ['a\nb']                     | ['a']
        ['ab', 'c', '\r', '\n', 'd\n'] | ['abc', 'd']
    }

    def "a line longer than the limit fails"() {
        when:
        lines(['abc', 'def', 'g\n'], 5)

        then:
        thrown(ContentLengthExceededException)
    }

    @Timeout(10)
    def "a long line in many pieces is read in linear time"() {
        given:
        int pieces = 65536
        String piece = 'x' * 64
        def reader = BodyPieces.lineReader(Long.MAX_VALUE, { byte[] b -> b.length })

        when:
        for (int i = 0; i < pieces; i++) {
            reader.read(ReadBufferFactory.getJdkFactory().adapt(piece.getBytes(StandardCharsets.UTF_8)))
        }
        reader.read(ReadBufferFactory.getJdkFactory().adapt('\n'.getBytes(StandardCharsets.UTF_8)))

        then:
        reader.poll() == pieces * 64
    }
}
