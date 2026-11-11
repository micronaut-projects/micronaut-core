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
@InterceptorBean(Labelled)
class LabelInterceptor implements MethodInterceptor<Object, String> {

    private final String label

    LabelInterceptor(InterceptionTarget target) { // <1>
        def qualifier = target.definition().declaredQualifier
        this.label = qualifier instanceof Named ? qualifier.name : target.definition().beanType.simpleName // <2>
    }

    @Override
    String intercept(MethodInvocationContext<Object, String> context) {
        "[$label] ${context.proceed()}"
    }
}
// end::interceptor[]
