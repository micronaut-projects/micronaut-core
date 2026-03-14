package io.micronaut.aop.around.noproxytarget;

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InvocationContext;
import io.micronaut.core.type.MutableArgumentValue;
import jakarta.inject.Singleton;

@Singleton
public class ArgMutatingInterceptor implements Interceptor {

    @Override
    public Object intercept(InvocationContext context) {
        Mutating m = context.synthesize(Mutating.class);
        MutableArgumentValue arg = (MutableArgumentValue) context.getParameters().get(m.value());
        if (arg != null) {
            Object value = arg.getValue();
            if (value instanceof Number number) {
                arg.setValue(number.intValue() * 2);
            } else {
                arg.setValue("changed");
            }
        }
        return context.proceed();
    }
}
