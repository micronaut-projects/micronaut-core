package io.micronaut.docs.aop.lifecycle

// tag::imports[]
import io.micronaut.aop.AroundConstruct
import io.micronaut.aop.InterceptorBinding
import io.micronaut.aop.InterceptorBindingDefinitions
import io.micronaut.aop.InterceptorKind
import io.micronaut.context.annotation.Prototype
// end::imports[]

// tag::class[]
@Retention(AnnotationRetention.RUNTIME)
@AroundConstruct // <1>
@InterceptorBindingDefinitions(
    InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT), // <2>
    InterceptorBinding(kind = InterceptorKind.PRE_DESTROY) // <3>
)
@Prototype // <4>
annotation class ProductBean
// end::class[]