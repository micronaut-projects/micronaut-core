/*
 * Copyright 2017-2021 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context;

import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.*;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.ExecutableMethodsDefinition;
import io.micronaut.inject.annotation.AbstractEnvironmentAnnotationMetadata;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Abstract base class for for {@link ExecutableMethodsDefinition}.
 *
 * @param <T> The type
 * @author Denis Stepanov
 * @since 3.0
 */
@Internal
public abstract class AbstractExecutableMethodsDefinition<T> implements ExecutableMethodsDefinition<T>, EnvironmentConfigurable {

    private final MethodReference[] methodsReferences;
    private final DispatchedExecutableMethod<T, ?>[] executableMethods;
    private Environment environment;
    private List<DispatchedExecutableMethod<T, ?>> executableMethodsList;

    protected AbstractExecutableMethodsDefinition(MethodReference[] methodsReferences) {
        this.methodsReferences = methodsReferences;
        this.executableMethods = new DispatchedExecutableMethod[methodsReferences.length];
    }

    @Override
    public void configure(Environment environment) {
        this.environment = environment;
        for (DispatchedExecutableMethod<T, ?> executableMethod : executableMethods) {
            if (executableMethod != null) {
                executableMethod.configure(environment);
            }
        }
    }

    @Override
    public Collection<ExecutableMethod<T, ?>> getExecutableMethods() {
        if (executableMethodsList == null) {
            // Initialize the collection
            for (int i = 0, methodsReferencesLength = methodsReferences.length; i < methodsReferencesLength; i++) {
                getExecutableMethodByIndex(i);
            }
            executableMethodsList = Arrays.asList(executableMethods);
        }
        return (Collection) executableMethodsList;
    }

    @Override
    public <R> Optional<ExecutableMethod<T, R>> findMethod(String name, Class<?>... argumentTypes) {
        return Optional.ofNullable(getMethod(name, argumentTypes));
    }

    @Override
    public <R> Stream<ExecutableMethod<T, R>> findPossibleMethods(String name) {
        return IntStream.range(0, methodsReferences.length)
                .filter(i -> methodsReferences[i].methodName.equals(name))
                .mapToObj(this::getExecutableMethodByIndex);
    }

    /**
     * Gets {@link ExecutableMethod} method by it's index.
     *
     * @param index The method index
     * @param <R>   The result type
     * @return The {@link ExecutableMethod}
     */
    @UsedByGeneratedCode
    public <R> ExecutableMethod<T, R> getExecutableMethodByIndex(int index) {
        DispatchedExecutableMethod<T, R> executableMethod = (DispatchedExecutableMethod<T, R>) executableMethods[index];
        if (executableMethod == null) {
            MethodReference methodsReference = methodsReferences[index];
            executableMethod = new DispatchedExecutableMethod<>(this, index, methodsReference, methodsReference.annotationMetadata);
            if (environment != null) {
                executableMethod.configure(environment);
            }
            executableMethods[index] = executableMethod;
        }
        return executableMethod;
    }

    /**
     * Finds executable method or returns a null otherwise.
     *
     * @param name          The method name
     * @param argumentTypes The method arguments
     * @param <R>           The return type
     * @return The {@link ExecutableMethod}
     */
    @UsedByGeneratedCode
    @Nullable
    protected <R> ExecutableMethod<T, R> getMethod(String name, Class<?>... argumentTypes) {
        for (int i = 0; i < methodsReferences.length; i++) {
            MethodReference methodReference = methodsReferences[i];
            if (methodReference.methodName.equals(name)
                    && methodReference.arguments.length == argumentTypes.length
                    && argumentsTypesMatch(argumentTypes, methodReference.arguments)) {
                return getExecutableMethodByIndex(i);
            }
        }
        return null;
    }

    /**
     * Triggers the invocation of the method at index. Used by {@link ExecutableMethod#invoke(Object, Object...)}.
     *
     * @param index  The method index
     * @param target The target
     * @param args   The arguments
     * @return The result
     */
    @UsedByGeneratedCode
    protected Object dispatch(int index, T target, Object[] args) {
        throw unknownDispatchAtIndexException(index);
    }

    /**
     * Find {@link Method} representation at the method by index. Used by {@link ExecutableMethod#getTargetMethod()}.
     *
     * @param index The index
     * @return The method
     */
    @UsedByGeneratedCode
    protected abstract Method getTargetMethodByIndex(int index);

    /**
     * Creates a new exception when the method at index is not found.
     *
     * @param index The method index
     * @return The exception
     */
    @UsedByGeneratedCode
    protected final Throwable unknownMethodAtIndexException(int index) {
        return new IllegalStateException("Unknown method at index: " + index);
    }

    /**
     * Creates a new exception when the dispatch at index is not found.
     *
     * @param index The method index
     * @return The exception
     */
    @UsedByGeneratedCode
    protected final RuntimeException unknownDispatchAtIndexException(int index) {
        return new IllegalStateException("Unknown dispatch at index: " + index);
    }

    /**
     * Checks if the method at index matches name and argument types.
     *
     * @param index         The method index
     * @param name          The method name
     * @param argumentTypes The method arguments
     * @return true if matches
     */
    @UsedByGeneratedCode
    protected final boolean methodAtIndexMatches(int index, String name, Class[] argumentTypes) {
        MethodReference methodReference = methodsReferences[index];
        Argument<?>[] arguments = methodReference.arguments;
        if (arguments.length != argumentTypes.length || !methodReference.methodName.equals(name)) {
            return false;
        }
        return argumentsTypesMatch(argumentTypes, arguments);
    }

