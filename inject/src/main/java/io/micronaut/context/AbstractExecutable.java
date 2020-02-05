/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;
import io.micronaut.core.util.ArrayUtils;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

/**
 * An abstract {@link Executable} for a method.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
abstract class AbstractExecutable implements Executable {

    protected final Class declaringType;
    protected final String methodName;
    protected final Class[] argTypes;

    private Argument[] arguments;
    private Method method;

    /**
     * @param declaringType The declaring type
     * @param methodName    The method name
     * @param arguments     The arguments
     */
    AbstractExecutable(Class declaringType, String methodName, Argument[] arguments) {
        Objects.requireNonNull(declaringType, "Declaring type cannot be null");
        Objects.requireNonNull(methodName, "Method name cannot be null");

        this.argTypes = Argument.toClassArray(arguments);
        this.declaringType = declaringType;
        this.methodName = methodName;

        if (ArrayUtils.isNotEmpty(arguments)) {
            this.arguments = arguments;
        } else {
            this.arguments = Argument.ZERO_ARGUMENTS;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AbstractExecutable)) {
            return false;
        }
        AbstractExecutable that = (AbstractExecutable) o;
        return Objects.equals(declaringType, that.declaringType) &&
                Objects.equals(methodName, that.methodName) &&
                Arrays.equals(argTypes, that.argTypes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(declaringType, methodName);
        result = 31 * result + Arrays.hashCode(argTypes);
        return result;
    }

    @Override
    public Argument[] getArguments() {
        return arguments;
    }

    /**
     * Soft resolves the target {@link Method} avoiding reflection until as late as possible.
     *
     * @return The method
     * @throws NoSuchMethodError if the method doesn't exist
     */
    public final Method getTargetMethod() {
        if (method == null) {
            Method resolvedMethod = resolveTargetMethod();
            resolvedMethod.setAccessible(true);
            this.method = resolvedMethod;
        }
        return this.method;
    }

    /**
     * Resolves the target method.
     * @return The target method
     */
    @NonNull
    @UsedByGeneratedCode
    protected Method resolveTargetMethod() {
        return ReflectionUtils.getRequiredMethod(declaringType, methodName, argTypes);
    }

}
