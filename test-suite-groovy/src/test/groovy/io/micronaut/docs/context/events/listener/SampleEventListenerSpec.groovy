package io.micronaut.docs.context.events.listener

import io.micronaut.context.ApplicationContext
import io.micronaut.docs.context.events.SampleEventEmitterBean
import spock.lang.Specification

// tag::class[]
class SampleEventListenerSpec extends Specification {

    void "test event listener is notified"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        SampleEventEmitterBean emitter = context.getBean(SampleEventEmitterBean)
        SampleEventListener listener = context.getBean(SampleEventListener)
        assert listener.invocationCounter == 0

        when:
        emitter.publishSampleEvent()

        then:
        listener.invocationCounter == 1

        cleanup:
        context.close()
    }
}
// end::class[]