/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.inject.writer;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.KotlinParameterElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.processing.JavaModelUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static io.micronaut.inject.writer.AbstractClassFileWriter.getConstructorDescriptor;
import static io.micronaut.inject.writer.AbstractClassFileWriter.getMethodDescriptor;
import static io.micronaut.inject.writer.AbstractClassFileWriter.getTypeReference;
import static io.micronaut.inject.writer.AbstractClassFileWriter.pushNewArray;
import static io.micronaut.inject.writer.AbstractClassFileWriter.pushNewArrayIndexed;

/**
 * The writer utils.
 *
 * @author Denis Stepanov
 * @since 4.3.0
 */
@Internal
public final class WriterUtils {
    private static final String METHOD_NAME_INSTANTIATE = "instantiate";

    public static void invokeBeanConstructor(GeneratorAdapter writer,
                                             MethodElement constructor,
                                             boolean allowKotlinDefaults,
                                             @Nullable
                                             BiConsumer<Integer, ParameterElement> argumentsPusher) {
        invokeBeanConstructor(writer, constructor, allowKotlinDefaults, argumentsPusher, null);
    }

    public static void invokeBeanConstructor(GeneratorAdapter writer,
                                             MethodElement constructor,
                                             boolean allowKotlinDefaults,
                                             @Nullable
                                             BiConsumer<Integer, ParameterElement> argumentsPusher,
                                             @Nullable
                                             BiFunction<Integer, ParameterElement, Boolean> argumentValueIsPresentPusher) {
        Type beanType = getTypeReference(constructor.getOwningType());
        boolean isConstructor = constructor.getName().equals("<init>");
        ClassElement declaringType = constructor.getOwningType();
        boolean isCompanion = declaringType.getSimpleName().endsWith("$Companion");

        List<ParameterElement> constructorArguments = Arrays.asList(constructor.getParameters());
        Collection<Type> argumentTypes = constructorArguments.stream().map(pe ->
            JavaModelUtils.getTypeReference(pe.getType())
        ).toList();
        boolean isKotlinDefault = allowKotlinDefaults && constructorArguments.stream().anyMatch(p -> p instanceof KotlinParameterElement kp && kp.hasDefault());

        int maskLocal = -1;
        if (isKotlinDefault) {
            // Calculate the Kotlin defaults mask
            // Every bit indicated true/false if the parameter should have the default value set
            maskLocal = DispatchWriter.computeKotlinDefaultsMask(writer, argumentsPusher, argumentValueIsPresentPusher, constructorArguments);
        }

        if (constructor.isReflectionRequired() && !isCompanion) { // Companion reflection not implemented
            writer.push(beanType);
            pushNewArray(writer, Class.class, constructorArguments, arg -> writer.push(getTypeReference(arg)));
            pushNewArrayIndexed(writer, Object.class, constructorArguments, (i, parameter) -> {
                pushValue(writer, argumentsPusher, parameter, i);
                if (parameter.isPrimitive()) {
                    writer.unbox(getTypeReference(parameter));
                }
            });
            writer.invokeStatic(
                Type.getType(InstantiationUtils.class),
                org.objectweb.asm.commons.Method.getMethod(
                    ReflectionUtils.getRequiredInternalMethod(
                        InstantiationUtils.class,
                        METHOD_NAME_INSTANTIATE,
                        Class.class,
                        Class[].class,
                        Object[].class
                    )
                )
            );
            if (JavaModelUtils.isPrimitive(beanType)) {
                writer.unbox(beanType);
            } else {
                writer.checkCast(beanType);
            }
            return;
        }

        if (!constructor.isStatic()) {
            if (isConstructor) {
                writer.newInstance(beanType);
                writer.dup();
            } else if (isCompanion) {
                writer.getStatic(
                    getTypeReference(constructor.getReturnType()),
                    "Companion",
                    JavaModelUtils.getTypeReference(declaringType)
                );
            }
        }

        int index = 0;
        for (ParameterElement constructorArgument : constructorArguments) {
            pushValue(writer, argumentsPusher, constructorArgument, index);
            index++;
        }

        if (isConstructor) {
            final String constructorDescriptor = getConstructorDescriptor(constructorArguments);
            Method method = new Method("<init>", constructorDescriptor);
            if (isKotlinDefault) {
                method = asDefaultKotlinConstructor(method);
                writer.loadLocal(maskLocal, Type.INT_TYPE); // Bit mask of defaults
                writer.push((String) null); // Last parameter is just a marker and is always null
            }
            writer.invokeConstructor(beanType, method);
        } else if (constructor.isStatic()) {
            final String methodDescriptor = getMethodDescriptor(getTypeReference(constructor.getReturnType()), argumentTypes);
            Method method = new Method(constructor.getName(), methodDescriptor);
            if (constructor.getOwningType().isInterface()) {
                writer.invokeStatic(getTypeReference(declaringType), method);
            } else {
                writer.invokeStatic(beanType, method);
            }
        } else if (isCompanion) {
            if (constructor.isStatic()) {
                writer.invokeStatic(
                    JavaModelUtils.getTypeReference(declaringType),
                    new Method(constructor.getName(), getMethodDescriptor(getTypeReference(constructor.getReturnType()), argumentTypes))
                );
            } else {
                writer.invokeVirtual(
                    JavaModelUtils.getTypeReference(declaringType),
                    new Method(constructor.getName(), getMethodDescriptor(getTypeReference(constructor.getReturnType()), argumentTypes))
                );
            }
        }
    }

    private static void pushValue(GeneratorAdapter writer,
                                  @Nullable
                                  BiConsumer<Integer, ParameterElement> argumentsPusher,
                                  ParameterElement parameter,
                                  int index) {
        if (argumentsPusher == null) {
            pushDefaultTypeValue(writer, parameter.getType());
        } else {
            argumentsPusher.accept(index, parameter);
        }
    }

    /**
     * Pushed a default value.
     *
     * @param writer The writer
     * @param type   The type
     */
    public static void pushDefaultTypeValue(GeneratorAdapter writer, ClassElement type) {
        if (type.isPrimitive() && !type.isArray()) {
            if (type.equals(PrimitiveElement.BOOLEAN)) {
                writer.push(false);
            } else {
                writer.push(0);
                writer.cast(Type.INT_TYPE, JavaModelUtils.getTypeReference(type));
            }
        } else {
            writer.push((String) null);
        }
    }

    private static Method asDefaultKotlinConstructor(Method method) {
        Type[] argumentTypes = method.getArgumentTypes();
        int length = argumentTypes.length;
        Type[] newArgumentTypes = Arrays.copyOf(argumentTypes, length + 2);
        newArgumentTypes[length] = Type.INT_TYPE;
        newArgumentTypes[length + 1] = Type.getObjectType("kotlin/jvm/internal/DefaultConstructorMarker");
        return new Method(method.getName(), method.getReturnType(), newArgumentTypes);
    }

}
