package io.micronaut.aop.targetaware;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.aop.TargetAwareMethodInterceptor;
import io.micronaut.context.Qualifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spockframework.util.Assert;

import javax.inject.Singleton;

@Singleton
public class TypeSpecificTargetAwareInterceptor implements TargetAwareMethodInterceptor<TestBean, Object> {
    private TestBean target;
    private int count;
    @Override
    public Object intercept(MethodInvocationContext<TestBean, Object> context) {
        Assert.notNull(target);
        return context.proceed();
    }

    @Override
    public void newTarget(@Nullable Qualifier<TestBean> qualifier, @NotNull TestBean target) {
        count++;
        this.target = target;
    }

    public TestBean getTarget() {
        return target;
    }

    public int getCount() {
        return count;
    }
}
