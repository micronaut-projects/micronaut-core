package io.micronaut.http

import spock.lang.Specification
import spock.lang.Unroll

class HttpHeadersUtilsSpec extends Specification {

    @Unroll("HttpHeaderUtils::normalizeHeaderName normalizes to #headerName to #expected")
    void "HttpHeaderUtils::normalizeHeaderName normalizes to a HttpHeaders constant or supplied header name"(String headerName, String expected) {
        expect:
        HttpHeadersUtils.normalizeHeaderName(headerName) == expected

        where:
        headerName    || expected
        'Accept'      || 'Accept'
        'accept'      || 'Accept'
        'aCcept'      || 'Accept'
        'Turbo-Frame' || 'Turbo-Frame'
    }
}
