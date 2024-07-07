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
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.visitor.VisitorContext;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.Optional;

import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.BOOLEAN;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.BOOLEAN_WRAPPER;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.BYTE;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.BYTE_WRAPPER;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.CHAR;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.CHAR_WRAPPER;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.DOUBLE;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.DOUBLE_WRAPPER;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.FLOAT;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.FLOAT_WRAPPER;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.INT;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.INT_WRAPPER;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.LONG;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.LONG_WRAPPER;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.SHORT;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.SHORT_WRAPPER;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.toBoxedIfNecessary;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.toUnboxedIfNecessary;
import static io.micronaut.inject.processing.JavaModelUtils.getTypeReference;
import static org.objectweb.asm.Opcodes.D2F;
import static org.objectweb.asm.Opcodes.D2I;
import static org.objectweb.asm.Opcodes.D2L;
import static org.objectweb.asm.Opcodes.F2D;
import static org.objectweb.asm.Opcodes.F2I;
import static org.objectweb.asm.Opcodes.F2L;
import static org.objectweb.asm.Opcodes.I2D;
import static org.objectweb.asm.Opcodes.I2F;
import static org.objectweb.asm.Opcodes.I2L;
import static org.objectweb.asm.Opcodes.L2D;
import static org.objectweb.asm.Opcodes.L2F;
import static org.objectweb.asm.Opcodes.L2I;

