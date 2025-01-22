/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.expressions.parser.ast.util;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.expressions.parser.exception.ExpressionCompilationException;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.TypeDef;

import java.util.Map;

import static java.util.stream.Collectors.toMap;

/**
 * Set of constants and utility methods for working with type descriptors
 * while compiling evaluated expressions.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class TypeDescriptors {

    public static final TypeDef STRING = TypeDef.STRING;
    public static final TypeDef OBJECT = TypeDef.OBJECT;
    public static final TypeDef CLASS = TypeDef.CLASS;
    public static final TypeDef VOID = TypeDef.VOID;

    // Primitives
    public static final TypeDef DOUBLE = TypeDef.Primitive.DOUBLE;
    public static final TypeDef FLOAT = TypeDef.Primitive.FLOAT;
    public static final TypeDef INT = TypeDef.Primitive.INT;
    public static final TypeDef LONG = TypeDef.Primitive.LONG;
    public static final TypeDef BOOLEAN = TypeDef.Primitive.BOOLEAN;
    public static final TypeDef CHAR = TypeDef.Primitive.CHAR;
    public static final TypeDef SHORT = TypeDef.Primitive.SHORT;
    public static final TypeDef BYTE = TypeDef.Primitive.BYTE;

    // Wrappers
    public static final ClassTypeDef BOOLEAN_WRAPPER = TypeDef.Primitive.BOOLEAN.wrapperType();
    public static final ClassTypeDef INT_WRAPPER = TypeDef.Primitive.INT.wrapperType();
    public static final ClassTypeDef LONG_WRAPPER = TypeDef.Primitive.LONG.wrapperType();
    public static final ClassTypeDef DOUBLE_WRAPPER = TypeDef.Primitive.DOUBLE.wrapperType();
    public static final ClassTypeDef FLOAT_WRAPPER = TypeDef.Primitive.FLOAT.wrapperType();
    public static final ClassTypeDef SHORT_WRAPPER = TypeDef.Primitive.SHORT.wrapperType();
    public static final ClassTypeDef BYTE_WRAPPER = TypeDef.Primitive.BYTE.wrapperType();
    public static final ClassTypeDef CHAR_WRAPPER = TypeDef.Primitive.CHAR.wrapperType();

    private static final Map<TypeDef, ClassTypeDef> PRIMITIVE_TO_WRAPPER = Map.of(
        BOOLEAN, BOOLEAN_WRAPPER,
        INT, INT_WRAPPER,
        DOUBLE, DOUBLE_WRAPPER,
        LONG, LONG_WRAPPER,
        FLOAT, FLOAT_WRAPPER,
        SHORT, SHORT_WRAPPER,
        CHAR, CHAR_WRAPPER,
        BYTE, BYTE_WRAPPER);

    public static final Map<String, TypeDef> WRAPPER_TO_PRIMITIVE =
        PRIMITIVE_TO_WRAPPER.entrySet()
            .stream()
            .collect(toMap(e -> e.getValue().getName(), Map.Entry::getKey));

    /**
     * Checks if passed type is a primitive.
     *
     * @param type type to check
     * @return true if it is
     */
    public static boolean isPrimitive(@NonNull TypeDef type) {
        return type.isPrimitive();
    }

    /**
     * Checks if passed type is either boolean primitive or wrapper.
     *
     * @param type type to check
     * @return true if it is
     */
    public static boolean isBoolean(@NonNull TypeDef type) {
        return isOneOf(type, BOOLEAN, BOOLEAN_WRAPPER);
    }

    /**
     * Checks if passed type is one of numeric primitives or numeric wrappers.
     *
     * @param type type to check
     * @return true if it is
     */
    @NonNull
    public static boolean isNumeric(@NonNull TypeDef type) {
        return isOneOf(type,
            DOUBLE, DOUBLE_WRAPPER,
            FLOAT, FLOAT_WRAPPER,
            INT, INT_WRAPPER,
            LONG, LONG_WRAPPER,
            SHORT, SHORT_WRAPPER,
            CHAR, CHAR_WRAPPER,
            BYTE, BYTE_WRAPPER);
    }

    /**
     * If passed type is boxed type, returns responsive primitive, otherwise returns
     * original passed type.
     *
     * @param type type to check
     * @return unboxed type or original passed type
     */
    @NonNull
    public static TypeDef toUnboxedIfNecessary(@NonNull TypeDef type) {
        if (type instanceof ClassTypeDef classTypeDef) {
            return WRAPPER_TO_PRIMITIVE.getOrDefault(classTypeDef.getName(), type);
        }
        return type;
    }

    /**
     * If passed type is primitive, returns responsive boxed type, otherwise returns
     * original passed type.
     *
     * @param type type to check
     * @return boxed type or original passed type
     */
    @NonNull
    public static ClassTypeDef toBoxedIfNecessary(@NonNull TypeDef type) {
        if (PRIMITIVE_TO_WRAPPER.containsKey(type)) {
            return PRIMITIVE_TO_WRAPPER.get(type);
        }
        if (type instanceof ClassTypeDef classTypeDef) {
            return classTypeDef;
        }
        throw new IllegalArgumentException("Unsupported type: " + type);
    }

    /**
     * For two passed types computes result numeric operation type. This method accepts
     * both primitive and wrapper types, but returns only primitive type.
     *
     * @param leftOperandType left operand type
     * @param rightOperandType right operand type
     * @return numeric operation result type
     * @throws ExpressionCompilationException if ony of the passed types is not a numeric type
     */
    @NonNull
    public static TypeDef computeNumericOperationTargetType(@NonNull TypeDef leftOperandType,
                                                         @NonNull TypeDef rightOperandType) {
        if (!isNumeric(leftOperandType) || !isNumeric(rightOperandType)) {
            throw new ExpressionCompilationException("Numeric operation can only be applied to numeric types");
        }

        if (toUnboxedIfNecessary(leftOperandType).equals(DOUBLE)
                || toUnboxedIfNecessary(rightOperandType).equals(DOUBLE)) {
            return DOUBLE;
        } else if (toUnboxedIfNecessary(leftOperandType).equals(FLOAT)
                       || toUnboxedIfNecessary(rightOperandType).equals(FLOAT)) {
            return FLOAT;
        } else if (toUnboxedIfNecessary(leftOperandType).equals(LONG)
                       || toUnboxedIfNecessary(rightOperandType).equals(LONG)) {
            return LONG;
        } else {
            return INT;
        }
    }

    /**
     * Utility method to check if passed type (first argument) is the same as any of
     * compared types (second and following args).
     *
     * @param type type to check
     * @param comparedTypes types against which checked types is compared
     * @return true if checked type is amount compared types
     */
    public static boolean isOneOf(TypeDef type, TypeDef... comparedTypes) {
        for (TypeDef comparedType: comparedTypes) {
            if (type.equals(comparedType)) {
                return true;
            }
        }
        return false;
    }
}
