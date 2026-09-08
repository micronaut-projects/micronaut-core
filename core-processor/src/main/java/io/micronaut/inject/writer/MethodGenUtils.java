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
import io.micronaut.sourcegen.model.StatementDef;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;
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
@NullUnmarked
public final class MethodGenUtils {

    private static final TypeDef KOTLIN_CONSTRUCTOR_MARKER = TypeDef.of("kotlin.jvm.internal.DefaultConstructorMarker");

    private static final java.lang.reflect.Method REQUIRE_NON_NULL_ELSE_METHOD = ReflectionUtils.getRequiredMethod(
            Objects.class,
            "requireNonNullElse",
            Object.class,
            Object.class
    );

    private static final java.lang.reflect.Method INSTANTIATE_METHOD = ReflectionUtils.getRequiredInternalMethod(
            InstantiationUtils.class,
            "instantiateReflectively",
            Class.class,
            Class[].class,
            Object[].class
    );

    private MethodGenUtils() {
    }

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

    /**
     * Checks if any parameter declares a default that this class knows how to honour, either
     * through Kotlin's calling convention or through a caller-side default value.
     *
     * @param arguments The arguments
     * @return true if any argument has a default this class can generate code for
     * @since 5.2.0
     */
    public static boolean hasDefaultsParameters(List<ParameterElement> arguments) {
        return hasKotlinDefaultsParameters(arguments) || hasCallerSideDefaultsParameters(arguments);
    }

    /**
     * Checks if any parameter has a default value that can be materialised at the call site.
     *
     * @param arguments The arguments
     * @return true if any argument has a caller-side default value expression
     * @since 5.2.0
     */
    public static boolean hasCallerSideDefaultsParameters(List<ParameterElement> arguments) {
        return arguments.stream().anyMatch(p -> callerSideDefault(p) != null);
    }

    /**
     * Resolves the caller-side default value expression of a parameter, if it has one.
     *
     * <p>Parameters using Kotlin's calling convention are excluded: their defaults are computed
     * by the callee and cannot be materialised here.</p>
     */
    @Nullable
    private static ExpressionDef callerSideDefault(ParameterElement parameter) {
        if (parameter instanceof KotlinParameterElement || !parameter.hasDefault()) {
            return null;
        }
        for (ParameterDefaultValueProvider provider : ParameterDefaultValueProviderLoader.load()) {
            if (provider.supports(parameter)) {
                ExpressionDef expression = provider.defaultValueExpression(parameter, null).orElse(null);
                if (expression != null) {
                    return expression;
                }
            }
        }
        return null;
    }

    public static ExpressionDef invokeKotlinDefaultMethod(ClassElement declaringType,
                                                          MethodElement methodElement,
                                                          ExpressionDef target,
                                                          List<? extends ExpressionDef> values) {
        return invokeKotlinDefaultMethod(declaringType, methodElement, target, values, values.stream().map(ExpressionDef::isNonNull).toList());
    }

    public static ExpressionDef invokeBeanConstructor(ClassElement callingType,
                                                      MethodElement constructor,
                                                      boolean allowDefaults,
                                                      @Nullable
                                                      List<? extends ExpressionDef> values,
                                                      List<StatementDef> additionalStatements) {
        return invokeBeanConstructor(
            constructor,
            constructor.isReflectionRequired(callingType),
            allowDefaults,
            values,
            values == null ? null : values.stream().map(ExpressionDef::isNonNull).toList(),
            additionalStatements
        );
    }

