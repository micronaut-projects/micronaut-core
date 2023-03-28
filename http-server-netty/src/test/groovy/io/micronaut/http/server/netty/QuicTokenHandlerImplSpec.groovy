package io.micronaut.http.server.netty

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.Unpooled
import spock.lang.Specification

class QuicTokenHandlerImplSpec extends Specification {
    def 'successful validation round-trip'() {
        given:
        def handler = new QuicTokenHandlerImpl(ByteBufAllocator.DEFAULT)
        def cid = Unpooled.wrappedBuffer("foobar".getBytes())

        when:
        ByteBuf token = Unpooled.buffer()
        handler.writeToken(token, cid, new InetSocketAddress("1.2.3.4", 1234))
        def offset = handler.validateToken(token, new InetSocketAddress("1.2.3.4", 1234))
        then:
        offset > 0
        token.skipBytes(offset) == cid
    }

    def 'validation failure: wrong token'() {
        given:
        def handler = new QuicTokenHandlerImpl(ByteBufAllocator.DEFAULT)
        def cid = Unpooled.wrappedBuffer("foobar".getBytes())

        when:
        ByteBuf token = Unpooled.buffer()
        handler.writeToken(token, cid, new InetSocketAddress("1.2.3.4", 1234))
        token.setByte(token.readerIndex(), token.getByte(token.readerIndex()) + 1)
        then:
        handler.validateToken(token, new InetSocketAddress("1.2.3.4", 1234)) == -1
    }

    def 'validation failure: wrong port'() {
        given:
        def handler = new QuicTokenHandlerImpl(ByteBufAllocator.DEFAULT)
        def cid = Unpooled.wrappedBuffer("foobar".getBytes())

        when:
        ByteBuf token = Unpooled.buffer()
        handler.writeToken(token, cid, new InetSocketAddress("1.2.3.4", 1235))
        then:
        handler.validateToken(token, new InetSocketAddress("1.2.3.4", 1234)) == -1
    }

    def 'validation failure: wrong address'() {
        given:
        def handler = new QuicTokenHandlerImpl(ByteBufAllocator.DEFAULT)
        def cid = Unpooled.wrappedBuffer("foobar".getBytes())

        when:
        ByteBuf token = Unpooled.buffer()
        handler.writeToken(token, cid, new InetSocketAddress("1.2.3.5", 1234))
        then:
        handler.validateToken(token, new InetSocketAddress("1.2.3.4", 1234)) == -1
    }

    def 'validation failure: wrong cid'() {
        given:
        def handler = new QuicTokenHandlerImpl(ByteBufAllocator.DEFAULT)
        def cid = Unpooled.wrappedBuffer("foobar".getBytes())

        when:
        ByteBuf token = Unpooled.buffer()
        handler.writeToken(token, cid, new InetSocketAddress("1.2.3.4", 1234))
        def changeIndex = token.readerIndex() + token.readableBytes() - 1
        token.setByte(changeIndex, token.getByte(changeIndex) + 1)
        then:
        handler.validateToken(token, new InetSocketAddress("1.2.3.4", 1234)) == -1
    }

    def 'validation window id'() {
        given:
        def handler = new QuicTokenHandlerImpl(ByteBufAllocator.DEFAULT) {
            def windowId = 0

            @Override
            long currentWindowId() {
                return windowId
            }
        }
        def cid = Unpooled.wrappedBuffer("foobar".getBytes())

        when:
        ByteBuf token = Unpooled.buffer()
        handler.writeToken(token, cid, new InetSocketAddress("1.2.3.4", 1234))
        def offset = handler.validateToken(token, new InetSocketAddress("1.2.3.4", 1234))
        then:
        offset > 0
        token.slice().skipBytes(offset) == cid

        when:
        handler.windowId++
        offset = handler.validateToken(token, new InetSocketAddress("1.2.3.4", 1234))
        then:
        offset > 0
        token.slice().skipBytes(offset) == cid

        when:
        handler.windowId++
        then:
        handler.validateToken(token, new InetSocketAddress("1.2.3.4", 1234)) == -1
    }
}
