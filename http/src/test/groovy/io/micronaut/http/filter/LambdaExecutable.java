package io.micronaut.http.filter;

import groovy.lang.Closure;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.inject.ExecutableMethod;

import java.lang.reflect.Method;

/**
 * This class used to live under {@link FilterRunnerSpec} but fails compiling under Groovy 5
 * with:
 * <pre>
 *      class io.micronaut.http.filter.LambdaExecutable inherits unrelated defaults for java.util.Optional findAnnotation(java.lang.Class) from types io.micronaut.core.annotation.AnnotationMetadataProvider and io.micronaut.core.annotation.AnnotationMetadata
 * </pre>
 */
public final class LambdaExecutable implements ExecutableMethod<Object, Object> {

    private final Closure<?> closure;
    private final Argument<?>[] arguments;
    private final ReturnType<Object> returnType;

    public LambdaExecutable(Closure<?> closure, Argument<?>[] arguments, ReturnType<Object> returnType) {
        this.closure = closure;
        this.arguments = arguments;
        this.returnType = returnType;
    }

    @Override
    public Class<Object> getDeclaringType() {
        return Object.class;
    }

    @Override
    public String getMethodName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Argument<?>[] getArguments() {
        return arguments;
    }

    @Override
    public Method getTargetMethod() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ReturnType<Object> getReturnType() {
        return returnType;
    }

    @Override
    public Object invoke(@Nullable Object instance, Object... arguments) {
        return closure.curry(arguments).call();
    }
}
