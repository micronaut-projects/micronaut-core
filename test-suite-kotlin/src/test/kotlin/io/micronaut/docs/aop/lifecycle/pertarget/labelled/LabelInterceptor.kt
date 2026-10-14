package io.micronaut.docs.aop.lifecycle.pertarget.labelled

// tag::imports[]
import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.context.InterceptionTarget
import io.micronaut.context.annotation.Prototype
import io.micronaut.core.naming.Named
// end::imports[]

// tag::interceptor[]
@Prototype
@InterceptorBean(Labelled::class)
class LabelInterceptor(target: InterceptionTarget) : MethodInterceptor<Any, String> { // <1>

    private val label: String = (target.definition().declaredQualifier as? Named)?.name
        ?: target.definition().beanType.simpleName // <2>

    override fun intercept(context: MethodInvocationContext<Any, String>): String {
        return "[$label] ${context.proceed()}"
    }
}
// end::interceptor[]
