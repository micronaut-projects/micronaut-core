package io.micronaut.docs.aop.around;

// tag::imports[]
import io.micronaut.aop.*;
import io.micronaut.core.type.MutableArgumentValue;

import javax.inject.Singleton;
import java.util.*;
// end::imports[]

// tag::interceptor[]
@Singleton
public class NotNullInterceptor implements MethodInterceptor<Object, Object> { // <1>
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Optional<Map.Entry<String, MutableArgumentValue<?>>> nullParam = context.getParameters()
            .entrySet()
            .stream()
            .filter(entry -> {
                MutableArgumentValue<?> argumentValue = entry.getValue();
                return Objects.isNull(argumentValue.getValue());
            })
            .findFirst(); // <2>
        if (nullParam.isPresent()) {
            throw new IllegalArgumentException("Null parameter [" + nullParam.get().getKey() + "] not allowed"); // <3>
        } else {
            return context.proceed(); // <4>
        }
    }
}
// end::interceptor[]
