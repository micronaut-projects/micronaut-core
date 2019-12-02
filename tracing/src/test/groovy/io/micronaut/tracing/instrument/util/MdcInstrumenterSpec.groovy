package io.micronaut.tracing.instrument.util

import io.micronaut.context.ApplicationContext
import org.slf4j.MDC
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

class MdcInstrumenterSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run()

    void "test MDC instrumenter"() {
        given:
        MDC.setContextMap(foo:'bar')

        when:
        String val =null
        Runnable runnable = {
            val =  MDC.get("foo")
            assert val == "bar"
        }
        runnable = new MdcInstrumenter().apply(runnable)
        def thread = new Thread(runnable)
        thread.start()
        thread.join()

        then:
        val == 'bar'

        cleanup:
        MDC.clear()
    }

    void "test MDC instrumenter with Executor"() {

        given:
        MDC.setContextMap(foo:'bar')
        ExecutorService executor = applicationContext.getBean(ExecutorService)
        String val = null

        when:
        CompletableFuture.supplyAsync({ ->
            val = MDC.get("foo")
        }, executor).get()

        then:
        val == "bar"

    }

}