    public static ExpressionDef invokeBeanConstructor(MethodElement constructor,
                                                      boolean requiresReflection,
                                                      boolean allowDefaults,
                                                      @Nullable
                                                      List<? extends ExpressionDef> values,
                                                      @Nullable
                                                      List<? extends ExpressionDef> hasValuesExpressions,
                                                      List<StatementDef> additionalStatements) {
        ClassTypeDef beanType = (ClassTypeDef) TypeDef.erasure(constructor.getOwningType());

        boolean isConstructor = constructor.getName().equals("<init>");
        boolean isCompanion = constructor.getOwningType().getSimpleName().endsWith("$Companion");
        List<ParameterElement> constructorArguments = Arrays.asList(constructor.getParameters());
        boolean allowKotlinDefaults = allowDefaults && hasKotlinDefaultsParameters(constructorArguments);
        boolean allowCallerSideDefaults = allowDefaults && !allowKotlinDefaults;

        List<ExpressionDef> constructorValues = constructorValues(constructor.getParameters(), values, hasValuesExpressions, allowKotlinDefaults, allowCallerSideDefaults);

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
                return beanType.instantiate(
                    getDefaultKotlinConstructorParameters(constructor.getParameters(), numberOfMasks),
                    createKotlinDefaultValues(hasValuesExpressions, additionalStatements, numberOfMasks, constructorArguments, constructorValues)
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

    public static StatementDef invokeSuperConstructor(ExpressionDef superVar,
                                                       MethodElement constructor,
                                                       boolean allowDefaults,
                                                       @Nullable
                                                       List<? extends ExpressionDef> values,
                                                       @Nullable
                                                       List<? extends ExpressionDef> hasValuesExpressions,
                                                       List<StatementDef> additionalStatements) {
        List<ParameterElement> constructorArguments = Arrays.asList(constructor.getParameters());
        boolean allowKotlinDefaults = allowDefaults && hasKotlinDefaultsParameters(constructorArguments);
        boolean allowCallerSideDefaults = allowDefaults && !allowKotlinDefaults;

        List<ExpressionDef> constructorValues = constructorValues(constructor.getParameters(), values, hasValuesExpressions, allowKotlinDefaults, allowCallerSideDefaults);
        if (allowKotlinDefaults) {
            int numberOfMasks = calculateNumberOfKotlinDefaultsMasks(constructorArguments);
            return superVar.invokeConstructor(
                getDefaultKotlinConstructorParameters(constructor.getParameters(), numberOfMasks),
                createKotlinDefaultValues(hasValuesExpressions, additionalStatements, numberOfMasks, constructorArguments, constructorValues)
            );
        }
        return superVar.invokeConstructor(MethodDef.of(constructor), constructorValues);
    }

    private static List<ExpressionDef> createKotlinDefaultValues(List<? extends ExpressionDef> hasValuesExpressions, List<StatementDef> additionalStatements, int numberOfMasks, List<ParameterElement> constructorArguments, List<ExpressionDef> constructorValues) {
        // Calculate the Kotlin defaults mask
        // Every bit indicated true/false if the parameter should have the default value set
        ExpressionDef[] masksExpressions = computeKotlinDefaultsMask(numberOfMasks, constructorArguments, hasValuesExpressions);
        List<ExpressionDef> maskValues = new ArrayList<>(masksExpressions.length);
        int i = 0;
        for (ExpressionDef masksExpression : masksExpressions) {
            StatementDef.DefineAndAssign defineAndAssign = masksExpression.newLocal("mask" + i++);
            additionalStatements.add(defineAndAssign);
            maskValues.add(defineAndAssign.variable());
        }

        List<ExpressionDef> newValues = new ArrayList<>();
        newValues.addAll(constructorValues);
        newValues.addAll(maskValues); // Bit mask of defaults
        newValues.add(ExpressionDef.nullValue()); // The last parameter is just a marker and is always null
        return newValues;
    }

    private static ExpressionDef invokeKotlinDefaultMethod(ClassElement declaringType,
                                                           MethodElement methodElement,
                                                           ExpressionDef target,
                                                           List<? extends ExpressionDef> values,
                                                           List<? extends @Nullable ExpressionDef> hasValuesExpressions) {
        int numberOfMasks = MethodGenUtils.calculateNumberOfKotlinDefaultsMasks(List.of(methodElement.getSuspendParameters()));
        ExpressionDef[] masks = MethodGenUtils.computeKotlinDefaultsMask(numberOfMasks, List.of(methodElement.getSuspendParameters()), hasValuesExpressions);
        List<ExpressionDef> newValues = new ArrayList<>();
        newValues.add(target);
        newValues.addAll(values);
        newValues.addAll(List.of(masks)); // Bit mask of defaults
        newValues.add(ExpressionDef.nullValue()); // The last parameter is just a marker and is always null

        return ClassTypeDef.of(declaringType).invokeStatic(
            MethodGenUtils.asDefaultKotlinMethod(TypeDef.erasure(declaringType), methodElement, numberOfMasks),
            newValues
        );
    }

    private static List<ExpressionDef> constructorValues(ParameterElement[] constructorArguments,
                                                         @Nullable
                                                         List<? extends ExpressionDef> values,
                                                         @Nullable
                                                         List<? extends ExpressionDef> hasValuesExpressions,
                                                         boolean addKotlinDefaults,
                                                         boolean addCallerSideDefaults) {
        List<ExpressionDef> expressions = new ArrayList<>(constructorArguments.length);
        for (int i = 0; i < constructorArguments.length; i++) {
            expressions.add(
                constructorValue(
                    constructorArguments[i],
                    values == null ? null : values.get(i),
                    hasValuesExpressions == null ? null : hasValuesExpressions.get(i),
                    addKotlinDefaults,
                    addCallerSideDefaults
                )
            );
        }
        return expressions;
    }

    private static ExpressionDef constructorValue(ParameterElement constructorArgument,
                                                  @Nullable
                                                  ExpressionDef value,
                                                  @Nullable
                                                  ExpressionDef hasValueExpression,
                                                  boolean addKotlinDefaults,
                                                  boolean addCallerSideDefaults) {
        ExpressionDef callerSideDefault = addCallerSideDefaults ? callerSideDefault(constructorArgument) : null;
        if (callerSideDefault != null) {
            return callerSideDefaultValue(value, hasValueExpression, callerSideDefault);
        }
        ExpressionDef defaultValue = getDefaultValue(constructorArgument);
        if (value == null) {
            return defaultValue;
        }
        if (!addKotlinDefaults || value instanceof ExpressionDef.Constant constant && constant.value() != null) {
            return value;
        }
        if (hasValueExpression != null) {
            if (defaultValue.equals(ExpressionDef.nullValue())) {
                return value; // Null value anyway
            }
            return hasValueExpression.isTrue().doIfElse(value, defaultValue);
        }
        if (!constructorArgument.isPrimitive()) {
            return value;
        }
        return ClassTypeDef.of(Objects.class)
            .invokeStatic(
                REQUIRE_NON_NULL_ELSE_METHOD,

                value.cast(TypeDef.OBJECT), // Remove any previous casts
                defaultValue
            ).cast(value.type());
    }

    /**
     * Selects between the supplied value and the parameter's declared default, for a language
     * that evaluates defaults in the caller.
     *
     * <p>Substitution requires a presence expression. Without one the supplied value is passed
     * through unchanged, rather than treating a {@code null} value as absent — a language may
     * allow an explicit {@code null} to be passed to a nullable parameter that also has a
     * default, and that {@code null} must not silently become the default.</p>
     *
     * @param value             The supplied value, or {@code null} if no value is supplied at all
     * @param hasValueExpression An expression that is true when the value is present, if known
     * @param defaultValue      The declared default value expression
     * @return The expression to pass in argument position
     */
    private static ExpressionDef callerSideDefaultValue(@Nullable ExpressionDef value,
                                                        @Nullable ExpressionDef hasValueExpression,
                                                        ExpressionDef defaultValue) {
        if (value == null) {
            // Nothing is supplied for this parameter, so the default always applies
            return defaultValue;
        }
        if (value instanceof ExpressionDef.Constant constant && constant.value() != null) {
            // A known non-null constant is always present
            return value;
        }
        if (hasValueExpression == null) {
            // Presence is unknown, so the value cannot be distinguished from an explicit null
            return value;
        }
        return hasValueExpression.isTrue().doIfElse(value, defaultValue);
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

    private static MethodDef asDefaultKotlinMethod(TypeDef owningType, MethodElement method, int numberOfMasks) {
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

    private static ExpressionDef[] computeKotlinDefaultsMask(int numberOfMasks,
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
            return maskValue.math(ExpressionDef.MathBinaryOperation.OpType.BITWISE_OR,
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
