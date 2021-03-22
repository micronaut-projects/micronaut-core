package io.micronaut.docs.inject.typed

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NoSuchBeanException
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

// tag::class[]
class EngineSpec extends Specification {
    @Shared @AutoCleanup
    ApplicationContext beanContext = ApplicationContext.run()

    void 'test engine'() {
        when:'the class is looked up'
        beanContext.getBean(V8Engine) // <1>

        then:'a no such bean exception is thrown'
        thrown(NoSuchBeanException)

        and:'it is possible to lookup by the typed interface'
        beanContext.getBean(Engine) instanceof V8Engine // <2>
    }
}
// end::class[]