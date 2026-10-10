package io.micronaut.docs.aop.lifecycle.pertarget.labelled;

// tag::imports[]
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.InterceptionTarget;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.naming.Named;
// end::imports[]

// tag::interceptor[]
@Prototype
@InterceptorBean(Labelled.class)
public class LabelInterceptor implements MethodInterceptor<Object, String> {

    private final String label;

    public LabelInterceptor(InterceptionTarget target) { // <1>
        this.label = target.definition().getDeclaredQualifier() instanceof Named named
            ? named.getName() : target.definition().getBeanType().getSimpleName(); // <2>
    }

    @Override
    public String intercept(MethodInvocationContext<Object, String> context) {
        return "[" + label + "] " + context.proceed();
    }
}
// end::interceptor[]
