package io.micronaut.http.server.util.locale

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class RequestLocaleResolverSpec extends Specification {

    void "bean of type RequestLocaleResolver is enabled by default"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        expect:
        context.containsBean(RequestLocaleResolver)

        cleanup:
        context.close()
    }
}
