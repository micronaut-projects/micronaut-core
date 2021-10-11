package io.micronaut.session.http

import io.micronaut.context.ApplicationContext
import io.micronaut.core.convert.value.MutableConvertibleValues
import io.micronaut.http.HttpRequest
import io.micronaut.session.Session
import io.micronaut.session.SessionStore
import spock.lang.Specification

class SessionLocaleResolverSpec extends Specification {

    void "test in-memory session store read and write"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(['micronaut.server.locale-resolution.session-attribute': 'userlocale'])

        SessionStore sessionStore = applicationContext.getBean(SessionStore)
        Session session = sessionStore.newSession()
        session.put("userlocale", Locale.CANADA_FRENCH)
        def attrs = Stub(MutableConvertibleValues) {
            get(HttpSessionFilter.SESSION_ATTRIBUTE, Session.class) >> Optional.of(session)
        }
        def req = Stub(HttpRequest) {
            getAttributes() >> attrs
        }

        expect:
        applicationContext.containsBean(SessionLocaleResolver)

        when:
        SessionLocaleResolver sessionLocaleResolver = applicationContext.getBean(SessionLocaleResolver)
        Optional<Locale> locale = sessionLocaleResolver.resolve(req)

        then:
        locale.isPresent()
        locale.get() == Locale.CANADA_FRENCH

        cleanup:
        applicationContext.close()
    }
}
