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
import io.micronaut.core.expressions.ExpressionEvaluationContext;
import io.micronaut.expressions.parser.exception.ExpressionCompilationException;
import org.objectweb.asm.Type;

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
    public static final Type EVALUATION_CONTEXT_TYPE = Type.getType(ExpressionEvaluationContext.class);

    public static final Type STRING = Type.getType(String.class);
    public static final Type OBJECT = Type.getType(Object.class);
    public static final Type CLASS = Type.getType(Class.class);
    public static final Type VOID = Type.VOID_TYPE;

    // Primitives
    public static final Type DOUBLE = Type.DOUBLE_TYPE;
    public static final Type FLOAT = Type.FLOAT_TYPE;
    public static final Type INT = Type.INT_TYPE;
    public static final Type LONG = Type.LONG_TYPE;
    public static final Type BOOLEAN = Type.BOOLEAN_TYPE;
    public static final Type CHAR = Type.CHAR_TYPE;
    public static final Type SHORT = Type.SHORT_TYPE;
    public static final Type BYTE = Type.BYTE_TYPE;

    // Wrappers
    public static final Type BOOLEAN_WRAPPER = Type.getType(Boolean.class);
    public static final Type INT_WRAPPER = Type.getType(Integer.class);
    public static final Type LONG_WRAPPER = Type.getType(Long.class);
    public static final Type DOUBLE_WRAPPER = Type.getType(Double.class);
    public static final Type FLOAT_WRAPPER = Type.getType(Float.class);
    public static final Type SHORT_WRAPPER = Type.getType(Short.class);
    public static final Type BYTE_WRAPPER = Type.getType(Byte.class);
    public static final Type CHAR_WRAPPER = Type.getType(Character.class);

    public static final Map<Type, Type> PRIMITIVE_TO_WRAPPER = Map.of(
        BOOLEAN, BOOLEAN_WRAPPER,
        INT, INT_WRAPPER,
        DOUBLE, DOUBLE_WRAPPER,
        LONG, LONG_WRAPPER,
        FLOAT, FLOAT_WRAPPER,
        SHORT, SHORT_WRAPPER,
        CHAR, CHAR_WRAPPER,
        BYTE, BYTE_WRAPPER);

    public static final Map<Type, Type> WRAPPER_TO_PRIMITIVE =
        PRIMITIVE_TO_WRAPPER.entrySet()
            .stream()
            .collect(toMap(Map.Entry::getValue, Map.Entry::getKey));

    /**
     * Checks if passed type is a primitive.
     *
     * @param type type to check
     * @return true if it is
     */
    public static boolean isPrimitive(@NonNull Type type) {
        return PRIMITIVE_TO_WRAPPER.containsKey(type);
    }

    /**
     * Checks if passed type is either boolean primitive or wrapper.
     *
     * @param type type to check
     * @return true if it is
     */
    public static boolean isBoolean(@NonNull Type type) {
        return isOneOf(type, BOOLEAN, BOOLEAN_WRAPPER);
    }

    /**
     * Checks if passed type is one of numeric primitives or numeric wrappers.
     *
     * @param type type to check
     * @return true if it is
     */
    @NonNull
    public static boolean isNumeric(@NonNull Type type) {
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
    public static Type toUnboxedIfNecessary(@NonNull Type type) {
        if (WRAPPER_TO_PRIMITIVE.containsKey(type)) {
            return WRAPPER_TO_PRIMITIVE.get(type);
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
    public static Type toBoxedIfNecessary(@NonNull Type type) {
        if (PRIMITIVE_TO_WRAPPER.containsKey(type)) {
            return PRIMITIVE_TO_WRAPPER.get(type);
        }
        return type;
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
    public static Type computeNumericOperationTargetType(@NonNull Type leftOperandType,
                                                         @NonNull Type rightOperandType) {
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
    public static boolean isOneOf(Type type, Type... comparedTypes) {
        for (Type comparedType: comparedTypes) {
            if (type.equals(comparedType)) {
                return true;
            }
        }
        return false;
    }
}
