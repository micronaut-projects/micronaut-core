package io.micronaut.http.server.util.localeresolution

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class CompositeHttpLocaleResolverSpec extends Specification {

    void "primary bean of type HttpLocaleResolver is CompositeHttpLocaleResolver"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        expect:
        context.getBean(HttpLocaleResolver) instanceof CompositeHttpLocaleResolver

        cleanup:
        context.close()
    }
}
