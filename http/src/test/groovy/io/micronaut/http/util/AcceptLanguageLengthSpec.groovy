package io.micronaut.http.util

import spock.lang.Specification

class AcceptLanguageLengthSpec extends Specification {

    void 'an over-long Accept-Language tag resolves to the default locale'() {
        given:
        String longTag = 'en-US-' + (1..100).collect { String.format('v%04d', it) }.join('-')

        expect:
        HttpHeadersUtil.parseAcceptLanguage(longTag + ';q=0.9') == Locale.getDefault()
        new MockHttpHeaders(['Accept-Language': [longTag]]).findAcceptLanguage().get() == Locale.getDefault()
        HttpHeadersUtil.parseAcceptLanguage('de-CH-1996;q=0.9, en') == Locale.forLanguageTag('de-CH-1996')
        new MockHttpHeaders(['Accept-Language': ['fr-CA, en;q=0.5']]).findAcceptLanguage().get() == Locale.CANADA_FRENCH
        HttpHeadersUtil.parseAcceptLanguage('*') == Locale.getDefault()
    }
}
