package io.micronaut.aop;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.inject.ExecutableMethod;

@Prototype
public class ContextualMyInterfaceInterceptor implements MethodInterceptor<MyInterface, Object> {

    @Override
    public Object intercept(MethodInvocationContext<MyInterface, Object> context) {
        final ExecutableMethod<MyInterface, Object> method = context.getExecutableMethod();
        MyInterface myInterface = new MyImpl();
        return method.invoke(myInterface, context.getParameterValues());
    }

}
