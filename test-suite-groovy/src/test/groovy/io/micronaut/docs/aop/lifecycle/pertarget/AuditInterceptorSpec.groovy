package io.micronaut.docs.aop.lifecycle.pertarget

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.context.scope.refresh.RefreshEvent
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class AuditInterceptorSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()

    void 'test each target has its own interceptor'() {
        // tag::test[]
        when:
        Greeter greeter = context.getBean(Greeter)

        then:
        greeter.greet('Fred') == 'Hello Fred (call 1)' // <1>
        greeter.farewell('Fred') == 'Goodbye Fred (call 2)'

        when:
        context.publishEvent(new RefreshEvent()) // <2>

        then:
        greeter.greet('Bob') == 'Hello Bob (call 1)' // <3>
        greeter.farewell('Bob') == 'Goodbye Bob (call 2)'
        // end::test[]
    }
}