    private boolean argumentsTypesMatch(Class[] argumentTypes, Argument<?>[] arguments) {
        for (int i = 0; i < arguments.length; i++) {
            if (!argumentTypes[i].equals(arguments[i].getType())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Internal class representing method's metadata.
     */
    @Internal
    public static final class MethodReference {
        final AnnotationMetadata annotationMetadata;
        final Class<?> declaringType;
        final String methodName;
        @Nullable
        final Argument<?> returnArgument;
        final Argument<?>[] arguments;
        final boolean isAbstract;
        final boolean isSuspend;

        /**
         * The constructor.
         *
         * @param declaringType      The declaring type
         * @param annotationMetadata The metadata
         * @param methodName         The method name
         * @param returnArgument     The return argument
         * @param arguments          The arguments
         * @param isAbstract         Is abstract
         * @param isSuspend          Is suspend
         */
        public MethodReference(Class<?> declaringType, AnnotationMetadata annotationMetadata, String methodName, Argument<?> returnArgument, Argument<?>[] arguments, boolean isAbstract, boolean isSuspend) {
            this.declaringType = declaringType;
            this.annotationMetadata = annotationMetadata == null ? AnnotationMetadata.EMPTY_METADATA : annotationMetadata;
            this.methodName = methodName;
            this.returnArgument = returnArgument;
            this.arguments = arguments == null ? Argument.ZERO_ARGUMENTS : arguments;
            this.isAbstract = isAbstract;
            this.isSuspend = isSuspend;
        }
    }

    /**
     * An {@link ExecutableMethod} instance based on dispatching by index.
     *
     * @param <T> The type
     * @param <R> The result type
     */
    private static final class DispatchedExecutableMethod<T, R> implements ExecutableMethod<T, R>, ReturnType<R>, EnvironmentConfigurable {

        private final AbstractExecutableMethodsDefinition dispatcher;
        private final int index;
        private final MethodReference methodReference;
        private AnnotationMetadata annotationMetadata;

        private DispatchedExecutableMethod(AbstractExecutableMethodsDefinition dispatcher, int index,
                                           MethodReference methodReference, AnnotationMetadata annotationMetadata) {
            this.dispatcher = dispatcher;
            this.index = index;
            this.methodReference = methodReference;
            this.annotationMetadata = annotationMetadata;
        }

        @Override
        public void configure(Environment environment) {
            if (annotationMetadata.hasPropertyExpressions()) {
                annotationMetadata = new MethodAnnotationMetadata(annotationMetadata, environment);
            }
        }

        @Override
        public boolean hasPropertyExpressions() {
            return annotationMetadata.hasPropertyExpressions();
        }

        @Override
        public boolean isAbstract() {
            return methodReference.isAbstract;
        }

        @Override
        public boolean isSuspend() {
            return methodReference.isSuspend;
        }

        @Override
        public Class<T> getDeclaringType() {
            return (Class<T>) methodReference.declaringType;
        }

        @Override
        public String getMethodName() {
            return methodReference.methodName;
        }

        @Override
        public Argument<?>[] getArguments() {
            return methodReference.arguments;
        }

        @Override
        public Method getTargetMethod() {
            return dispatcher.getTargetMethodByIndex(index);
        }

        @Override
        public ReturnType<R> getReturnType() {
            return this;
        }

        @Override
        public Class<R> getType() {
            if (methodReference.returnArgument == null) {
                return (Class<R>) void.class;
            }
            return (Class<R>) methodReference.returnArgument.getType();
        }

        @Override
        public boolean isSuspended() {
            return methodReference.isSuspend;
        }

        @NonNull
        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return annotationMetadata;
        }

        @Override
        public Argument[] getTypeParameters() {
            if (methodReference.returnArgument != null) {
                return methodReference.returnArgument.getTypeParameters();
            }
            return Argument.ZERO_ARGUMENTS;
        }

        @Override
        public Map<String, Argument<?>> getTypeVariables() {
            if (methodReference.returnArgument != null) {
                return methodReference.returnArgument.getTypeVariables();
            }
            return Collections.emptyMap();
        }

        @Override
        @NonNull
        public Argument asArgument() {
            Map<String, Argument<?>> typeVariables = getTypeVariables();
            Collection<Argument<?>> values = typeVariables.values();
            final AnnotationMetadata annotationMetadata = getAnnotationMetadata();
            return Argument.of(getType(), annotationMetadata, values.toArray(Argument.ZERO_ARGUMENTS));
        }

        @Override
        public R invoke(T instance, Object... arguments) {
            ArgumentUtils.validateArguments(this, methodReference.arguments, arguments);
            return (R) dispatcher.dispatch(index, instance, arguments);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof AbstractExecutableMethodsDefinition.DispatchedExecutableMethod)) {
                return false;
            }
            DispatchedExecutableMethod that = (DispatchedExecutableMethod) o;
            return Objects.equals(methodReference.declaringType, that.methodReference.declaringType) &&
                    Objects.equals(methodReference.methodName, that.methodReference.methodName) &&
                    Arrays.equals(methodReference.arguments, that.methodReference.arguments);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    methodReference.declaringType,
                    methodReference.methodName,
                    Arrays.hashCode(methodReference.arguments)
            );
        }
    }

    private static final class MethodAnnotationMetadata extends AbstractEnvironmentAnnotationMetadata {

        private final Environment environment;

        MethodAnnotationMetadata(AnnotationMetadata targetMetadata, Environment environment) {
            super(targetMetadata);
            this.environment = environment;
        }

        @Nullable
        @Override
        protected Environment getEnvironment() {
            return environment;
        }
    }

}
