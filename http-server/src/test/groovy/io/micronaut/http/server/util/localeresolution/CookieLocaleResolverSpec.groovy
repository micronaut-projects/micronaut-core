package io.micronaut.http.server.util.localeresolution

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class CookieLocaleResolverSpec extends Specification {

    void "bean of type CookieLocaleResolver is not enabled by default"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        expect:
        !context.containsBean(CookieLocaleResolver)

        cleanup:
        context.close()
    }
}
