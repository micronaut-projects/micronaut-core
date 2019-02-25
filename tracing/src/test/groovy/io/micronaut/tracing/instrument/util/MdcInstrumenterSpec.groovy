package io.micronaut.tracing.instrument.util

import org.slf4j.MDC
import spock.lang.Specification

class MdcInstrumenterSpec extends Specification {

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
}
