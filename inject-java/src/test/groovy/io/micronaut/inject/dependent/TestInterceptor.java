package io.micronaut.inject.dependent;

import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.core.annotation.Nullable;

import jakarta.annotation.PreDestroy;

@Bean
@InterceptorBinding(
        value = TestAnn.class,
        kind = InterceptorKind.AROUND
)
public class TestInterceptor implements MethodInterceptor<Object, Object> {
    @Nullable
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.proceed();
    }

    @PreDestroy
    void close() {
        TestData.DESTRUCTION_ORDER.add(TestInterceptor.class.getSimpleName());
    }
}
