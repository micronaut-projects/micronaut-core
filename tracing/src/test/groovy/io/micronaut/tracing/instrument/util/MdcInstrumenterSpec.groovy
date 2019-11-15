package io.micronaut.tracing.instrument.util

import io.micronaut.scheduling.instrument.InvocationInstrumenter
import org.slf4j.MDC
import spock.lang.Specification
import spock.util.concurrent.AsyncConditions

class MdcInstrumenterSpec extends Specification {
    static final String key = 'foo'
    static final String value = 'bar'

    AsyncConditions conds = new AsyncConditions()
    MdcInstrumenter mdcInstrumenter = new MdcInstrumenter()

    def cleanup() {
        MDC.clear()
    }

    void "test MDC instrumenter"() {
        given:
        MDC.put(key, value)

        and:
        Runnable runnable = {
            conds.evaluate {
                assert MDC.get(key) == value
            }
        }
        runnable = InvocationInstrumenter.instrument(runnable, mdcInstrumenter.newInvocationInstrumenter().get())
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
        def instrumenter = mdcInstrumenter.newInvocationInstrumenter().get()

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
        def instrumenter1 = mdcInstrumenter.newInvocationInstrumenter().get()
        Runnable runnable1 = InvocationInstrumenter.instrument({
            conds.evaluate {
                assert MDC.get(key) == value
            }
        } as Runnable, instrumenter1)

        and:
        String value2 = 'baz'
        MDC.put(key, value2)
        def instrumenter2 = mdcInstrumenter.newInvocationInstrumenter().get()
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
}
