package io.micronaut.http.server.types.files

import spock.lang.Specification

class StreamedFileSpec extends Specification {
    def 'buildAttachmentHeader'() {
        expect:
        StreamedFile.buildAttachmentHeader('foo') == 'attachment; filename="foo"; filename*=utf-8\'\'foo'
        StreamedFile.buildAttachmentHeader('€ rates') == 'attachment; filename=" rates"; filename*=utf-8\'\'%E2%82%AC%20rates'
    }
}
