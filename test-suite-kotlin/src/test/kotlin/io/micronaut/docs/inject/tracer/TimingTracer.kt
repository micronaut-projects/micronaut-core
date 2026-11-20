package io.micronaut.docs.inject.tracer

// tag::imports[]
import io.micronaut.context.BeanResolutionTracer
import io.micronaut.context.BeanResolutionTracer.Injection
import io.micronaut.context.BeanResolutionTracer.Resolution
import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinition
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
// end::imports[]

// tag::class[]
class TimingTracer : BeanResolutionTracer {

    private val starts = ThreadLocal.withInitial { ArrayDeque<Long>() } // <1>
    val timings: MutableMap<String, Long> = ConcurrentHashMap()

    override fun traceBeanCreation(resolution: Resolution, definition: BeanDefinition<*>, beanType: Argument<*>) {
        starts.get().push(System.nanoTime()) // <2>
    }

    override fun traceBeanCreated(resolution: Resolution, definition: BeanDefinition<*>) {
        val took = System.nanoTime() - starts.get().pop() // <3>
        timings[definition.beanType.name] = took
    }

    override fun traceBeanCreationFailed(resolution: Resolution, definition: BeanDefinition<*>, failure: Throwable) {
        starts.get().pop() // <4>
    }

    override fun traceInjectBean(resolution: Resolution, injection: Injection) {
        // injection points are not timed
    }
}
// end::class[]
