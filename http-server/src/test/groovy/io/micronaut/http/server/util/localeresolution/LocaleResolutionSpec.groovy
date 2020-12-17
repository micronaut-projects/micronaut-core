package io.micronaut.http.server.util.localeresolution

import io.micronaut.context.ApplicationContext
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.util.localeresolution.LocaleResolutionConfiguration
import io.micronaut.http.HttpRequest
import io.micronaut.http.server.util.MockHttpHeaders
import io.micronaut.http.simple.cookies.SimpleCookie
import io.micronaut.http.simple.cookies.SimpleCookies
import spock.lang.Specification

class LocaleResolutionSpec extends Specification {

    void "test locale resolution via header with header resolution disabled"() {
        ApplicationContext applicationContext = ApplicationContext.run([
                'micronaut.server.locale-resolution.header': false
        ])
        HttpLocaleResolver localeResolver = applicationContext.getBean(HttpLocaleResolver)
        def request = createMock()
        request.getHeaders() >> new MockHttpHeaders(['Accept-Language': ['en-GB']])

        when:
        Optional<Locale> locale = localeResolver.resolve(request)

        then:
        !locale.isPresent()

        cleanup:
        applicationContext.close()
    }

    void "test locale resolution via header"() {
        ApplicationContext applicationContext = ApplicationContext.run()
        HttpLocaleResolver localeResolver = applicationContext.getBean(HttpLocaleResolver)
        def request = createMock()
        request.getHeaders() >> new MockHttpHeaders(['Accept-Language': ['en-GB']])

        when:
        Optional<Locale> locale = localeResolver.resolve(request)

        then:
        locale.isPresent()
        locale.get() == Locale.UK

        cleanup:
        applicationContext.close()
    }

    void "test locale resolution with cookie"() {
        ApplicationContext applicationContext = ApplicationContext.run([
                'micronaut.server.locale-resolution.cookie-name': 'Locale'
        ])
        HttpLocaleResolver localeResolver = applicationContext.getBean(HttpLocaleResolver)
        def request = createMock()
        def cookies = new SimpleCookies(applicationContext.getBean(ConversionService))
        cookies.put('Locale', new SimpleCookie('Locale', 'en_CA'))
        request.getCookies() >> cookies

        when:
        Optional<Locale> locale = localeResolver.resolve(request)

        then:
        locale.isPresent()
        locale.get() == Locale.CANADA

        cleanup:
        applicationContext.close()
    }

    void "test fixed locale"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run([
                'micronaut.server.locale-resolution.cookie-name': 'Locale',
                'micronaut.server.locale-resolution.fixed': 'ko-KR'
        ])

        HttpLocaleResolver localeResolver = applicationContext.getBean(HttpLocaleResolver)
        def request = createMock()
        request.getHeaders() >> new MockHttpHeaders(['Accept-Language': ['en-GB']])
        def cookies = new SimpleCookies(applicationContext.getBean(ConversionService))
        cookies.put('Locale', new SimpleCookie('Locale', 'en_CA'))
        request.getCookies() >> cookies

        expect:
        applicationContext.containsBean(LocaleResolutionConfiguration)

        when:
        Optional<Locale> locale = localeResolver.resolve(request)

        then:
        locale.isPresent()
        locale.get() == Locale.KOREA

        cleanup:
        applicationContext.close()
    }

    private HttpRequest createMock() {
        Mock(HttpRequest) {
            getUri() >> new URI("/")
            getLocale() >> { callRealMethod() }
        }
    }
}
