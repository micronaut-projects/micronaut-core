package io.micronaut.http.server.types.files

import io.netty.handler.codec.http.QueryStringEncoder
import spock.lang.Specification

class StreamedFileSpec extends Specification {
    def 'encode RFC 6987'() {
        expect:
        for (int codePoint = 0; codePoint < 0x10000; codePoint++) {
            StringBuilder sb = new StringBuilder(2);
            sb.appendCodePoint(codePoint)
            String s = sb.toString()

            def encoder = new QueryStringEncoder('')
            encoder.addParam("foo", s)
            def encoded = encoder.toString().substring("?foo=".length())

            assert StreamedFile.encodeRfc6987(s) == encoded
        }
    }

    def 'buildAttachmentHeader'() {
        expect:
        StreamedFile.buildAttachmentHeader('foo') == 'attachment; filename="foo"; filename*=utf-8\'\'foo'
        StreamedFile.buildAttachmentHeader('€ rates') == 'attachment; filename=" rates"; filename*=utf-8\'\'%E2%82%AC%20rates'
    }
}
