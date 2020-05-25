package io.micronaut.tracing.instrument.util

import io.micronaut.context.ApplicationContext
import io.micronaut.scheduling.instrument.InvocationInstrumenter
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
        runnable = InvocationInstrumenter.instrument(runnable, mdcInstrumenter.newInvocationInstrumenter())
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
        def instrumenter = mdcInstrumenter.newInvocationInstrumenter()

        and:
        Runnable runnable = {
            conds.evaluate {
                assert MDC.get(key) == value
            }
        }
        runnable = InvocationInstrumenter.instrument(runnable, instrumenter)

        when:
        runnable.run()

        then:
        conds.await()
        MDC.get(key) == value
    }

    def "old context map is preserved after instrumented execution"() {
        given:
        MDC.put(key, value)
        def instrumenter1 = mdcInstrumenter.newInvocationInstrumenter()
        Runnable runnable1 = InvocationInstrumenter.instrument({
            conds.evaluate {
                assert MDC.get(key) == value
            }
        } as Runnable, instrumenter1)

        and:
        String value2 = 'baz'
        MDC.put(key, value2)
        def instrumenter2 = mdcInstrumenter.newInvocationInstrumenter()
        Runnable runnable2 = InvocationInstrumenter.instrument({
            conds.evaluate {
                assert MDC.get(key) == value2
            }
        } as Runnable, instrumenter2)
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

    def "empty context should't clean MDC"() {
        when:
        MDC.put(key, value)
        MDC.remove(key) // Make empty context
        def mdcBeforeNew = MDC.copyOfContextMap
        def instrumenterWithEmptyContext = mdcInstrumenter.newInvocationInstrumenter()
        MDC.put("contextValue", "contextValue")
        def instrumenterWithContext = mdcInstrumenter.newInvocationInstrumenter()
        MDC.clear()
        Runnable runnable = InvocationInstrumenter.instrument({
            MDC.put("inside1", "inside1")
            InvocationInstrumenter.instrument({
                assert MDC.get("contextValue") == "contextValue"
                assert MDC.get("inside1") == "inside1"

                MDC.put("inside2", "inside2")
            } as Runnable, instrumenterWithEmptyContext).run()
            assert MDC.get("contextValue") == "contextValue"
            assert MDC.get("inside1") == "inside1"
            assert MDC.get("inside2") == null
        } as Runnable, instrumenterWithContext)
        runnable.run()

        then:
        mdcBeforeNew.isEmpty()
        MDC.get(key) == null
        MDC.get("XXX") == null
        MDC.get("aaa") == null
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
