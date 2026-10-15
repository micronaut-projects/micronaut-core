package io.micronaut.docs.aop.lifecycle.pertarget;

// tag::imports[]
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Prototype;
// end::imports[]

// tag::interceptor[]
@Prototype // <1>
@InterceptorBean(Audited.class)
public class AuditInterceptor implements MethodInterceptor<Object, String> {

    private int calls; // <2>

    @Override
    public String intercept(MethodInvocationContext<Object, String> context) {
        calls++;
        return context.proceed() + " (call " + calls + ")";
    }
}
// end::interceptor[]
