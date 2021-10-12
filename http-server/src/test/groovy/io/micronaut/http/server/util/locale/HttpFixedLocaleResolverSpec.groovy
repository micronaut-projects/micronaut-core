package io.micronaut.http.server.util.locale

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class HttpFixedLocaleResolverSpec extends Specification {
    void "bean of type HttpFixedLocaleResolver is not enabled by default"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        expect:
        !context.containsBean(HttpFixedLocaleResolver)

        cleanup:
        context.close()
    }
}
