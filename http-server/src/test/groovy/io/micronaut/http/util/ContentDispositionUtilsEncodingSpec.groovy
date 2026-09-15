package io.micronaut.http.util

import io.netty.handler.codec.http.QueryStringEncoder
import spock.lang.Specification

class ContentDispositionUtilsEncodingSpec extends Specification {
    def 'encode RFC 5987'() {
        expect:
        for (int codePoint = 0; codePoint < 0x10000; codePoint++) {
            StringBuilder sb = new StringBuilder(2);
            sb.appendCodePoint(codePoint)
            String s = sb.toString()

            def encoder = new QueryStringEncoder('')
            encoder.addParam("foo", s)
            def encoded = encoder.toString().substring("?foo=".length())

            assert ContentDispositionUtils.encodeRfc5987(s) == encoded
        }
    }

    def 'encode RFC 5987 handles supplementary plane characters'() {
        expect:
        for (int codePoint : [0x1F600, 0x10000, 0x10FFFF]) {
            StringBuilder sb = new StringBuilder(2)
            sb.appendCodePoint(codePoint)
            String s = sb.toString()

            def encoder = new QueryStringEncoder('')
            encoder.addParam("foo", s)
            def encoded = encoder.toString().substring("?foo=".length())

            assert ContentDispositionUtils.encodeRfc5987(s) == encoded
        }
    }
}
