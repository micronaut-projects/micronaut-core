package io.micronaut.http

import spock.lang.Specification

/**
 * {@link HttpRequest#getLocale()} resolves the Accept-Language header through the bounded locales.
 */
class AcceptLanguageRequestLocaleSpec extends Specification {

    void 'the locales of requests with distinct private-use tags are bounded'() {
        when:
        Set<Locale> resolved = (1..3000).collect {
            HttpRequest.GET('/').header(HttpHeaders.ACCEPT_LANGUAGE, 'en-US-x-req' + it).getLocale().get()
        } as Set

        then: "at most the bound of other locales plus the closest available one"
        resolved.size() <= 1025
        resolved.contains(Locale.US)
    }

    void 'an available locale is returned as sent'() {
        expect:
        HttpRequest.GET('/').header(HttpHeaders.ACCEPT_LANGUAGE, 'fr-CA,en;q=0.5').getLocale().get() == Locale.CANADA_FRENCH
    }
}
