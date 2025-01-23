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
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.TypeDef;

import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.toBoxedIfNecessary;
import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.toUnboxedIfNecessary;

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
            TypeDef parameterType = TypeDef.erasure(parameter);
            TypeDef argumentType = TypeDef.erasure(argument);

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
     *             This type can also represent a primitive type. In this case it will be
     *             boxed
     * @param visitorContext visitor context
     * @return resolved class element
     * @throws ExpressionCompilationException if class element can not be obtained
     */
    @NonNull
    public static ClassElement getRequiredClassElement(TypeDef type,
                                                       VisitorContext visitorContext) {

        if (type instanceof TypeDef.Array array) {
            TypeDef elementType = array.componentType();
            ClassElement classElement = getRequiredClassElement(elementType, visitorContext);

            for (int i = 0; i < array.dimensions(); i++) {
                classElement = classElement.toArray();
            }

            return classElement;
        }
        if (type instanceof TypeDef.Primitive primitive) {
            return PrimitiveElement.valueOf(primitive.name());
        }
        if (type instanceof ClassTypeDef classType) {
            return getClassElementForName(visitorContext, classType.getName());
        }
        throw new IllegalStateException("Unknown type " + type);
    }

    private static ClassElement getClassElementForName(VisitorContext visitorContext, String className) {
        return visitorContext.getClassElement(className)
                   .orElseThrow(() -> new ExpressionCompilationException(
                       "Can not resolve type information for [" + className + "]"));
    }

}
