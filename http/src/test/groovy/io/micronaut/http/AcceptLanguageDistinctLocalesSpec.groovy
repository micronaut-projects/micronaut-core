package io.micronaut.http

import spock.lang.Specification

/**
 * The JDK caches per locale without a bound, so request values must resolve to a bounded number
 * of distinct locales.
 */
class AcceptLanguageDistinctLocalesSpec extends Specification {

    void 'distinct private-use tags resolve to a bounded number of locales'() {
        given:
        def locales = new BoundedLocales(10)

        when:
        Set<Locale> resolved = (1..1000).collect { locales.resolve(Locale.forLanguageTag('en-US-x-' + it)) } as Set

        then:
        resolved.size() <= 11
        resolved.contains(Locale.US)
    }

    void 'available locales and already seen locales are kept once the bound is reached'() {
        given:
        def locales = new BoundedLocales(1)
        def seen = Locale.forLanguageTag('de-CH-1996')
        locales.resolve(seen)

        expect:
        locales.resolve(Locale.forLanguageTag('de-CH-1901')) == Locale.forLanguageTag('de-CH')
        locales.resolve(seen) == seen
        locales.resolve(Locale.CANADA_FRENCH) == Locale.CANADA_FRENCH
        locales.resolve(Locale.forLanguageTag('zz-x-foo')) == Locale.getDefault()
    }
}