/**
 * Utility methods for used when compiling evaluated expressions.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class EvaluatedExpressionCompilationUtils {

    /**
     * Checks whether the argument class element is assignable to the parameter
     * class element. This method also accepts primitive and wrapper elements and
     * determines whether argument can be assigned to parameter after boxing or unboxing.
     * In case when parameter or argument is an array, array dimensions are also checked.
     *
     * @param parameter checked parameter
     * @param argument checked argument
     * @return whether argument is assignable to parameter
     */
    public static boolean isAssignable(@NonNull ClassElement parameter,
                                       @NonNull ClassElement argument) {
        if (!argument.isAssignable(parameter)) {
            Type parameterType = getTypeReference(parameter);
            Type argumentType = getTypeReference(argument);

            return toUnboxedIfNecessary(parameterType).equals(toUnboxedIfNecessary(argumentType))
                || toBoxedIfNecessary(parameterType).equals(toBoxedIfNecessary(argumentType));
        }

        if (parameter.getArrayDimensions() > 0 || argument.getArrayDimensions() > 0) {
            return parameter.getArrayDimensions() == argument.getArrayDimensions();
        }

        return true;
    }

    /**
     * Provides {@link ClassElement} for passed type or throws exception
     * if class element can not be provided.
     *
     * @param type Type element for which {@link ClassElement} needs to be obtained.
     * This type can also represent a primitive type. In this case it will be
     * boxed
     * @param visitorContext visitor context
     * @return resolved class element
     * @throws ExpressionCompilationException if class element can not be obtained
     */
    @NonNull
    public static ClassElement getRequiredClassElement(Type type,
                                                       VisitorContext visitorContext) {
        boolean isArrayType = type.getDescriptor().startsWith("[");
        if (isArrayType) {
            Type elementType = type.getElementType();
            ClassElement classElement = toPrimitiveElement(elementType).orElse(null);
            if (classElement == null) {
                classElement = getClassElementForName(visitorContext, elementType.getClassName());
            }

            for (int i = 0; i < type.getDimensions(); i++) {
                classElement = classElement.toArray();
            }

            return classElement;
        }


        String className = toBoxedIfNecessary(type).getClassName();
        return getClassElementForName(visitorContext, className);
    }

    private static ClassElement getClassElementForName(VisitorContext visitorContext, String className) {
        return visitorContext.getClassElement(className)
            .orElseThrow(() -> new ExpressionCompilationException("Can not resolve type information for [" + className + "]"));
    }

    /**
     * Pushed unboxing instruction if passed type is a primitive wrapper.
     *
     * @param type type to unbox
     * @param mv method visitor
     */
    public static void pushUnboxPrimitiveIfNecessary(@NonNull Type type,
                                                     @NonNull GeneratorAdapter mv) {
        if (type.equals(BOOLEAN_WRAPPER)) {
            mv.invokeVirtual(BOOLEAN_WRAPPER, new Method("booleanValue", "()Z"));
        } else if (type.equals(INT_WRAPPER)) {
            mv.invokeVirtual(INT_WRAPPER, new Method("intValue", "()I"));
        } else if (type.equals(DOUBLE_WRAPPER)) {
            mv.invokeVirtual(DOUBLE_WRAPPER, new Method("doubleValue", "()D"));
        } else if (type.equals(LONG_WRAPPER)) {
            mv.invokeVirtual(LONG_WRAPPER, new Method("longValue", "()J"));
        } else if (type.equals(FLOAT_WRAPPER)) {
            mv.invokeVirtual(FLOAT_WRAPPER, new Method("floatValue", "()F"));
        } else if (type.equals(SHORT_WRAPPER)) {
            mv.invokeVirtual(SHORT_WRAPPER, new Method("shortValue", "()S"));
        } else if (type.equals(CHAR_WRAPPER)) {
            mv.invokeVirtual(CHAR_WRAPPER, new Method("charValue", "()C"));
        } else if (type.equals(BYTE_WRAPPER)) {
            mv.invokeVirtual(BYTE_WRAPPER, new Method("byteValue", "()B"));
        }
    }

    /**
     * Pushed primitive boxing instruction if passed type is a wrapper.
     *
     * @param type type to box
     * @param mv method visitor
     */
    public static void pushBoxPrimitiveIfNecessary(@NonNull Type type,
                                                   @NonNull GeneratorAdapter mv) {
        if (type.equals(BOOLEAN)) {
            mv.invokeStatic(BOOLEAN_WRAPPER, new Method("valueOf", BOOLEAN_WRAPPER, new Type[] {BOOLEAN}));
        } else if (type.equals(INT)) {
            mv.invokeStatic(INT_WRAPPER, new Method("valueOf", INT_WRAPPER, new Type[] {INT}));
        } else if (type.equals(DOUBLE)) {
            mv.invokeStatic(DOUBLE_WRAPPER, new Method("valueOf", DOUBLE_WRAPPER, new Type[] {DOUBLE}));
        } else if (type.equals(LONG)) {
            mv.invokeStatic(LONG_WRAPPER, new Method("valueOf", LONG_WRAPPER, new Type[] {LONG}));
        } else if (type.equals(FLOAT)) {
            mv.invokeStatic(FLOAT_WRAPPER, new Method("valueOf", FLOAT_WRAPPER, new Type[] {FLOAT}));
        } else if (type.equals(SHORT)) {
            mv.invokeStatic(SHORT_WRAPPER, new Method("valueOf", SHORT_WRAPPER, new Type[] {SHORT}));
        } else if (type.equals(CHAR)) {
            mv.invokeStatic(CHAR_WRAPPER, new Method("valueOf", CHAR_WRAPPER, new Type[] {CHAR}));
        } else if (type.equals(BYTE)) {
            mv.invokeStatic(BYTE_WRAPPER, new Method("valueOf", BYTE_WRAPPER, new Type[] {BYTE}));
        }
    }

    /**
     * @param type type to be converted to {@link PrimitiveElement}
     * @return optional corresponding primitive element
     */
    public static Optional<PrimitiveElement> toPrimitiveElement(Type type) {
        try {
            return Optional.of(PrimitiveElement.valueOf(type.getClassName()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /**
     * This method checks whether passed primitive type needs to be explicitly cast
     * to target type. If it is, respective cast instruction is pushed.
     *
     * @param type type to cast
     * @param targetType target type to which the cast is required
     * @param mv method visitor
     */
    public static void pushPrimitiveCastIfNecessary(@NonNull Type type,
                                                    @NonNull Type targetType,
                                                    @NonNull GeneratorAdapter mv) {
        String typeDescriptor = type.getDescriptor();
        String targetDescriptor = targetType.getDescriptor();

        switch (targetDescriptor) {
            case "J" -> {
                switch (typeDescriptor) {
                    case "I" -> mv.visitInsn(I2L);
                    case "D" -> mv.visitInsn(D2L);
                    case "F" -> mv.visitInsn(F2L);
                    default -> {
                    }
                }
            }
            case "I" -> {
                switch (typeDescriptor) {
                    case "J" -> mv.visitInsn(L2I);
                    case "D" -> mv.visitInsn(D2I);
                    case "F" -> mv.visitInsn(F2I);
                    default -> {
                    }
                }
            }
            case "D" -> {
                switch (typeDescriptor) {
                    case "J" -> mv.visitInsn(L2D);
                    case "I" -> mv.visitInsn(I2D);
                    case "F" -> mv.visitInsn(F2D);
                    default -> {
                    }
                }
            }
            case "F" -> {
                switch (typeDescriptor) {
                    case "J" -> mv.visitInsn(L2F);
                    case "I" -> mv.visitInsn(I2F);
                    case "D" -> mv.visitInsn(D2F);
                    default -> {
                    }
                }
            }
            default -> {
            }
        }
    }
}
