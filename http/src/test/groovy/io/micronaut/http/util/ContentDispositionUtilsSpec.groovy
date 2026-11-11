package io.micronaut.http.util

import spock.lang.Specification

class ContentDispositionUtilsSpec extends Specification {
    def 'toHeaderValue with filename'() {
        expect:
        ContentDispositionUtils.toHeaderValue('attachment', 'foo') == 'attachment; filename="foo"; filename*=utf-8\'\'foo'
        ContentDispositionUtils.toHeaderValue('attachment', '€ rates') == 'attachment; filename=" rates"; filename*=utf-8\'\'%E2%82%AC%20rates'
        ContentDispositionUtils.toHeaderValue('inline', 'foo') == 'inline; filename="foo"; filename*=utf-8\'\'foo'
    }

    def 'toHeaderValue without filename'() {
        expect:
        ContentDispositionUtils.toHeaderValue('attachment', null) == 'attachment'
        ContentDispositionUtils.toHeaderValue('attachment', '') == 'attachment'
        ContentDispositionUtils.toHeaderValue('inline', null) == 'inline'
    }

    def 'toHeaderValue strips backslashes and quotes from the legacy filename fallback'() {
        expect:
        ContentDispositionUtils.toHeaderValue('attachment', 'foo\\bar') == 'attachment; filename="foobar"; filename*=utf-8\'\'foo%5Cbar'
        ContentDispositionUtils.toHeaderValue('attachment', 'foo"bar') == 'attachment; filename="foobar"; filename*=utf-8\'\'foo%22bar'
    }
}
