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
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.TypeDef;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The writer utils.
 *
 * @author Denis Stepanov
 * @since 4.7
 */
@Internal
public final class MethodGenUtils {

    private static final TypeDef KOTLIN_CONSTRUCTOR_MARKER = TypeDef.of("kotlin.jvm.internal.DefaultConstructorMarker");

    private static final java.lang.reflect.Method INSTANTIATE_METHOD = ReflectionUtils.getRequiredInternalMethod(
            InstantiationUtils.class,
            "instantiate",
            Class.class,
            Class[].class,
            Object[].class
    );

    /**
     * The number of Kotlin defaults masks.
     *
     * @param parameters The parameters
     * @return The number if masks
     * @since 4.6.2
     */
    public static int calculateNumberOfKotlinDefaultsMasks(List<ParameterElement> parameters) {
        return (int) Math.ceil(parameters.size() / 32.0);
    }

    /**
     * Checks if parameter include Kotlin defaults.
     *
     * @param arguments The arguments
     * @return true if include
     * @since 4.6.2
     */
    public static boolean hasKotlinDefaultsParameters(List<ParameterElement> arguments) {
        return arguments.stream().anyMatch(p -> p instanceof KotlinParameterElement kp && kp.hasDefault());
    }

    public static ExpressionDef invokeKotlinDefaultMethod(ClassElement declaringType,
                                                          MethodElement methodElement,
                                                          ExpressionDef target,
                                                          List<? extends ExpressionDef> values,
                                                          List<? extends ExpressionDef> hasValuesExpressions) {
        int numberOfMasks = MethodGenUtils.calculateNumberOfKotlinDefaultsMasks(List.of(methodElement.getSuspendParameters()));
        ExpressionDef[] masks = MethodGenUtils.computeKotlinDefaultsMask(numberOfMasks, List.of(methodElement.getSuspendParameters()), hasValuesExpressions);
        List<ExpressionDef> newValues = new ArrayList<>();
        newValues.add(target);
        newValues.addAll(values);
        newValues.addAll(List.of(masks)); // Bit mask of defaults
        newValues.add(ExpressionDef.nullValue()); // Last parameter is just a marker and is always null

        MethodDef defaultKotlinMethod = MethodGenUtils.asDefaultKotlinMethod(TypeDef.of(declaringType), methodElement, numberOfMasks);

        return ClassTypeDef.of(declaringType).invokeStatic(defaultKotlinMethod, newValues);
    }

    public static ExpressionDef invokeKotlinDefaultMethod(ClassElement declaringType,
                                                          MethodElement methodElement,
                                                          ExpressionDef target,
                                                          List<? extends ExpressionDef> values) {
        return invokeKotlinDefaultMethod(declaringType, methodElement, target, values, values.stream().map(ExpressionDef::isNonNull).toList());
    }

    public static ExpressionDef invokeBeanConstructor(MethodElement constructor,
                                                      boolean allowKotlinDefaults,
                                                      @Nullable
                                                      List<? extends ExpressionDef> values) {
        return invokeBeanConstructor(constructor, constructor.isReflectionRequired(), allowKotlinDefaults, values, values == null ? null : values.stream().map(ExpressionDef::isNonNull).toList());
    }

    public static ExpressionDef invokeBeanConstructor(MethodElement constructor,
                                                      boolean requiresReflection,
                                                      boolean allowKotlinDefaults,
                                                      @Nullable
                                                      List<? extends ExpressionDef> values,
                                                      @Nullable
                                                      List<? extends ExpressionDef> hasValuesExpressions) {
        ClassTypeDef beanType = (ClassTypeDef) TypeDef.erasure(constructor.getOwningType());

        boolean isConstructor = constructor.getName().equals("<init>");
        boolean isCompanion = constructor.getOwningType().getSimpleName().endsWith("$Companion");
        List<ParameterElement> constructorArguments = Arrays.asList(constructor.getParameters());
        allowKotlinDefaults = allowKotlinDefaults && hasKotlinDefaultsParameters(constructorArguments);

        List<ExpressionDef> constructorValues = constructorValues(constructor.getParameters(), values, allowKotlinDefaults);

        if (requiresReflection && !isCompanion) { // Companion and reflection not implemented
            return ClassTypeDef.of(InstantiationUtils.class).invokeStatic(
                    INSTANTIATE_METHOD,

                    ExpressionDef.constant(beanType),
                    TypeDef.CLASS.array().instantiate(
                            Arrays.stream(constructor.getParameters()).map(param ->
                                    ExpressionDef.constant(TypeDef.erasure(param.getType()))
                            ).toList()
                    ),
                    TypeDef.OBJECT.array().instantiate(constructorValues)
            );
        }

        if (isConstructor) {
            if (allowKotlinDefaults) {
                int numberOfMasks = calculateNumberOfKotlinDefaultsMasks(constructorArguments);
                // Calculate the Kotlin defaults mask
                // Every bit indicated true/false if the parameter should have the default value set
                ExpressionDef[] masksExpressions = computeKotlinDefaultsMask(numberOfMasks, constructorArguments, hasValuesExpressions);

                List<ExpressionDef> newValues = new ArrayList<>();
                newValues.addAll(constructorValues);
                newValues.addAll(List.of(masksExpressions)); // Bit mask of defaults
                newValues.add(ExpressionDef.nullValue()); // Last parameter is just a marker and is always null
                List<TypeDef> defaultKotlinConstructorParameters = getDefaultKotlinConstructorParameters(constructor.getParameters(), masksExpressions.length);
                return beanType.instantiate(
                        defaultKotlinConstructorParameters,
                        newValues
                );
            }
            return beanType.instantiate(constructor, constructorValues);
        } else if (constructor.isStatic()) {
            return beanType.invokeStatic(constructor, constructorValues);
        } else if (isCompanion) {
            if (constructor.isStatic()) {
                return beanType.invokeStatic(constructor, constructorValues);
            }
            return ((ClassTypeDef) TypeDef.erasure(constructor.getReturnType()))
                    .getStaticField("Companion", beanType)
                    .invoke(constructor, constructorValues);
        }
        throw new IllegalStateException("Unknown constructor");
    }

