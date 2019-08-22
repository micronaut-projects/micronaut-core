package io.micronaut.docs.aop.around

// tag::imports[]

import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.core.type.MutableArgumentValue

import javax.inject.Singleton
// end::imports[]

// tag::interceptor[]
@Singleton
class NotNullInterceptor implements MethodInterceptor<Object, Object> { // <1>
    @Override
    Object intercept(MethodInvocationContext<Object, Object> context) {
        Optional<Map.Entry<String, MutableArgumentValue<?>>> nullParam = context.getParameters()
            .entrySet()
            .stream()
            .filter({entry ->
                MutableArgumentValue<?> argumentValue = entry.getValue()
                return Objects.isNull(argumentValue.getValue())
            })
            .findFirst() // <2>
        if (nullParam.isPresent()) {
            throw new IllegalArgumentException("Null parameter [" + nullParam.get().getKey() + "] not allowed") // <3>
        } else {
            return context.proceed() // <4>
        }
    }
}
// end::interceptor[]
