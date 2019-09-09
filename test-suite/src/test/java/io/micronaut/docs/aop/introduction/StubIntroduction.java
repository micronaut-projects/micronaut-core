package io.micronaut.docs.aop.introduction;

// tag::imports[]
import io.micronaut.aop.*;
import javax.inject.Singleton;
// end::imports[]

// tag::class[]
@Singleton
public class StubIntroduction implements MethodInterceptor<Object,Object> { // <1>

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.getValue( // <2>
                Stub.class,
                context.getReturnType().getType()
        ).orElse(null); // <3>
    }
}
// end::class[]
