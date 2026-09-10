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
package io.micronaut.python.processing;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.writer.ParameterDefaultValueProvider;
import io.micronaut.python.processing.element.PythonParameterElement;
import io.micronaut.python.processing.model.ArgumentDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Supplies the declared default value of a Python parameter, where that default is an immutable
 * literal the generated code can reproduce.
 *
 * <p>A Python default is evaluated by the interpreter, so in general it is applied by the stub
 * constructor rather than by the caller. A literal is the exception: {@code host: str = "localhost"}
 * carries its value into {@code ArgumentDef#defaultValue()} at compile time, so it can be passed
 * explicitly and is indistinguishable from letting Python apply it.</p>
 *
 * <p>Anything else — a mutable literal, a {@code default_factory}, a call, a name — yields
 * {@link Optional#empty()}, leaving those parameters to the stub's own defaulting. In particular a
 * fresh mutable default must not be materialised here, as Python evaluates a mutable default once
 * at definition time and shares it between calls.</p>
 *
 * @author graemerocher
 * @since 5.2.0
 */
@Experimental
public final class PythonParameterDefaultValueProvider implements ParameterDefaultValueProvider {

    @Override
    public boolean supports(ParameterElement parameter) {
        return parameter instanceof PythonParameterElement;
    }

    @Override
    public Optional<ExpressionDef> defaultValueExpression(ParameterElement parameter, @Nullable ExpressionDef target) {
        if (!(parameter.getNativeType() instanceof ArgumentDef argumentDef) || !argumentDef.hasDefaultValue()) {
            return Optional.empty();
        }
        return Optional.ofNullable(literal(parameter.getType(), argumentDef.defaultValue()));
    }

    /**
     * Reproduces a Python literal default as a constant of the parameter's own type.
     *
     * @param type  The parameter type
     * @param value The default value, already converted to a Java value
     * @return The constant, or {@code null} if it cannot be reproduced faithfully
     */
    private static @Nullable ExpressionDef literal(ClassElement type, @Nullable Object value) {
        String typeName = type.getName();
        if (value == null) {
            // An explicit `= None`, which a primitive parameter cannot represent
            return type.isPrimitive() ? null : ExpressionDef.nullValue();
        }
        if (value instanceof Number number) {
            return switch (typeName) {
                case "int", "java.lang.Integer" -> ExpressionDef.constant(number.intValue());
                case "long", "java.lang.Long" -> ExpressionDef.constant(number.longValue());
                case "short", "java.lang.Short" -> ExpressionDef.constant(number.shortValue());
                case "byte", "java.lang.Byte" -> ExpressionDef.constant(number.byteValue());
                case "double", "java.lang.Double" -> ExpressionDef.constant(number.doubleValue());
                case "float", "java.lang.Float" -> ExpressionDef.constant(number.floatValue());
                default -> null;
            };
        }
        if (value instanceof Boolean booleanValue && ("boolean".equals(typeName) || "java.lang.Boolean".equals(typeName))) {
            return ExpressionDef.constant(booleanValue);
        }
        if (value instanceof String stringValue && "java.lang.String".equals(typeName)) {
            return ExpressionDef.constant(stringValue);
        }
        return null;
    }
}
