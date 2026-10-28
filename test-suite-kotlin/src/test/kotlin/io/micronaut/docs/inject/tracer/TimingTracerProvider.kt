package io.micronaut.docs.inject.tracer

// tag::imports[]
import io.micronaut.context.BeanResolutionTracer
import io.micronaut.context.BeanResolutionTracerProvider
import io.micronaut.context.BeanResolutionTracerProvider.Activation
import io.micronaut.context.env.Environment
// end::imports[]

// tag::class[]
class TimingTracerProvider : BeanResolutionTracerProvider {

    override fun isEnabled(activation: Activation): Boolean =
        activation.isDiscoveryEnabled ||
            activation.environmentNames.contains(Environment.DEVELOPMENT) // <1>

    override fun createTracer(activation: Activation): BeanResolutionTracer =
        TimingTracer() // <2>
}
// end::class[]
