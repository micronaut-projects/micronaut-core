package io.micronaut.tracing.instrument.util

import io.micronaut.scheduling.instrument.RunnableInstrumenter
import io.micronaut.context.ApplicationContext
import org.slf4j.MDC
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.AsyncConditions

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

class MdcInstrumenterSpec extends Specification {
    static final String key = 'foo'
    static final String value = 'bar'

    AsyncConditions conds = new AsyncConditions()
    MdcInstrumenter mdcInstrumenter = new MdcInstrumenter()

    def cleanup() {
        MDC.clear()
    }

    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run()

    void "test MDC instrumenter"() {
        given:
        MDC.put(key, value)

        and:
        Runnable runnable = {
            conds.evaluate {
                assert MDC.get(key) == value
            }
        }
        runnable = mdcInstrumenter.apply(runnable)
        def thread = new Thread(runnable)

        when:
        thread.start()

        then:
        conds.await()
        MDC.get(key) == value

        cleanup:
        thread.join()
    }

    def "async operation runs as blocking operation within calling thread"() {
        given:
        MDC.put(key, value)

        and:
        RunnableInstrumenter instrumenter = mdcInstrumenter.newInstrumentation().get()

        and:
        Runnable runnable = {
            conds.evaluate {
                assert MDC.get(key) == value
            }
        }
        runnable = instrumenter.instrument(runnable)

        when:
        runnable.run()

        then:
        conds.await()
        MDC.get(key) == value
    }

    def "old context map is preserved after instrumented execution"() {
        given:
        MDC.put(key, value)
        RunnableInstrumenter instrumenter1 = mdcInstrumenter.newInstrumentation().get()
        Runnable runnable1 = instrumenter1.instrument({
            conds.evaluate {
                assert MDC.get(key) == value
            }
        } as Runnable)


        and:
        String value2 = 'baz'
        MDC.put(key, value2)
        RunnableInstrumenter instrumenter2 = mdcInstrumenter.newInstrumentation().get()
        Runnable runnable2 = instrumenter2.instrument({
            conds.evaluate {
                assert MDC.get(key) == value2
            }
        } as Runnable)

        when:
        runnable1.run()

        then:
        conds.await()
        MDC.get(key) == value2

        when:
        runnable2.run()

        then:
        conds.await()
        MDC.get(key) == value2
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
