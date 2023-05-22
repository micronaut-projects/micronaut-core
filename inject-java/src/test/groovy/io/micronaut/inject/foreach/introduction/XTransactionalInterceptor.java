package io.micronaut.inject.foreach.introduction;

import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Prototype;

@InterceptorBean(XTransactionalAdvice.class)
@Prototype
public class XTransactionalInterceptor implements MethodInterceptor<Object, Object> {
    private final XSessionFactory sessionFactory;

    XTransactionalInterceptor(BeanContext beanContext, Qualifier<XSessionFactory> qualifier) {
        this.sessionFactory = beanContext.getBean(XSessionFactory.class, qualifier);
    }
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return sessionFactory.name();
    }
}
