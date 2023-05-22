package io.micronaut.docs.context.events.async

// tag::imports[]
import io.kotest.assertions.timing.eventually
import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.AnnotationSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.docs.context.events.SampleEventEmitterBean
import org.opentest4j.AssertionFailedError
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.toDuration
// end::imports[]

// tag::class[]
@ExperimentalTime
class SampleEventListenerSpec : AnnotationSpec() {

    @Test
    suspend fun testEventListenerWasNotified() {
        val context = ApplicationContext.run()
        val emitter = context.getBean(SampleEventEmitterBean::class.java)
        val listener = context.getBean(SampleEventListener::class.java)
        listener.invocationCounter.get().shouldBe(0)
        emitter.publishSampleEvent()

        eventually(5.toDuration(DurationUnit.SECONDS), AssertionFailedError::class) {
            println("Current value of counter: " + listener.invocationCounter.get())
            listener.invocationCounter.get().shouldBe(1)
        }

        context.close()
    }
}
// end::class[]
