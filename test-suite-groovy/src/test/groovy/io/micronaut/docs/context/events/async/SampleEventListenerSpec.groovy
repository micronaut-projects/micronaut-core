package io.micronaut.docs.context.events.async

import io.micronaut.context.ApplicationContext
import io.micronaut.docs.context.events.SampleEventEmitterBean
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

// tag::class[]
class SampleEventListenerSpec extends Specification {

    void "test event listener is notified"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        SampleEventEmitterBean emitter = context.getBean(SampleEventEmitterBean)
        SampleEventListener listener = context.getBean(SampleEventListener)
        assert listener.invocationCounter.get() == 0

        when:
        emitter.publishSampleEvent()

        then:
        new PollingConditions().eventually {
            listener.invocationCounter.get() == 1
        }
    }
}
// end::class[]
