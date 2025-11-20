/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.context.python.aop;

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.chain.MethodInterceptorChain;
import io.micronaut.aop.internal.ProxySetupAware;
import io.micronaut.context.AbstractExecutableMethod;
import io.micronaut.context.python.ValueCoercible;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ExecutableMethod;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

/// Sets up Python AOP proxies by replacing the original methods in the [Value] which represents a class,
/// with the new methods that invoke the interceptor chain and then the original method.
///
/// NOTE: Internal interface not for public consumption.
@Internal
public interface PythonAopSetup extends ProxySetupAware, ValueCoercible {

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    default void $proxyInitialized(ProxySetup proxySetup) {
        Value pythonClass = asPolyglotValue();
        if (pythonClass != null) {
            Context context = pythonClass.getContext();
            Value wrapFunction = PythonAopUtil.setupWrapFunction(context);

            ExecutableMethod[] executableMethods = proxySetup.proxyMethods();
            Interceptor[][] allInterceptors = proxySetup.interceptors();
            for (int i = 0; i < executableMethods.length; i++) {
                ExecutableMethod executableMethod = executableMethods[i];
                Interceptor[] interceptors = allInterceptors[i];

                String methodName = executableMethod.getMethodName();
                Value pythonFunction = pythonClass.getMember(methodName);
                if (pythonFunction != null && pythonFunction.canExecute()) {
                    // construct a new Executable method that invokes the original function


                    ProxyExecutable chainExecutable = args -> {
                        // args[0] = original Python method
                        // args[1] = tuple of positional args
                        // args[2] = dict of keyword args

                        Value pyArgs = args[1];
                        // what to do about named arguments?
                        // Value pyKw = args[2];

                        ExecutableMethod pythonTargetMethod = new PythonExecutableMethod(executableMethod, methodName, pythonFunction);

                        MethodInterceptorChain chain = new MethodInterceptorChain<>(
                            interceptors,
                            pythonClass,
                            pythonTargetMethod,
                            toObjectArray(pyArgs)
                        );

                        return chain.proceed();
                    };

                    Value wrapped = wrapFunction.execute(pythonFunction, chainExecutable);
                    // remove interceptors from Java chain, processed by Python instead
                    allInterceptors[i] = new Interceptor[0];
                    pythonClass.putMember(methodName, wrapped);
                }
            }
        }
    }

    private static Object[] toObjectArray(Value pyArray) {
        if (!pyArray.hasArrayElements()) {
            throw new IllegalArgumentException("Value is not an array");
        }

        int size = (int) pyArray.getArraySize();
        Object[] result = new Object[size];

        for (int i = 0; i < size; i++) {
            result[i] = pyArray.getArrayElement(i).as(Object.class);
        }

        return result;
    }

    /**
     * An executable method representation that invokes the original funciton.
     */
    @SuppressWarnings("rawtypes")
    final class PythonExecutableMethod extends AbstractExecutableMethod {
        private final ExecutableMethod executableMethod;
        private final Value pythonFunction;

        PythonExecutableMethod(ExecutableMethod executableMethod, String methodName, Value pythonFunction) {
            super(executableMethod.getDeclaringType(), methodName, executableMethod.getReturnType().asArgument(), executableMethod.getArguments());
            this.executableMethod = executableMethod;
            this.pythonFunction = pythonFunction;
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return executableMethod.getAnnotationMetadata();
        }

        @Override
        public Object invokeUnsafe(Object instance, Object... arguments) {
            return pythonFunction.execute(arguments);
        }

        @Override
        protected Object invokeInternal(Object instance, Object[] arguments) {
            return pythonFunction.execute(arguments);
        }
    }
}
