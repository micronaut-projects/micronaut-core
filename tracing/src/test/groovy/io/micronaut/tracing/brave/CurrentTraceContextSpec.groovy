package io.micronaut.tracing.brave

import brave.propagation.CurrentTraceContext
import brave.propagation.ThreadLocalCurrentTraceContext
import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class CurrentTraceContextSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()

    void "test current trace context"() {
        given:
        def bean = context.getBean(CurrentTraceContext)

        expect:
        bean instanceof ThreadLocalCurrentTraceContext
    }
}
