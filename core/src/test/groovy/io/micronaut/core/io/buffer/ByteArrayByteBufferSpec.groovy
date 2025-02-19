package io.micronaut.core.io.buffer

import spock.lang.Specification

import java.nio.charset.StandardCharsets

class ByteArrayByteBufferSpec extends Specification {

    void 'test creating a buffer'() {
        given:
        def bytes = 'abcdefghij'.bytes
        def buffer = new ByteArrayByteBuffer(bytes)

        expect:
        buffer.toByteArray() == bytes
        buffer.maxCapacity() == 10

        buffer.readableBytes() == 10
        buffer.readerIndex() == 0

        buffer.writableBytes() == 10
        buffer.writerIndex() == 0
    }

    void 'test copying a buffer'() {
        given:
        def bytes = 'abcdefghij'.bytes
        def buffer = new ByteArrayByteBuffer(bytes)

        when:
        def sliced = buffer.slice(1, 5)

        then:
        sliced.toByteArray() == 'bcdef'.bytes

        when:
        def expanded = new ByteArrayByteBuffer(buffer.toByteArray(), 20)

        then:
        expanded.readerIndex() == 0
        expanded.readableBytes() == 20
        expanded.toByteArray() == 'abcdefghij\0\0\0\0\0\0\0\0\0\0'.bytes

        when:
        def shrunk = new ByteArrayByteBuffer(buffer.toByteArray(), 5)

        then:
        shrunk.readerIndex() == 0
        shrunk.readableBytes() == 5
        shrunk.toByteArray() == 'abcde'.bytes
    }

    void 'test inputstream creation'() {
        given:
        def bytes = 'abcdefghij'.bytes
        def buffer = new ByteArrayByteBuffer(bytes)

        when:
        def stream = buffer.toInputStream()

        then:
        stream.readAllBytes() == bytes
    }

    void 'test writing'() {
        given:
        def bytes = 'abcdefghij'.bytes
        def buffer = new ByteArrayByteBuffer(bytes)

        when:
        buffer.write('12345'.bytes)

        then:
        buffer.toByteArray() == '12345fghij'.bytes
        buffer.readerIndex() == 0
        buffer.readableBytes() == 10

        buffer.writerIndex() == 5
        buffer.writableBytes() == 5

        when:
        buffer = new ByteArrayByteBuffer('abcdefghij'.bytes)
        buffer.writerIndex(5)
        buffer.write('T', StandardCharsets.UTF_8)

        then:
        buffer.toByteArray() == 'abcdeTghij'.bytes
        buffer.readerIndex() == 0
        buffer.readableBytes() == 10
        buffer.writerIndex() == 6
        buffer.writableBytes() == 4
    }

    void 'test reading'() {
        given:
        def bytes = 'abcdefghij'.bytes
        def buffer = new ByteArrayByteBuffer(bytes)
        def target = new byte[5]

        when:
        buffer.read(target)

        then:
        target == 'abcde'.bytes
        buffer.readerIndex() == 5
        buffer.readableBytes() == 5

        buffer.writerIndex() == 0
        buffer.writableBytes() == 10

        when:
        target = new byte[5]
        buffer = new ByteArrayByteBuffer('abcdefghij'.bytes)
        buffer.readerIndex(5)
        buffer.read(target, 3, 5)

        then:
        target == '\0\0\0fg'.bytes
        buffer.writerIndex() == 0
        buffer.writableBytes() == 10

        and: 'we only read 2 elements'
        buffer.readerIndex() == 7
        buffer.readableBytes() == 3
    }
}
