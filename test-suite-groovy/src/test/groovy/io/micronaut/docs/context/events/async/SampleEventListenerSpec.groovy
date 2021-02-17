package io.micronaut.docs.context.events.async

// tag::imports[]
import io.micronaut.context.ApplicationContext
import io.micronaut.docs.context.events.SampleEventEmitterBean
import spock.lang.Specification
import spock.util.concurrent.PollingConditions
// end::imports[]

// tag::class[]
class SampleEventListenerSpec extends Specification {

    void "test event listener is notified"() {
        given:
        def context = ApplicationContext.run()
        def emitter = context.getBean(SampleEventEmitterBean)
        def listener = context.getBean(SampleEventListener)

        expect:
        listener.invocationCounter.get() == 0

        when:
        emitter.publishSampleEvent()

        then:
        new PollingConditions(timeout: 5).eventually {
            listener.invocationCounter.get() == 1
        }

        cleanup:
        context.close()
    }
}
// end::class[]
