package io.micronaut.tracing.instrument

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ExecutorServiceInstrumentSpec extends Specification {

    @Shared @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run(
            (IExecutorServiceImpl.PROP): true
    )

    void "test that sub interfaces for executor service are not instrumented"() {
        expect:
        applicationContext.getBean(IExecutorService) instanceof IExecutorService
    }
}
