package io.micronaut.context

import spock.lang.Specification

class BeanResolutionTraceModeSpec extends Specification {

    void cleanup() {
        System.clearProperty("micronaut.inject.trace.mode")
    }

    void "the trace mode property selects the mode #mode"() {
        given:
        System.setProperty("micronaut.inject.trace.mode", value)

        expect:
        BeanResolutionTraceMode.getDefaultMode(Set.of()) == mode

        where:
        value          | mode
        "none"         | BeanResolutionTraceMode.NONE
        "log"          | BeanResolutionTraceMode.LOG
        "standard-out" | BeanResolutionTraceMode.STANDARD_OUT
    }

    void "an unknown trace mode is rejected"() {
        given:
        System.setProperty("micronaut.inject.trace.mode", "unknown")

        when:
        BeanResolutionTraceMode.getDefaultMode(Set.of())

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "No enum constant io.micronaut.context.BeanResolutionTraceMode.UNKNOWN"
    }
}
