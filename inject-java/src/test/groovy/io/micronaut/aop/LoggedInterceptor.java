package io.micronaut.aop;

public class LoggedInterceptor implements Interceptor {

    @Override
    public Object intercept(InvocationContext context) {
        System.out.println("Starting method");
        Object value = context.proceed();
        System.out.println("Finished method");
        return value;
    }
}
