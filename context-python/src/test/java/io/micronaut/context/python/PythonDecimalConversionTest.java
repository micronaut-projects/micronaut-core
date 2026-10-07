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
package io.micronaut.context.python;

import java.math.BigDecimal;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonDecimalConversionTest {

    private static final List<String> VALUES = List.of("14.50", "-123.4500", "0.000", "1E+30", "12345678901234567890.12345678901234567890");

    @Test
    void pythonDecimalConvertsToBigDecimalWithoutLosingPrecisionOrScale() {
        try (Context context = Context.newBuilder("python")
            .allowAllAccess(true)
            .allowHostAccess(new GraalPyHostAccessFactory().hostAccess(List.of()))
            .build()) {
            for (String text : VALUES) {
                Value decimal = context.eval("python", "__import__('decimal').Decimal").execute(text);
                BigDecimal expected = new BigDecimal(text);

                assertEquals(expected, PythonConversion.convertValue(decimal, BigDecimal.class));
                assertEquals(expected, decimal.as(BigDecimal.class));
                assertEquals(expected, decimal.as(Object.class));
            }
        }
    }

    @Test
    void bigDecimalBecomesANativePythonDecimalWithoutLosingPrecisionOrScale() {
        try (Context context = Context.newBuilder("python").allowAllAccess(true).build()) {
            for (String text : VALUES) {
                BigDecimal expected = new BigDecimal(text);
                Value decimal = context.asValue(PythonCoercion.coerceToContext(expected, context));

                assertTrue(context.eval("python", "lambda value: isinstance(value, __import__('decimal').Decimal)")
                    .execute(decimal).asBoolean());
                assertEquals(text, context.eval("python", "str").execute(decimal).asString());
                assertEquals(-expected.scale(), decimal.invokeMember("as_tuple").getMember("exponent").asInt());
            }
        }
    }

    @Test
    void nonFiniteDecimalsAreRejectedExplicitly() {
        try (Context context = Context.newBuilder("python")
            .allowAllAccess(true)
            .allowHostAccess(new GraalPyHostAccessFactory().hostAccess(List.of()))
            .build()) {
            for (String text : List.of("NaN", "sNaN", "Infinity", "-Infinity")) {
                Value decimal = context.eval("python", "__import__('decimal').Decimal").execute(text);
                RuntimeException error = assertThrows(RuntimeException.class,
                    () -> PythonConversion.convertValue(decimal, BigDecimal.class));
                assertTrue(error.getMessage().contains("Non-finite decimal.Decimal"));
                assertThrows(RuntimeException.class, () -> decimal.as(Object.class));
            }
        }
    }

    @Test
    void applicationClassNamedDecimalIsNotConvertedAsTheStandardLibraryType() {
        try (Context context = Context.newBuilder("python")
            .allowAllAccess(true)
            .allowHostAccess(new GraalPyHostAccessFactory().hostAccess(List.of()))
            .build()) {
            Value custom = context.eval("python", """
                class Decimal:
                    def __str__(self):
                        return "14.50"

                    def is_finite(self):
                        return True

                Decimal()
                """);
            assertThrows(ClassCastException.class, () -> custom.as(BigDecimal.class));
            assertFalse(custom.as(Object.class) instanceof BigDecimal);
        }
    }
}
