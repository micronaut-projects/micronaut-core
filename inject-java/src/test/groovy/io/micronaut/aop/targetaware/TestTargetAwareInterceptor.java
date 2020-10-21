package io.micronaut.aop.targetaware;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.aop.TargetAwareMethodInterceptor;
import io.micronaut.context.Qualifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spockframework.util.Assert;

import javax.inject.Singleton;

@Singleton
public class TestTargetAwareInterceptor implements TargetAwareMethodInterceptor<Object, Object> {
    private Object target;
    private int count;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Assert.notNull(target);
        return context.proceed();
    }

    @Override
    public void newTarget(@Nullable Qualifier<Object> qualifier, @NotNull Object target) {
        count++;
        Assert.notNull(target);
        if (target instanceof AnotherBean) {
            Assert.notNull(qualifier);
        }
        this.target = target;
    }

    public Object getTarget() {
        return target;
    }

    public int getCount() {
        return count;
    }
}
