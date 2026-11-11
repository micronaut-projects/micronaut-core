package io.micronaut.docs.aop.lifecycle.pertarget

// tag::imports[]
import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.context.annotation.Prototype
// end::imports[]

// tag::interceptor[]
@Prototype // <1>
@InterceptorBean(Audited::class)
class AuditInterceptor : MethodInterceptor<Any, String> {

    private var calls = 0 // <2>

    override fun intercept(context: MethodInvocationContext<Any, String>): String {
        calls++
        return "${context.proceed()} (call $calls)"
    }
}
// end::interceptor[]
