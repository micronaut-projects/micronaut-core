package io.micronaut.docs.context.events.async

// tag::imports[]
import io.kotlintest.eventually
import io.kotlintest.seconds
import io.kotlintest.shouldBe
import io.kotlintest.specs.AnnotationSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.docs.context.events.SampleEventEmitterBean
import org.opentest4j.AssertionFailedError
// end::imports[]

// tag::class[]
class SampleEventListenerSpec : AnnotationSpec() {
    
    @Test
//    @Ignore // TODO can't get this to pass on CI, any help is welcome
    fun testEventListenerWasNotified() {
        val context = ApplicationContext.run()
        val emitter = context.getBean(SampleEventEmitterBean::class.java)
        val listener = context.getBean(SampleEventListener::class.java)
        listener.invocationCounter.get().shouldBe(0)
        emitter.publishSampleEvent()
        
        eventually(5.seconds,  AssertionFailedError::class.java) {
            println("Current value of counter: " + listener.invocationCounter.get())
            listener.invocationCounter.get().shouldBe(1)
        }

        context.close()
    }
}
// end::class[]