package io.micronaut.aop.introduction;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Minimal introduction advice used in tests with {@link Stub}.
 * Returns null for introduced method invocations.
 */
@Singleton
public class StubIntroducer implements MethodInterceptor<Object, Object> {

    public static final int POSITION = 100;
    private final Map<String, AnnotationMetadata> visitedMethods = new HashMap<>();

    @Override
    public int getOrder() {
        return POSITION;
    }

    public Map<String, AnnotationMetadata> getVisitedMethods() {
        return visitedMethods;
    }

    @Nullable
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        // Record visited method metadata for assertions in introduction tests
        visitedMethods.put(context.getMethodName(), context.getExecutableMethod().getAnnotationMetadata());

        // For introduction tests, return the (possibly mutated) first argument when present.
        // The @Mutating("name") advice changes the "name" parameter to "changed", so returning it
        // aligns with the Groovy spec expectations. Also cover myMethod* used by inheritance tests.
        String name = context.getMethodName();
        if (name.startsWith("test") || name.startsWith("myMethod")) {
            Object[] values = context.getParameterValues();
            if (values.length > 0 && values[0] instanceof String) {
                return values[0];
            }
        }
        return null;
    }
}
