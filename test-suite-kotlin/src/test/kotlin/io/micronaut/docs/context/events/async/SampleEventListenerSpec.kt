package io.micronaut.docs.context.events.async

import io.kotlintest.eventually
import io.kotlintest.seconds
import io.kotlintest.shouldBe
import io.kotlintest.specs.AnnotationSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.docs.context.events.SampleEventEmitterBean

// tag::class[]
class SampleEventListenerSpec : AnnotationSpec() {
    
    @Test
    @Ignore // TODO can't get this to pass on CI, any help is welcome
    fun testEventListenerWasNotified() {
        val context = ApplicationContext.run()
        val emitter = context.getBean(SampleEventEmitterBean::class.java)
        val listener = context.getBean(SampleEventListener::class.java)
        listener.invocationCounter.shouldBe(0)
        emitter.publishSampleEvent()
        
        eventually(5.seconds) {
            listener.invocationCounter.shouldBe(1)
        }
    }
}
// end::class[]