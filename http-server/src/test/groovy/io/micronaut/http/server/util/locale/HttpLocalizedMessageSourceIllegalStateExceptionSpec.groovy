package io.micronaut.http.server.util.locale

import spock.lang.Specification

class HttpLocalizedMessageSourceIllegalStateExceptionSpec extends Specification {

    void "if locale is null an exception is thrown"() {
        when:
        new HttpLocalizedMessageSource(null, null).getLocale()

        then:
        thrown(IllegalStateException)

    }
}