    private static List<ExpressionDef> constructorValues(ParameterElement[] constructorArguments,
                                                         @Nullable
                                                         List<? extends ExpressionDef> values,
                                                         boolean addKotlinDefaults) {
        List<ExpressionDef> expressions = new ArrayList<>(constructorArguments.length);
        for (int i = 0; i < constructorArguments.length; i++) {
            ParameterElement constructorArgument = constructorArguments[i];
            ExpressionDef value = values == null ? null : values.get(i);
            if (value != null) {
                if (!addKotlinDefaults || value instanceof ExpressionDef.Constant constant && constant.value() != null || !constructorArgument.isPrimitive()) {
                    expressions.add(value);
                } else {
                    expressions.add(
                            ClassTypeDef.of(Objects.class)
                                    .invokeStatic(
                                            ReflectionUtils.getRequiredMethod(Objects.class, "requireNonNullElse", Object.class, Object.class),

                                            value.cast(TypeDef.OBJECT), // Remove any previous casts
                                            getDefaultValue(constructorArgument)
                                    ).cast(value.type())
                    );
                }
                continue;
            }
            expressions.add(getDefaultValue(constructorArgument));
        }
        return expressions;
    }

    private static ExpressionDef getDefaultValue(ParameterElement constructorArgument) {
        ClassElement type = constructorArgument.getType();
        if (type.isPrimitive() && !type.isArray()) {
            if (type.equals(PrimitiveElement.BOOLEAN)) {
                return ExpressionDef.falseValue();
            }
            return TypeDef.Primitive.INT.constant(0).cast(TypeDef.erasure(type));
        }
        return ExpressionDef.nullValue();
    }

    private static List<TypeDef> getDefaultKotlinConstructorParameters(ParameterElement[] constructorArguments, int numberOfMasks) {
        List<TypeDef> parameters = new ArrayList<>(constructorArguments.length + numberOfMasks + 1);
        for (ParameterElement constructorArgument : constructorArguments) {
            parameters.add(TypeDef.erasure(constructorArgument.getType()));
        }
        for (int i = 0; i < numberOfMasks; i++) {
            parameters.add(TypeDef.Primitive.INT);
        }
        parameters.add(KOTLIN_CONSTRUCTOR_MARKER);
        return parameters;
    }

    /**
     * Create a method for Kotlin default invocation.
     *
     * @param owningType    The owing type
     * @param method        The method
     * @param numberOfMasks The number of default masks
     * @return A new method
     */
    static MethodDef asDefaultKotlinMethod(TypeDef owningType, MethodElement method, int numberOfMasks) {
        ParameterElement[] prevParameters = method.getSuspendParameters();
        List<TypeDef> parameters = new ArrayList<>(1 + prevParameters.length + numberOfMasks + 1);
        parameters.add(owningType);
        for (ParameterElement constructorArgument : prevParameters) {
            parameters.add(TypeDef.erasure(constructorArgument.getType()));
        }
        for (int i = 0; i < numberOfMasks; i++) {
            parameters.add(TypeDef.Primitive.INT);
        }
        parameters.add(TypeDef.OBJECT);
        return MethodDef.builder(method.getName() + "$default")
                .addParameters(parameters)
                .returns(method.isSuspend() ? TypeDef.OBJECT : TypeDef.erasure(method.getReturnType()))
                .build();
    }

    public static ExpressionDef[] computeKotlinDefaultsMask(int numberOfMasks,
                                                            List<ParameterElement> parameters,
                                                            @Nullable
                                                            List<? extends ExpressionDef> hasValuesExpressions) {
        ExpressionDef[] masksLocal = new ExpressionDef[numberOfMasks];
        for (int i = 0; i < numberOfMasks; i++) {
            int fromIndex = i * 32;
            List<ParameterElement> params = parameters.subList(fromIndex, Math.min(fromIndex + 32, parameters.size()));
            if (hasValuesExpressions == null) {
                masksLocal[i] = TypeDef.Primitive.INT.constant((int) ((long) Math.pow(2, params.size() + 1) - 1));
            } else {
                ExpressionDef maskValue = TypeDef.Primitive.INT.constant(0);
                int maskIndex = 1;
                int paramIndex = fromIndex;
                for (ParameterElement parameter : params) {
                    if (parameter instanceof KotlinParameterElement kp && kp.hasDefault()) {
                        maskValue = writeMask(hasValuesExpressions, kp, paramIndex, maskIndex, maskValue);
                    }
                    maskIndex *= 2;
                    paramIndex++;
                }
                masksLocal[i] = maskValue;
            }
        }
        return masksLocal;
    }

    private static ExpressionDef writeMask(@Nullable
                                           List<? extends ExpressionDef> hasValuesExpressions,
                                           KotlinParameterElement kp,
                                           int paramIndex,
                                           int maskIndex,
                                           ExpressionDef maskValue) {
        TypeDef.Primitive intType = TypeDef.Primitive.INT;
        if (hasValuesExpressions != null) {
            return maskValue.math("|",
                    hasValuesExpressions.get(paramIndex).ifTrue(
                            intType.constant(0),
                            intType.constant(maskIndex)
                    )
            );
        } else if (kp.getType().isPrimitive() && !kp.getType().isArray()) {
            // We cannot recognize the default from a primitive value
            return maskValue;
        }
        return maskValue;
    }

}
