package io.micronaut.aop

class LoggedInterceptor implements Interceptor {

    @Override
    Object intercept(InvocationContext context) {
        println("Starting method")
        Object value = context.proceed()
        println("Finished method")
        value
    }
}
